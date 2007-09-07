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

import javax.management.ObjectName;
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
 *
 *  This is modelled after the composite pattern, a cluster can contain either
 *  other clusters or JMXInstances.
 */
class JmxCluster {
    private final INDENT = '\n    '

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
        // append all children's representation, indenting them by one level
        def members = children
            .inject([]) { r, c -> r + c.toString().tokenize('\n') }
            .join(INDENT)

        return "Cluster '$name'$INDENT$members"
    }
}


/**
 *  Base class for named MBeans.
 */
class JmxMBean {
    // name
    String name

    // name
    ObjectName objectName

    // the model we're part of
    def model


    protected JmxMBean(model, name, objectName) {
        if (model.mbeans.containsKey(name)) {
            throw new IllegalArgumentException("MBean with name '$name' already defined!")
        }

        this.model = model
        this.name = name
        this.objectName = objectName

        model.mbeans[name] = this
    }

    def toString() {
        "MBean $name = '$objectName'"
    }

    static JmxMBean create(model, name, objectName) {
        def jmxObjectName
        try {
            jmxObjectName = objectName as ObjectName
        } catch (GroovyCastException) {
            jmxObjectName = new ObjectName(objectName)
        }

        if (jmxObjectName.isPropertyPattern()) {
            return new JmxMBeanGroup(model, name, jmxObjectName)
        } else {
            return new JmxSimpleMBean(model, name, jmxObjectName)
        }
    }
}


/**
 *  Simple (scalar) MBean.
 */
class JmxSimpleMBean extends JmxMBean {
    protected JmxSimpleMBean(model, name, objectName) {
        super(model, name, objectName)
    }
}


/**
 *  A group of releated MBeans.
 */
class JmxMBeanGroup extends JmxMBean {
    // the primary keys identifying each group bean
    def keys = []


    protected JmxMBeanGroup(model, name, objectName) {
        super(model, name, objectName)

        // this might seem overly complex, but real-life experience shows
        // that queries for 'key=*' don't work, just ones with a trailing ',*'
        def literals = []        
        objectName.keyPropertyList.each { key, val ->
            if (val == '*') {
                keys << key
            } else {
                literals << "${ObjectName.quote(key)}=${ObjectName.quote(val)}"
            }
        }
        this.objectName = new ObjectName("${ObjectName.quote(objectName.domain)}:${literals.join(',')},*")
    }

    def toString() {
        "${super.toString()} keys=${keys}"
    }
}


/**
 *  Complete JMX data model.
 */
class JmxModel {
    // map of clusters in this model
    final clusters = [:]

    // map of mbeans in this model
    final mbeans = [:]

    // root cluster
    final rootCluster = new JmxCluster(this, null, '')

    // definition of aggregation beans
    def beans

    def toString() {
        def result = [rootCluster.toString()]

        mbeans.each { key, bean ->
            result << bean.toString()
        }

        result << beans.inspect()

        return result.join('\n')
    }
}


/**
 *  DSL implementation.
 */
class JmxConfigReader {
    //private static log = LogFactory.getLog(JmxConfigReader.class)
    private static log = new Expando(debug: System.out.&println, info: System.out.&println)

    // known verbs of the DSL
    private final DSL_VERBS = [
        'include',
        'cluster', 'host',
        'mbean',
    ]

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
     *  Initializes reader state before parsing a new config file.
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
        DSL_VERBS.each {
            binding."$it" = this.&"do${it[0].toUpperCase()}${it[1..-1]}"
        }
    }

    /**
     *  Saves parts of the reader state before entering a closure, so it can
     *  be restored later.
     *
     *  @return State object.
     */
    private getState() {
        // save certain keys of the binding in a hashmap
        ['defaultPort'].inject([:]) { state, key ->
            state[key] = binding."$key"; state
        }
    }


    /**
     *  Restores saved reader state.
     *
     *  @param state State object as returned by getState().
     */
    private setState(state) {
        state.each { k, v ->
            binding."$k" = v
        }
        return this
    }


    /**
     *  Implements the "cluster" verb.
     *
     *  @param name Name of the cluster.
     *  @param clusterDef Definition of the cluster content.
     */
    private void doCluster(String name, Closure clusterDef) {
        // create new cluster and push it on the stack
        clusterStack.add(new JmxCluster(binding.model, clusterStack[-1], name))

        // execute cluster definition, with local scoping as defined by "state"
        def savedState = state
        try {
            clusterDef()
        } finally {
            state = savedState
        }

        // do not allow empty clusters
        if (!(clusterStack.pop().children)) {
            throw new IllegalArgumentException("The member list for cluster '$name' is empty!")
        }
    }
    private void doCluster(String name) {
        // prevent cluster verbs without a closure following them
        throw new IllegalArgumentException("You didn't define any members for cluster '$name'!")
    }

    /**
     *  Implements the "host" verb with a list of names.
     *
     *  @param names List of hostnames, using the default port.
     */
    private void doHost(String[] names) {
        assert binding.defaultPort != null, "No default port defined"
        names.each {
            new JmxInstance(clusterStack[-1], it, binding.defaultPort)
        }
    }
    /**
     *  Implements the "host" verb with a list of "host:port" arguments.
     *
     *  @param params Named parameters, interpreted as a "host:port" list.
     */
    private void doHost(Map params) {
        params.each { hostname, port ->
            new JmxInstance(clusterStack[-1], hostname, port)
        }
    }
    /**
     *  Implements the "host" verb with a template and numbering range,
     *  using the default port.
     *
     *  @param templ Naming template, e.g. 'myhost%02d'.
     *  @param range Numbers to use.
     */
    private void doHost(String templ, IntRange range) {
        assert binding.defaultPort != null, "No default port defined"
        range.each {
            new JmxInstance(clusterStack[-1], sprintf(templ, it), binding.defaultPort)
        }
    }

    /**
     *  Implements the "mbean" verb.
     *
     *  @param params Named parameters, interpreted as a "name:objectName" list.
     */
    private void doMbean(Map params) {
        params.each { name, objectName ->
            JmxMBean.create(model, name, objectName)
        }
    }

    /**
     *  Implements the "include" verb.
     *
     *  This tries to resolve the given path in the following order: <ol>
     *    <li>as given, absolute or relative to the cwd</li>
     *    <li>relative to the including script</li>
     *    <li>as a classpath resource</li>
     *  </ol>
     *
     *  @param scriptPath Path to the script to include.
     */
    private void doInclude(scriptPath) {
        log.debug("Including $scriptPath...")

        def resolved = scriptPath as File
        if (!resolved.isAbsolute()) {
            if (!resolved.exists()) {
                // try relative to including script
                resolved = new File((loadedPaths[-1] as File).parent, scriptPath)
            }
            if (!resolved.exists()) {
                // try to get a classpath resource
                def resource = getClass().classLoader.getResource(scriptPath)
                if (resource) {
                    assert resource.protocol == 'file', "Included resource '$resource' not a file"
                    resolved = resource.path as File
                }
            }
        }

        // unable to resolve the given name?
        if (!resolved.exists()) {
            throw new FileNotFoundException("Can't find '${scriptPath}' for inclusion!")
        }

        // execute the script by a recursive call
        execScript(resolved)
    }
    private void doInclude() {
        // catch common error with a nice message
        throw new IllegalArgumentException('You must define an include filename!')
    }

    /**
     *  Loads and executes a script or include.
     *
     *  @param script String or File object with the script's filename.
     */
    private void execScript(script) {
        script = script as File
        log.debug("Loading $script...")
        def scriptText = script.text
        def className = script.absolutePath.replaceAll('[^a-zA-Z0-9]', '_')

        // prevent recursive includes
        if (loadedPaths.contains(script.absolutePath)) {
            throw new StackOverflowError("Recursive inclusion of '${script.name}' from '${(loadedPaths[-1] as File).name}'")
        }

        // closure that tries to drop excessive information from exceptions thrown
        // while executing the script 
        def handleException = { ex, msg ->
            throw ex
            def trace = ex.stackTrace
                .findAll { it.fileName == className }
                .collect { "${script.name}, line ${it.lineNumber}:" }
            if (trace) {
                if (msg == null) msg = "${ex.getClass().name}: ${ex.message.replace(className, script.name)}"
                throw new ScriptException("${trace[0]} $msg", ex)
            } else {
                throw ex
            }
        }

        // execute script or include
        loadedPaths.add(script.absolutePath)
        try {
            groovyShell.evaluate(scriptText, className)
        } catch (MissingMethodException ex) {
            if (ex.type.name == className) {
                def msg = null
                if (!DSL_VERBS.contains(ex.method)) {
                    msg = "Unknown configuration directive '${ex.method}' used!"
                }
                handleException(ex, msg)
            } else if (ex.type.name == this.getClass().name) {
                if (ex.method.startsWith('do')) {
                    def verb = ex.method[2..-1].toLowerCase()
                    handleException(ex, "Bad parameters for configuration directive '$verb': ${ex.message.replace(className, script.name)}")
                } else {
                    // internal (programming) error
                    throw ex
                }
            } else {
                // internal (programming) error
                throw ex
            }
        } catch (AssertionError ex) {
            handleException(ex, null)
        } catch (Exception ex) {
            handleException(ex, null)
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
        model.beans = binding.beans
        return model
    }

}

// def cr = new JmxConfigReader(); def model = cr.loadModel('tests_src/conf/test.jagger'); println '~'*78; println model.toString()

