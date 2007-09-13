/*  jagger - Demon Mode Command Line Interface

    Copyright (c) 2007 by 1&1 Internet AG

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as
    published by the Free Software Foundation. A copy of this license is
    included with this software in the file "LICENSE.txt".

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    $Id: CLI.groovy 68 2007-09-04 09:35:41Z jhermann $
*/

package de.web.tools.jagger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.web.tools.jagger.jmx.JmxConfigReader;
import de.web.tools.jagger.jmx.JMXAgentFacade;


class AttributeAccessor {
    def remoteBean
    def property

    public toString() {
        return "$property@${remoteBean.objectName}"
    }    
}


class RemoteBeanAccessor {
    def context
    def remoteBean

    public Object getProperty(final String property) {
        //println "GET $property@${remoteBean.name}"
        new AttributeAccessor(remoteBean: remoteBean, property: property)
    }    
}


class ModelDelegate {
    def context

    private doAggregation(name, accessor, aggregator) {
        def values = context.pollInstances(accessor)

        "${values.inject(0, aggregator)} = $name($values)"
    }

    public Object getProperty(final String property) {
        //println "Accessing bean $property"
        return new RemoteBeanAccessor(context: context, remoteBean: context.model.remoteBeans[property])
    }

    public sum(accessor) {
        doAggregation("sum", accessor) { a, b -> a + b }
    }

    public avg(accessor) {
        // XXX TODO: does not work that way!
        doAggregation("avg", accessor) { a, b -> a + b }
    }

    public min(accessor) {
        doAggregation("min", accessor) { a, b -> [a, b].min() }
    }

    public max(accessor) {
        doAggregation("max", accessor) { a, b -> [a, b].max() }
    }
}


class ExecutionContext {
    private agentCache = [:]
    private beanCache = [:]

    Boolean tracing = true
    
    def model

    public trace(messageGenerator) {
        if (tracing) {
            println "TRACE: ${messageGenerator()}"
        }
    }


    public getAgent(instance) {
        if (!agentCache.containsKey(instance.url)) {
            trace{"Connecting to ${instance.toString()}..."}
            def agent = new JMXAgentFacade(url: instance.url, username: instance.username, password: instance.password)
            agent.openConnection()
            agentCache[instance.url] = agent
        }

        return agentCache[instance.url]
    }


    public pollInstances(accessor) {
        model.rootCluster.instances.inject([]) { result, instance ->
            def agent = getAgent(instance)
            if (!beanCache.containsKey(agent.url)) {
                beanCache[agent.url] = [:]
            }
            
            def cachedBeans = beanCache[agent.url]
            if (!cachedBeans.containsKey(accessor.remoteBean.name)) {
                cachedBeans[accessor.remoteBean.name] = accessor.remoteBean.lookupBeans(agent)
                def names = cachedBeans[accessor.remoteBean.name].collect { it.name().canonicalName }
                trace {
                    "Lookup for '$accessor.remoteBean.name' returned ${names.join(', ')}"
                }
            }

            cachedBeans[accessor.remoteBean.name].each {
                def val = it.getProperty(accessor.property)
                //trace { "${instance.url}:${accessor.toString()} = $val" }
                result << val
            }

            result
        }
    }
}


class Executor {
    def model

    public run() {
        def context = new ExecutionContext(model: model)
        model.targetBeans.each { beanName, bean ->
            bean.attributes.each { attributeName, attribute ->
                attribute.expression.delegate = new ModelDelegate(context: context)
                //attribute.expression.resolveStrategy = Closure.DELEGATE_ONLY
                println "${attribute.name} = ${attribute.expression.call()}"
            }
        }
    }
}



/**
 *  Command line interface to the JMX demon.
 */
class Demon extends CLISupport {
    private static Log log = LogFactory.getLog(Demon.class)


    /**
     *  Demon instances are only created by main().
     */
    private Demon() {}


    /**
     *  Add jagger's options to CLI builder.
     *
     *  @param cli CLI builder instance
     */
    protected void addOptions(cli) {
        //cli.u(longOpt: 'username', args: 1, argName: 'USER',    'JMX username.')
        //cli.w(longOpt: 'password', args: 1, argName: 'PWD',     'JMX password.')
    }    


    /**
     *  Start everything up, coordinate the running threads and
     *  finally try to shut down cleanly.
     *
     *  @param cli CLI builder instance
     *  @param options parsed options
     *  @return exit code
     */
    protected mainloop(cli, options) {
        // log proper startup
        log.info("Jagger demon startup initiated by ${System.getProperty('user.name')}")

        def args = options.arguments()
        def configFilename = 'tests_src/conf/test.jagger'
        if (args) {
            configFilename = args[0]
        }

        def cr = new JmxConfigReader()
        def model
        try {
            model = cr.loadModel(configFilename)
        } catch (ScriptException ex) {
            println "FATAL: ${ex.message}"
            return 1
        }
        println '~'*78
        println model.toString()
        println '~'*78
        model.rootCluster.instances.each { println it.toString() }

        new Executor(model: model).run()

        // log proper shutdown
        log.info("Jagger demon shutdown initiated by ${System.getProperty('user.name')}")

        return 0
    }


    /**
     *  The jagger Demon main.
     *
     *  @param args command line argument array
     *  @return exit code
     */
    public static main(args) {
        // delegate to instance of this class
        return new Demon().process(args)
    }
}

