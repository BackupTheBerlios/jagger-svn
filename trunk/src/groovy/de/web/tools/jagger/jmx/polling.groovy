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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.management.ObjectName;

import de.web.tools.jagger.jmx.JMXAgentFacade;
import de.web.tools.jagger.jmx.execution.AttributeEvaluationCategory;


/**
 *  Decorator for remote bean alias evaluation.
 */
class RemoteBeanAliasEvaluator extends groovy.util.Proxy {
    private static Log log = LogFactory.getLog(RemoteBeanAliasEvaluator.class)

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
        if (log.isTraceEnabled()) {
            log.trace("getProperty for alias $name")
        }
        if (getAliases().containsKey(name)) {
            if (log.isTraceEnabled()) {
                log.trace("Evaluating alias $name")
            }
            def alias = getAliases()[name]
            synchronized (alias) {
                // context for the alias expression is the bean containing it
                alias.delegate = getAdaptee()
                alias.resolveStrategy = Closure.DELEGATE_ONLY
                use(AttributeEvaluationCategory) {
                    result = alias.call(getAdaptee())
                }
            }
        } else {
            result = getAdaptee().getProperty(name)
        }
        return result
    }
}


/**
 *  Poller for named beans on remote JVMs.
 */
class BeanPoller {
    private static Log log = LogFactory.getLog(BeanPoller.class)

    // polling context
    private context
    
    // cache for resolved remote mbeans (lists of GroovyMBeans) indexed by bean name
    private beanCache = [:]

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
     *  @param context The polling context.
     *  @param bean The remote bean definition in the model.
     */
    public BeanPoller(context, bean) {
        this.context = context
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
        assert bean != null
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
                if (log.isTraceEnabled()) {
                    log.trace("JMX query returned $name")
                }
                if (passesFilter(name)) {
                    result << withAliases(bean)
                }
            }
            return result
        }
    }

    /**
     *  Poll all remote beans in the given agent.
     *
     *  @param result List the new values get injected into.
     *  @param agent Remote agent (connection).
     *  @param attribute Name of attribute to inject.
     *  @return Extended list.
     */
    public injectValues(result, agent, attribute) {
        def beans
        synchronized (beanCache) {
            if (!beanCache.containsKey(agent.url)) {
                beanCache[agent.url] = [:]
            }
            def cachedBeans = beanCache[agent.url]
            if (cachedBeans.containsKey(remoteBean.name)) {
                beans = cachedBeans[remoteBean.name]
            } else {
                try {
                    beans = lookupBeans(agent)
                } catch (java.rmi.ConnectException ex) {
                    failAgent(agent.url, ex)

                    // return unchanged result
                    return result
                }
                if (log.isTraceEnabled()) {
                    log.trace("Lookup for '$remoteBean.name' returned ${beans.collect { it.name().canonicalName }.join(', ')}")
                }
                if (beans) {
                    cachedBeans[remoteBean.name] = beans
                }
            }
        }

        try {
            beans.each {
                def val = it.getProperty(attribute)

                if (log.isTraceEnabled()) {
                    log.trace("${agent.url}:${it.name().canonicalName}.${attribute} = $val")
                }
                result << val
            }
        } catch (java.rmi.ConnectException ex) {
            failAgent(agent.url, ex)
        } catch (javax.management.InstanceNotFoundException ex) {
            failAgent(agent.url, ex)
        }

        return result
    }

    void clearAgent(jmxUrl) {
        synchronized (beanCache) {
            // clear cache for failed instance
            beanCache.remove(jmxUrl)

            if (log.isTraceEnabled()) {
                log.trace("Cache after clean for bean $remoteBean.name => ${beanCache.keySet().dump()}")
            }
        }
    }

    void failAgent(jmxUrl, ex) {
        clearAgent(jmxUrl)
        context.failAgent(jmxUrl, ex)
    }
}


/**
 *  Controls polling of remote mbeans.
 *
 *  Has to be thread-safe.
 */
class PollingContext {
    private static Log log = LogFactory.getLog(PollingContext.class)

    // cache for connection facades to remote agents, indexed by URL
    private agentCache = [:]

    // cache for bean pollers
    private beanCache = [:]

    // map of previously failed instances
    private failedAgents = [:]


    /**
     *  Return agent facade for the given model instance.
     *
     *  @param instance Instance object in the model.
     *  @return Agent facade (connection).
     */
    public synchronized getAgent(instance) {
        if (!agentCache.containsKey(instance.url)) {
            if (log.isTraceEnabled()) {
                log.trace("Connecting to ${instance.toString()}...")
            }
            def agent = new JMXAgentFacade(url: instance.url, username: instance.username, password: instance.password)
            agent.openConnection()
            agentCache[instance.url] = agent
        }

        return agentCache[instance.url]
    }

    public synchronized getBeanPoller(remoteBean) {
        if (!beanCache.containsKey(remoteBean.name)) {
            beanCache[remoteBean.name] = new BeanPoller(this, remoteBean)
        }

        return beanCache[remoteBean.name]
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
            } catch (java.rmi.ConnectException ex) {
                failAgent(instance.url, ex)
            } catch (IOException ex) {
                failAgent(instance.url, ex)
            }

            if (agent) {
                synchronized(this) {
                    if (failedAgents.containsKey(instance.url)) {
                        if (log.isInfoEnabled()) {
                            log.info("Instance $instance.url up again!")
                        }

                        // need to clean all bean caches
                        beanCache.each { name, bean ->
                            if (log.isTraceEnabled()) {
                                log.trace("Clearing bean $name with $agent.url")
                            }
                            bean.clearAgent(agent.url)
                        }
                        failedAgents.remove(instance.url)
                    }                            
                }                            

                ++reachable
                accessor.injectValues(result, agent)
            }
            return result
        }
        if (!reachable) {
            throw new IOException("All instances unreachable")
        }
        
        if (log.isTraceEnabled()) {
            log.trace("polling of ${instances.url.join(', ')} returned $values")
        }
        return values ? values : [0]
    }

    synchronized void failAgent(jmxUrl, ex) {
        // XXX need to better handle failed servers, at least provide
        // a list of those as a target bean attribute
        if (log.isWarnEnabled()) {
            log.warn("Instance $jmxUrl failed!")
        }

        // clear cache for failed instance
        agentCache.remove(jmxUrl)

        failedAgents[jmxUrl] = ex.message

        if (log.isTraceEnabled()) {
            log.trace("Cached agents = ${agentCache.keySet().dump()}")
            log.trace("Failed agents = ${failedAgents.keySet().dump()}")
        }
    }
}

