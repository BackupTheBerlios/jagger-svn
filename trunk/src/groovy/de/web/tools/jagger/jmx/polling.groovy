/*  jagger - JMX remote bean polling

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

package de.web.tools.jagger.jmx.polling;

import javax.management.ObjectName;

import de.web.tools.jagger.jmx.JMXAgentFacade;
import de.web.tools.jagger.jmx.execution.ModelEvaluationCategory;


/**
 *  Decorator for remote bean alias evaluation.
 */
class RemoteBeanAliasEvaluator extends groovy.util.Proxy {
    // alias definitions
    def aliases


    /**
     *  Evaluates an alias definition or gets a property of a remote bean.
     *  Aliases take precedence, i.e. you can hide properties behind an alias
     *  to e.g. scale them.
     *
     *  @param name Name of bean property or alias.
     *  @return Property value.
     */
    public getProperty(String name) {
        def result
        //println "!!! $name"
        if (getAliases().containsKey(name)) {
            //println "!!! Evaluating $name"
            synchronized (getAliases()[name]) {
                // context for the alias expression is the bean containing it
                getAliases()[name].delegate = getAdaptee()
                use(ModelEvaluationCategory) {
                    result = getAliases()[name].call(getAdaptee())
                }
            }
        } else {
            result = getAdaptee().getProperty(name)
        }
        return result
    }
}


/**
 *  Base class for named beans on remote JVMs.
 */
class BeanPoller {
    // JMX query if this is a bean group
    private ObjectName groupQuery = null

    // the primary keys identifying each group bean
    private filters = [:]

    // remote bean definition in the model
    def remoteBean


    /**
     *  Creates a new poller for a remote bean or group of such beans, if
     *  the objectname contains patterns.
     *
     *  @param bean The remote bean definition in the model.
     */
    protected BeanPoller(bean) {
        remoteBean = bean

        if (remoteBean.objectName.isPropertyPattern()) {
            // the following might seem overly complex, but real-life experience
            // shows that queries for 'key=*' or 'key=prefix*' don't work, just ones
            // with a trailing ',*'

            // Should work in theory, leads to failed queries in real life
            //def quote = ObjectName.&quote
            def quote = { it }

            // divide keys into literal ones and patterns
            def literals = []
            remoteBean.objectName.keyPropertyList.each { key, val ->
                if (val.endsWith('*')) {
                    filters[key] = (val =='*') ? '' : val[0..-2]
                } else {
                    literals << "${quote(key)}=${quote(val)}"
                }
            }

            // put literal keys into query for remote filtering
            groupQuery = new ObjectName("${quote(remoteBean.objectName.domain)}:${(literals + '*').join(',')}")
        }
    }

    /**
     *  Wraps the given bean into a proxy if necessary.
     *
     *  @param bean The GroovyMBean.
     *  @return Possibly wrapped bean.
     */
    private withAliases(bean) {
        if (remoteBean.aliases) {
            def proxy = new RemoteBeanAliasEvaluator(aliases: remoteBean.aliases)
            bean = proxy.wrap(bean)
        }
        return bean
    }
    
    /**
     *  Checks whether the given objectname passes the filter criteria.
     *
     *  @param objectName JMX objectname to be checked.
     *  @return True if all criteria match.
     */
    private Boolean passesFilter(objectName) {
        // filter is passed if no mismatch is found
        null == filters.find {
            !objectName.getKeyProperty(it.key)?.startsWith(it.value)
        }
    }

    /**
     *  Resolve objectname to list of matching GroovyMBeans in the remote JVM.
     *
     *  @param agent Remote JVM.
     *  @return List containing the remote beans.
     */
    def lookupBeans(agent) {
        if (groupQuery == null) {
            return [withAliases(agent.getBean(remoteBean.objectName))]
        } else {
            def result = []
            agent.queryBeans(groupQuery) { name, bean ->
                //println name
                if (passesFilter(name)) {
                    result << withAliases(bean)
                }
            }
            return result
        }
    }
}


/**
 *  Controls polling of remote mbeans.
 *
 *  Has to be thread-safe.
 */
class RemotePoller {
    // cache for connection facades to remote agents, indexed by URL
    private agentCache = [:]


    /**
     *  Return agent facade for the given model instance.
     *
     *  @param instance Instance object in the model.
     *  @return Agent facade (connection).
     */
    public synchronized getAgent(instance) {
        if (!agentCache.containsKey(instance.url)) {
            //println "Connecting to ${instance.toString()}..."
            def agent = new JMXAgentFacade(url: instance.url, username: instance.username, password: instance.password)
            agent.openConnection()
            agentCache[instance.url] = agent
        }

        return agentCache[instance.url]
    }
    
    /**
     *  Poll all defined instances for the values of the given bean attribute
     *  accessor.
     *
     *  @param instances List of instances to poll.
     *  @param accessor RemoteAttributeAccessor object.
     *  @return List of current values.
     */
    public pollInstances(instances, accessor) {
        def reachable = 0
        def values = instances.inject([]) { result, instance ->
            def agent
            try {
                agent = getAgent(instance)
            // XXX need to better handle failed servers, at least provide
            // a list of those as a target bean attribute
            } catch (java.rmi.ConnectException ex) {
            } catch (IOException ex) {
            }

            if (agent) {
                ++reachable
                accessor.injectValues(result, agent)
            }
            return result
        }
        if (!reachable) {
            throw new IOException("All instances unreachable")
        }
        //println values
        return values ? values : [0]
    }
}

