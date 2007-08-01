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
    static final RUNTIME = 'java.lang:type=Runtime'

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

    void testOpenConnection() {
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

        assertNotNull(objname)
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

            def runtimeBean = agent.getBean(RUNTIME)
            assertNotNull(runtimeBean)
            assertEquals(runtimeBean.VmVersion, System.getProperty('java.vm.version'))
        }
    }

    void testQueryBeans() {
        usingPlatformAgent { agent ->
            def countBeans = { objname ->
                def count = 0
                agent.queryBeans(objname) { name, bean -> count++ }
                return count
            }
            
            assertEquals(0, countBeans('JUnit:name=*'))
            assertEquals(1, countBeans(RUNTIME))
            assertTrue(1 < countBeans('java.lang:type=MemoryPool,*'))

            agent.queryBeans(RUNTIME) { name, bean ->
                assertSame(bean.getClass(), GroovyMBean)
                assertEquals(name.canonicalName, RUNTIME)
                assertEquals(bean.name().canonicalName, RUNTIME)
            }

        }
    }
}

