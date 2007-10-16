/*  jagger - Remote JMX Tomcat Access

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

import javax.management.InstanceNotFoundException;
import javax.management.AttributeNotFoundException;


/**
 *  Facade to Tomcat 5.5 container including Java Service Wrapper.
 *  This hides some of the more gory details of JMX data structures and
 *  caches data where possible.
 */
class TomcatFacade {
    // Java Service Wrapper
    private jswBean = null

    // Catalina
    private serverBean = null

    // Connectors (map sorted by name)
    private connectors = null

    // JDBC datasources (map sorted by name)
    private datasources = null

    // reference to JMX agent proxy
    def agent = null


    /**
     *  Changes the JMX agent to a new one, effectively changing the
     *  JMX connection. This reloads several cached entities, that only
     *  change when the underlying JVM is a different one.
     *
     *  @param agent The new JMX agent.
     */
    void setAgent(agent) {
        // remember connection
        this.agent = agent

        // reset caches
        jswBean = null
        serverBean = null
        connectors = null
        datasources = null

        // no agent?
        if (agent == null) return

        // get beans for this agent
        try {
            // objectname using WrapperManager.mbean=true
            jswBean = agent.getBean('org.tanukisoftware.wrapper:type=WrapperManager')
        } catch (InstanceNotFoundException dummy1) {
            try {
                // older mbean name exported by "WrapperLifecycleListener" patch
                jswBean = agent.getBean('JavaServiceWrapper:service=WrapperManager')
            } catch (InstanceNotFoundException dummy2) {
                // JSW is optional
                jswBean = null
            }
        }
        serverBean = agent.getBean('Catalina:type=Server')

        // query the connectors
        connectors = new TreeMap()
        agent.queryBeans('Catalina:type=ThreadPool,*') { name, bean ->
            String key = name.getKeyProperty('name')
            connectors[key] = [:]
            connectors[key].threads = bean
            connectors[key].requests = agent.getBean(
                "Catalina:type=GlobalRequestProcessor,name=${key}"
            )
        }
        
        // query the SQL datasources
        datasources = new TreeMap()
        agent.queryBeans('Catalina:type=DataSource,class=javax.sql.DataSource,*') { name, bean ->
            String key = name.getKeyProperty('name')
            datasources[key] = bean
        }
    }

    /**
     *  Return connector info.
     *
     *  @return Sorted map of connector info, each consisting of
     *          a "threads" and a "requests" MBean.
     */
    def getConnectors() { connectors }

    /**
     *  Return datasource info.
     *
     *  @return Sorted map of datasource MBeans.
     */
    def getDataSources() { datasources }

    /**
     *  Return webapplication info.
     *
     *  @return Sorted map of contexts, each consisting of
     *          a "session" and a "webapp" MBean.
     */
    def getContexts() {
        def contexts = new TreeMap()
        agent.queryBeans('Catalina:type=Manager,*') { name, bean ->
            String host = name.getKeyProperty('host')
            String path = name.getKeyProperty('path')
            String key = "//${host}${path}"
            String webappName = "Catalina:j2eeType=WebModule,name=${key},J2EEApplication=none,J2EEServer=none"
            
            contexts[key] = [:]
            contexts[key].session = bean
            contexts[key].webapp = agent.getBean(webappName)
        }
        return contexts
    }

    /**
     *  Return the instance number of the JVM.
     *
     *  @return Counter which identifies the instance the JSW has created
     *          during his lifetime (initially 1).
     */
    def getJVMId() { jswBean ? jswBean.JVMId : '1' }

    /**
     *  Return version information.
     *
     *  @return Map with "jsw" and "tomcat" versions.
     */
    def getVersions() {
        def result = [
            jsw: jswBean ? jswBean.Version : 'N/A',
            tomcat: 'N/A',
        ]

        // try to get Tomcat's version, older ones don't have this
        try {
            result.tomcat = serverBean.serverInfo
        } catch (AttributeNotFoundException) {}

        return result
    }
}

