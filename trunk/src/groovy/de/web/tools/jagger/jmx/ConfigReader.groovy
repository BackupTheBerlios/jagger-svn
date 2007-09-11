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

import de.web.tools.jagger.jmx.model.*;


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
        'remoteBean',
    ]

    // binding names that should be local to a scope
    private final LOCAL_STATE = [
        'defaultPort', 'username', 'password',
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
        binding.username = null
        binding.password = null

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
        LOCAL_STATE.inject([:]) { state, key ->
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
            copyCredentials(new JmxInstance(clusterStack[-1], it, binding.defaultPort))
        }
    }
    /**
     *  Implements the "host" verb with a list of "host:port" arguments.
     *
     *  @param params Named parameters, interpreted as a "host:port" list.
     */
    private void doHost(Map params) {
        params.each { hostname, port ->
            copyCredentials(new JmxInstance(clusterStack[-1], hostname, port))
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
            copyCredentials(new JmxInstance(clusterStack[-1], sprintf(templ, it), binding.defaultPort))
        }
    }
    private void copyCredentials(instance) {
        instance.username = binding.username
        instance.password = binding.password
    }

    /**
     *  Implements the "remoteBean" verb.
     *
     *  @param params Named parameters, interpreted as a "name:objectName" list.
     */
    private void doRemoteBean(Map params) {
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
        model.targetBeans = binding.targetBeans
        return model
    }

}

// def cr = new JmxConfigReader(); def model = cr.loadModel('tests_src/conf/test.jagger'); println '~'*78; println model.toString()

