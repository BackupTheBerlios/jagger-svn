/*  jagger - JMX model description tests

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

package de.web.tools.jagger.jmx.model;

import javax.management.ObjectName;


class JmxInstanceTest extends GroovyTestCase {
    private static final CLUSTER = [
        model: [clusters: [:], remoteBeans: [:], rootCluster: CLUSTER, targetBeans: [:]],
        parent: null,
        name: 'MockedCluster',
        children: [],
    ]

    void testPortCheck() {
        [0, 66536].each {
            shouldFail(AssertionError) {
                new JmxInstance(CLUSTER, 'example.com', it)
            }
        }
    }

    void testCtor() {
        CLUSTER.children = []

        def ji = new JmxInstance(CLUSTER, 'example.com', 12345)
        assertEquals CLUSTER, ji.cluster
        assert ji.url.contains('//example.com:12345/')
        assertEquals([ji], CLUSTER.children)
    }

    void testUrlCtor() {
        CLUSTER.children = []

        def ji = new JmxInstance(CLUSTER, 'service:jmx:foo', 0)
        assertEquals('service:jmx:foo', ji.url)
    }

    void testGetInstances() {
        def ji = new JmxInstance(CLUSTER, 'example.com', 12345)
        assertEquals([ji], ji.instances)
    }

    void testToString() {
        def ji = new JmxInstance(CLUSTER, 'example.com', 12345)
        assert ji.toString().contains('example.com')
    }
}


class JmxClusterTest extends GroovyTestCase {
    private makeTree() {
        def model = [clusters: [:], rootCluster: [children: []]]

        def c1 = new JmxCluster(model, model.rootCluster, 'Cluster1')
        def c2 = new JmxCluster(model, c1, 'Cluster2')
        new JmxInstance(c1, 'example.com', 1)
        new JmxInstance(c2, 'example.com', 2)

        return c2
    }

    void testCtor() {
        def model = [clusters: [:], rootCluster: [children: []]]
        def c1 = new JmxCluster(model, model.rootCluster, 'Cluster1')

        assertEquals(model, c1.model)
        assertEquals(model.rootCluster, c1.parent)
        assertEquals('Cluster1', c1.name)
        assert !c1.children

        assertEquals(c1, model.clusters[c1.name])
        assertEquals([c1], model.rootCluster.children)
    }

    void testClusterTree() {
        def c2 = makeTree()
        def c1 = c2.parent

        assertEquals(c1.children.size(), 2)
        assertEquals(c2.children.size(), 1)
        assert c2.children[0].url.contains('//example.com:2/')
        assertEquals(c2.instances, c2.children)
        assertEquals([], c1.instances.collect{ it.url } -
            ['service:jmx:rmi:///jndi/rmi://example.com:2/jmxrmi',
             'service:jmx:rmi:///jndi/rmi://example.com:1/jmxrmi'])
    }

    void testToString() {
        def c2 = makeTree()
        def text = c2.parent.toString()

        assert text.contains('Cluster1')
        assert text.contains('Cluster2')
        assert text.contains('example.com:1')
        assert text.contains('example.com:2')
    }
}


class JmxRemoteBeanTest extends GroovyTestCase {
    void testCtor() {
        def model = [remoteBeans: [:]]

        def jrb = new JmxRemoteBean(model, 'test', new ObjectName('junit:name=test'))
        assertEquals(model, jrb.model)
        assertEquals('test', jrb.name)
        assertEquals('junit:name=test', jrb.objectName.toString())
        assertEquals(jrb, model.remoteBeans[jrb.name])

        def jrb2 = new JmxRemoteBean(model, 'test2', 'junit:name=test2')
        assertEquals('junit:name=test2', jrb2.objectName.toString())

        shouldFail(IllegalArgumentException) {
            new JmxRemoteBean(model, 'test', new ObjectName('junit:name=duplicate'))
        }
    }

    void testToString() {
        def model = [remoteBeans: [:]]
        def jrb = new JmxRemoteBean(model, 'test', new ObjectName('junit:name=test'))
        def text = jrb.toString()
        assert text.contains(jrb.name)
        assert text.contains(jrb.objectName.toString())
    }
}


class JmxTargetBeanTest extends GroovyTestCase {
    void testCtor() {
        def model = [targetBeans: [:]]

        def jtb = new JmxTargetBean(model, 'test')
        assertEquals(model, jtb.model)
        assertEquals('test', jtb.name)
        assertEquals(jtb, model.targetBeans[jtb.name])

        shouldFail(IllegalArgumentException) {
            new JmxTargetBean(model, 'test')
        }
    }

    void testToString() {
        def model = [targetBeans: [:]]
        def jtb = new JmxTargetBean(model, 'test')

        assert jtb.toString().contains('test')
        assert !jtb.toString().contains('smurf')
        assert !jtb.toString().contains('Attribute')

        jtb.description = 'smurf'
        assert jtb.toString().contains('smurf')

        jtb.attributes['foo'] = [name: 'foo', description: 'bar', expression: 'baz']
        assert jtb.toString().contains('Attribute')
        assert jtb.toString().contains('foo')
        assert jtb.toString().contains('bar')
    }
}


class JmxModelTest extends GroovyTestCase {
    void testCtor() {
        def m = new JmxModel()
        assertEquals(m.rootCluster, m.clusters[''])
        assert !m.remoteBeans
        assertEquals(m, m.rootCluster.model)
        assertEquals(null, m.rootCluster.parent)
        assert !m.targetBeans
    }

    void testToString() {
        def m = new JmxModel()
        def text = m.toString()

        assert text.contains("Cluster ''")
    }
}


