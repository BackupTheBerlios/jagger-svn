/*  jagger - Remote JMX Agent Access

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
import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;


/**
 *  Facade and proxy to a remote JMX agent. Handles credentials and provides
 *  some groovyfied helpers to access its MBeans.
 */
class JMXAgentFacade {
    // connection to remote agent
    private jmxConnection = null;

    // JMX service url
    def url = null

    // credentials: username
    def username = null

    // credentials: password
    def password = null


    /**
     *  Open connection to remote agent.
     *
     *  @return Opened connection.
     */
    synchronized private openConnection() {
        // create connection on demand
        if (jmxConnection == null) {
            def jmxEnv = null
            if (password != null) {
                // this is the form Tomcat expects credentials, other
                // containers might differ!
                jmxEnv = [(JMXConnector.CREDENTIALS):
                    [this.username, this.password] as String[]
                ]
            }
            
            // create connection
            def connector = JMXConnectorFactory.connect(new JMXServiceURL(this.url), jmxEnv)
            jmxConnection = connector.mBeanServerConnection
        }

        return jmxConnection
    }

    /**
     *  Force a bean description to be an ObjectName
     *
     *  @param objname Either a String or ObjectName describing a MBean.
     *  @return ObjectName for the given name.
     */
    static ObjectName makeObjectName(objname) {
        // easy way out?
        if (ObjectName.isInstance(objname)) {
            return objname
        }

        // treat everything not already an ObjectName as a String
        // and create an ObjectName from it
        return new ObjectName(objname)
    }

    /**
     *  Get a single MBean and return it as a GroovyMBean.
     *
     *  @param objname Either a String or ObjectName describing the bean.
     *  @return GroovyMBean for the given name.
     */
    GroovyMBean getBean(objname) {
        // create and return proxy
        return new GroovyMBean(openConnection(), makeObjectName(objname))
    }

    /**
     *  Query and iterate over a set of MBeans and call "handler"
     *  with the bean's name and a GroovyMBean already created.
     *
     *  @param query Either a String or ObjectName containing the query pattern.
     *  @param handler A closure that gets called for every bean found.
     */
    void queryBeans(query, handler) {
        def queryName = makeObjectName(query)
        def conn = openConnection()

        conn.queryMBeans(queryName, null).each { mbean ->
            //println mbean.inspect() + mbean.dump()
            def beanName = mbean.objectName
            def gbean = new GroovyMBean(conn, beanName)
            //handler('name': beanName, 'mbean': gbean)
            handler(beanName, gbean)
        }
    }
}

