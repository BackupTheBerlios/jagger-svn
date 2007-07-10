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

    $Id: TomcatFacade.groovy 122570 2007-07-06 07:58:43Z jhe $
*/

package de.web.tools.jagger.jmx;

import javax.management.AttributeNotFoundException


class TomcatFacade {
    private jswBean = null
    private serverBean = null
    private connectors = null
    private datasources = null

    def agent = null

    void setAgent(agent) {
        this.agent = agent

        // get beans for this agent
        jswBean = agent.getBean('JavaServiceWrapper:service=WrapperManager')
        serverBean = agent.getBean('Catalina:type=Server')

        connectors = new TreeMap()
        agent.queryBeans('Catalina:type=ThreadPool,*') { name, bean ->
            String key = name.getKeyProperty('name')
            connectors[key] = [:]
            connectors[key].threads = bean
            connectors[key].requests = agent.getBean(
                "Catalina:type=GlobalRequestProcessor,name=${key}"
            )
        }
        
        datasources = new TreeMap()
        agent.queryBeans('Catalina:type=DataSource,class=javax.sql.DataSource,*') { name, bean ->
            String key = name.getKeyProperty('name')
            datasources[key] = bean
        }
    }

    def getConnectors() { connectors }
    def getDataSources() { datasources }

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

    def getJVMId() { jswBean.JVMId }

    def getVersions() {
        def result = [
            jsw: jswBean.Version,
            tomcat: 'N/A',
        ]
        try {
            result.tomcat = serverBean.serverInfo
        } catch (AttributeNotFoundException) {}
        return result
    }
}

