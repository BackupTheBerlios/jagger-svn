/*  jagger - reader for the JMX model configuration DSL

    Copyright (c) 2007 by 1&1 Internet AG

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as
    published by the Free Software Foundation. A copy of this license is
    included with this software in the file "LICENSE.txt".

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    $Id$
*/

package de.web.tools.jagger.jmx;

import org.apache.commons.logging.LogFactory;


/**
 *  A single JVM on a host.
 */
class JmxInstance {
    // agent URL
    String url

    // the cluster we belong to
    def cluster


    public JmxInstance (cluster, hostname, port) {
        assert (1..65535).contains(port as Integer), "Illegal port number '${port}'" 

        this.cluster = cluster
        this.url = "${hostname}:${port}"

        cluster.children << this
    }

    def toString() {
        "Instance '$url'"
    }
}


/**
 *  A single cluster.
 */
class JmxCluster {
    // the model we're part of
    def model

    // our parent cluster (or null for root)
    def parent

    // name of this cluster
    String name

    // what's in the cluster (instances and sub-clusters)
    def children = []

    public JmxCluster(model, parent, name) {
        if (model.clusters.containsKey(name)) {
            throw new IllegalArgumentException("Cluster with name '$name' already defined!")
        }

        this.model = model
        this.parent = parent
        this.name = name

        model.clusters[name] = this
        if (parent) parent.children << this
    }

    def toString() {
        def members = children
            .inject([]) { r, c -> r + c.toString().tokenize('\n') }
            .join('\n    ')
        return "Cluster '$name'\n    $members"
    }
}


/**
 *  Complete JMX data model.
 */
class JmxModel {
    // map of clusters in this model
    final clusters = [:]

    // root cluster
    final rootCluster = new JmxCluster(this, null, '')

    def toString() {
        rootCluster.toString()
    }
}


/**
 *  DSL implementation.
 */
class JmxConfigReader {
    //private static log = LogFactory.getLog(JmxConfigReader.class)
    private static log = new Expando(debug: System.out.&println, info: System.out.&println)

    // the binding for the configuration file's outer scope
    private binding

    // GroovyShell to read the configuration script
    private groovyShell

    // list of loaded paths to prevent recursive includes
    private loadedPaths

    // list of current cluster nesting
    private clusterStack

    // model (while loading)
    private model


    /**
     *  XXX TODO!
     */
    private void init() {
        binding = new Binding()
        groovyShell = new GroovyShell(binding)
        loadedPaths = []
        model = new JmxModel()
        clusterStack = [model.rootCluster]

        // bind objects
        binding.model = model
        binding.defaultPort = null

        // bind methods (include => doInclude, etc.)
        ['cluster', 'host', 'include'].each {
            binding."$it" = this.&"do${it[0].toUpperCase()}${it[1..-1]}"
        }
    }

    /**
     *  XXX TODO!
     *
     *  @param 
     */
    private getState() {
        ['defaultPort'].inject([:]) { state, key ->
            state[key] = binding."$key"; state
        }
    }


    /**
     *  XXX TODO!
     *
     *  @param 
     */
    private setState(Map state) {
        state.each { k, v ->
            binding."$k" = v
        }
        return this
    }


    /**
     *  XXX TODO!
     *
     *  @param 
     */
    private void doCluster(String name, Closure clusterDef) {
        clusterStack.add(new JmxCluster(binding.model, clusterStack[-1], name))

        def savedState = state
        try {
            clusterDef()
        } finally {
            state = savedState
        }

        if (!(clusterStack.pop().children)) {
            throw new IllegalArgumentException("The member list for cluster '$name' is empty!")
        }
    }
    private void doCluster(String name) {
        throw new IllegalArgumentException("You didn't define any members for cluster '$name'!")
    }

    /**
     *  XXX TODO!
     *
     *  @param 
     */
    private void doHost(String[] names) {
        assert binding.defaultPort != null, "No default port defined"
        names.each {
            new JmxInstance(clusterStack[-1], it, binding.defaultPort)
        }
    }
    private void doHost(Map params) {
        params.each { hostname, port ->
            new JmxInstance(clusterStack[-1], hostname, port)
        }
    }
    private void doHost(String templ, IntRange range) {
        assert binding.defaultPort != null, "No default port defined"
        range.each {
            new JmxInstance(clusterStack[-1], sprintf(templ, it), binding.defaultPort)
        }
    }

    /**
     *  Implementation of the "include" directive.
     *
     *  This tries to resolve the given path in the following order: <ol>
     *    <li>as given, absolute or relative to the cwd</li>
     *    <li>relative to the including script</li>
     *    <li>as a classpath resource</li>
     *  </ol>
     *
     *  @param Path to the script to include.
     */
    private void doInclude(scriptPath) {
        log.debug("Including $scriptPath...")

        def resolved = scriptPath as File
        if (!resolved.isAbsolute()) {
            if (!resolved.exists()) resolved = new File((loadedPaths[-1] as File).parent, scriptPath)
            if (!resolved.exists()) {
                def resource = getClass().classLoader.getResource(scriptPath)
                if (resource) {
                    assert resource.protocol == 'file', "Included resource '$resource' not a file"
                    resolved = resource.path as File
                }
            }
        }

        if (!resolved.exists()) {
            throw new FileNotFoundException("Can't find '${scriptPath}' for inclusion!")
        }

        execScript(resolved)
    }
    private void doInclude() {
        throw new IllegalArgumentException('You must define an include filename!')
    }

    /**
     *  Load and execute a script or include.
     *
     *  @param script String or File object with the script's filename.
     */
    private void execScript(script) {
        script = script as File
        log.debug("Loading $script...")
        def scriptText = script.text
        def className = script.absolutePath.replaceAll('[^a-zA-Z0-9]', '_')

        if (loadedPaths.contains(script.absolutePath)) {
            throw new StackOverflowError("Recursive inclusion of '${script.name}' from '${(loadedPaths[-1] as File).name}'")
        }

        def handleException = { ex ->
            def trace = ex.stackTrace
                .findAll { it.fileName == className }
                .collect { "${script.name}, line ${it.lineNumber}:" }
            if (trace) {
                throw new ScriptException(trace[0] +
                " ${ex.getClass().name}: ${ex.message.replace(className, script.name)}", ex)
            } else {
                throw ex
            }
        }

        loadedPaths.add(script.absolutePath)
        try {
            groovyShell.evaluate(scriptText, className)
        } catch (AssertionError ex) {
            handleException(ex)
        } catch (Exception ex) {
            handleException(ex)
        } finally {
            loadedPaths.pop()
        }
    }

    /**
     *  Load a JMX model from a DSL config script.
     *
     *  @param script String or File object with the script's filename.
     *  @return The loaded model.
     */
    public JmxModel loadModel(script) {
        init()
        execScript(script)
        return model
    }

}

def cr = new JmxConfigReader(); def model = cr.loadModel('tests_src/conf/test.jagger'); println model.dump(); println model.toString()

