/*  jagger - Remote JMX Agent Access unit tests

    Copyright (c) 2007 by 1&1 Internet AG

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as
    published by the Free Software Foundation. A copy of this license is
    included with this software in the file "LICENSE.txt".

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    $Id: JmxAgentFacade.groovy 44 2007-07-25 12:27:29Z jhermann $
*/

package de.web.tools.jagger.jmx;

import java.lang.management.ManagementFactory;

import javax.management.ObjectName;
import javax.management.InstanceNotFoundException;
//import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServerFactory;
//import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;


/**
 *  Unit tests for JMX agent facade.
 */
class JMXAgentFacadeTest extends GroovyTestCase {

    private void usingPlatformServer(testCode) {
        def mbs = ManagementFactory.getPlatformMBeanServer()
        def url = new JMXServiceURL("rmi", null, 0)
        def cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs)

        cs.start()
        try {
            def address = cs.getAddress() as String
            testCode(address)
        } finally {
            cs.stop()
        }
    }

    private void usingPlatformAgent(testCode) {
        usingPlatformServer {
            def agent = new JMXAgentFacade(url: it)
            testCode(agent)
        }
    }

    void testConnect() {
        usingPlatformServer {
            def agent = new JMXAgentFacade(url: it)
            def conn = agent.openConnection()

            // must always return same object
            (0..5).each { assertSame(conn, agent.openConnection()) }
        }
    }

    void testMakeObjectName() {
        def strname = "JUnit:name=test"
        def objname = new ObjectName(strname)

        assertSame(objname, JMXAgentFacade.makeObjectName(objname))
        assertNotSame(objname, JMXAgentFacade.makeObjectName(strname))
        assertEquals(objname, JMXAgentFacade.makeObjectName(strname))
        assertEquals(objname.canonicalName, strname)
        assertEquals(strname, JMXAgentFacade.makeObjectName(strname).canonicalName)
    }

    void testGetBean() {
        usingPlatformAgent { agent ->
            shouldFail(InstanceNotFoundException) {
                agent.getBean("JUnit:name=MR.NOWHEREMAN")
            }
        }
    }

    void testQueryBeans() {
    }
}

