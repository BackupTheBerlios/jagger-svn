/*  jagger - JMX model execution tests

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

package de.web.tools.jagger.jmx.execution;

import java.lang.management.ManagementFactory;


class RemoteAttributeAccessorTest extends GroovyTestCase {
    void testInjectValues() {
        def agent = [
            url: 'test:42',
            _beans: [new Expando(bar: 47), new Expando(bar: 11),],
        ]
        def bean = [
            name: 'foo',
            lookupBeans: { it._beans },
        ]
        def raa = new RemoteAttributeAccessor(remoteBean: bean, attribute: 'bar')
        def values = raa.injectValues([], agent)

        assert values == [47, 11]
        bean.lookupBeans = { assert false, "Bean lookup was not cached!" }
        assert raa.injectValues(values, agent) == [47, 11, 47, 11]
    }

    void testToString() {
        def bean = [objectName: 'test:name=foo']
        def raa = new RemoteAttributeAccessor(remoteBean: bean, attribute: 'bar')
        assert raa.toString().contains('name=foo')
        assert raa.toString().contains('bar')
    }
}


class RemoteBeanAccessorTest extends GroovyTestCase {
    void testGetProperty() {
        def rba = new RemoteBeanAccessor(context: 'context', remoteBean: 'remoteBean')
        def foo = rba.foo
        assert foo.remoteBean == 'remoteBean'
        assert foo.attribute == 'foo'
    }
}


class ModelDelegateTest extends GroovyTestCase {
    def TEST_VALUES = [47, 11]

    private testAggregatorMethod(name, expected) {
        def context = new Expando(
            pollInstances: { acc ->
                assert acc == TEST_VALUES
                acc
            }
        )
        def md = new ModelDelegate(context: context)
        assert md."$name"(TEST_VALUES) == expected
    }

    void testGetProperty() {
        def context = new Expando(model: new Expando(remoteBeans: [foo: 'bar']))
        def md = new ModelDelegate(context: context)
        def rba = md.foo
        assert rba.getContext() == context
        assert rba.getRemoteBean() == 'bar'
    }

    void testSum() {
        testAggregatorMethod('sum', 58)
    }

    void testMin() {
        testAggregatorMethod('min', 11)
    }

    void testMax() {
        testAggregatorMethod('max', 47)
    }

    void testAvg() {
        testAggregatorMethod('avg', 29)
    }
}


//class DynamicTargetMBeanTest extends GroovyTestCase {
//}


class ExecutionContextTest extends GroovyTestCase {
    void testRegister() {
        def beans = []
        def mbs = ManagementFactory.getPlatformMBeanServer()
        def emc = new ExpandoMetaClass(DynamicTargetMBean)
        emc.registerWithServer = { server -> assert server == mbs; beans << delegate }
        emc.initialize()

        try {
            def model = new Expando(targetBeans: [
                1: new Expando(name: 'b1'),
                2: new Expando(name: 'b2'),
            ])
            def context = new ExecutionContext(model: model)
            context.register()

            beans.eachWithIndex { bean, idx ->
                bean.objectName.getKeyProperty('name') == "b${idx+1}"
            }
        } finally {
            GroovySystem.metaClassRegistry.removeMetaClass(emc.class)
        }
    }

    void testPollInstances() {
        def model = new Expando(rootCluster:
             new Expando(instances: ['test1', 'test2'])
        )
        def accessor = new Expando(
            injectValues: { result, agent ->
                result << "iV:$agent"
                result
            }
        )
        def context = new ExecutionContext(model: model)

        def emc = new ExpandoMetaClass(context.class)
        emc.getAgent = { "gA:$it" }
        emc.initialize()
        context.metaClass = emc

        context.pollInstances(accessor).eachWithIndex { val, idx ->
            assert val == ['iV:gA:test1', 'iV:gA:test2'][idx]
        }
    }
}

