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

    $Id: JmxAgentFacade.groovy 122570 2007-07-06 07:58:43Z jhe $
*/

package de.web.tools.jagger.jmx;

// Imports
import javax.management.ObjectName
import javax.management.MBeanServer
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXConnector
import javax.management.remote.JMXServiceURL


class JMXAgentFacade {
    private jmxConnection = null;

    def url = null;
    def username = null;
    def password = null;

    // Open connection to remote agent.
    private openConnection() {
        if (this.jmxConnection == null) {
            def jmxEnv = null
            if (password != null)
                jmxEnv = [(JMXConnector.CREDENTIALS): [this.username, this.password] as String[]]
            def connector = JMXConnectorFactory.connect(new JMXServiceURL(this.url), jmxEnv)
            this.jmxConnection = connector.mBeanServerConnection
        }

        return this.jmxConnection
    }

    /** Get a single MBean and return it as a GroovyMBean.
     */
    def getBean(objname) {
        def beanName = objname
        if (!ObjectName.isInstance(beanName))
            beanName = new ObjectName(beanName)

        return new GroovyMBean(openConnection(), beanName)
    }

    /** Query and iterate over a set of MBeans and call "handler"
        with the bean's name and a GroovyMBean already created.
     */
    def queryBeans(query, handler) {
        def queryName = new ObjectName(query)

        openConnection().queryMBeans(queryName, null).each { mbean ->
            //println mbean.inspect() + mbean.dump()
            def beanName = mbean.objectName
            def gbean = new GroovyMBean(openConnection(), beanName)
            handler(beanName, gbean)
        }
    }

}


