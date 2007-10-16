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
        testAggregatorMethod(name, expected, TEST_VALUES)
    }
    
    private testAggregatorMethod(name, expected, values) {
        def context = new Expando(
            pollInstances: { acc ->
                assert acc == values
                acc
            }
        )
        def md = new ModelDelegate(context: context)
        def val = md."$name"(values)
        assert val == expected
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
        testAggregatorMethod('sum', null, [])
    }

    void testMin() {
        testAggregatorMethod('min', 11)
        testAggregatorMethod('min', null, [])
    }

    void testMax() {
        testAggregatorMethod('max', 47)
        testAggregatorMethod('max', null, [])
    }

    void testAvg() {
        testAggregatorMethod('avg', 29)
        testAggregatorMethod('avg', null, [])
    }

    void testMedian() {
        testAggregatorMethod('median', 29)
        testAggregatorMethod('median', null, [])
        testAggregatorMethod('median', 1, [1])
        testAggregatorMethod('median', 2, [4, 1])
        testAggregatorMethod('median', 2, [3, 1])
        testAggregatorMethod('median', 2, [3, 1, 2])
        testAggregatorMethod('median', 2, [1, 3, 3, 2])
        testAggregatorMethod('median', 2, [1, 42, 2, 2])

        testAggregatorMethod('median', 2.5, [4.0, 1.0])
        testAggregatorMethod('median', 2.0, [3.0, 1.0, 2.0])
        testAggregatorMethod('median', 2.0, [2.0, 3.0, 1.0, 2.0])
    }
}


//class DynamicTargetMBeanTest extends GroovyTestCase {
//}


class ExecutionContextTest extends GroovyTestCase {
    void testRegister() {
        def beans = []
        def mbs = new Expando([
            registerMBean: { bean, name -> beans << bean },
        ])

        def model = new Expando(targetBeans: [
            1: new Expando(name: 'b1'),
            2: new Expando(name: 'b2'),
        ])
        def context = new ExecutionContext(model: model, mbeanServer: mbs)
        context.register()

        beans.eachWithIndex { bean, idx ->
            assert bean.objectName.getKeyProperty('name') == "b${idx+1}"
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

