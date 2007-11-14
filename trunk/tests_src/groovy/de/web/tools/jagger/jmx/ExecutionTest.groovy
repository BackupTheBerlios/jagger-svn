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


class AttributeEvaluationCategoryTest extends GroovyTestCase {
    void testNonzero() {
        use (AttributeEvaluationCategory) {
            assert 0.nonzero != 0
            assert 1.nonzero == 1
            assert 0.nonzero instanceof Integer
            assert (0.0).nonzero instanceof BigDecimal
        }
    }

    void testPercent() {
        use (AttributeEvaluationCategory) {
            assert 1.percent == 100.0
            assert 1.percent instanceof BigDecimal
            assert (0.00004).percent == 0.00
            assert (0.00005).percent == 0.01
        }
    }

    void testScale() {
        use (AttributeEvaluationCategory) {
            assert 1.scale(0) instanceof BigDecimal
            assert (12345).scale(-2) == 12300
            assert (1.2345).scale(0) == 1.0
            assert (1.2345).scale(1) == 1.2
            assert (1.2345).scale(2) == 1.23
            assert (1.2344).scale(3) == 1.234
            assert (1.2346).scale(3) == 1.235
        }
    }
}


class RemoteAttributeAccessorTest extends GroovyTestCase {
    void testInjectValues() {
        def poller = [
            injectValues: { result, agent, attribute ->  result << agent << attribute; result },
        ]
        def beanAccessor = [
            getContext: { [
                poller: [getBeanPoller: { rb -> poller.rb = rb; poller } ]
            ] },
            getRemoteBean: { 'rb' },
        ]
        def raa = new RemoteAttributeAccessor(beanAccessor, 'bar')
        def values = raa.injectValues(['foo'], 'agent')

        assert values == ['foo', 'agent', 'bar']
    }

    void testToString() {
        def remoteBean = [objectName: 'test:name=foo']
        def beanAccessor = [
            getContext: { [
                poller: [getBeanPoller: { [remoteBean: remoteBean] } ]
            ] },
            getRemoteBean: { remoteBean },
        ]
        def raa = new RemoteAttributeAccessor(beanAccessor, 'bar')
        assert raa.toString().contains('name=foo')
        assert raa.toString().contains('bar')
    }
}


class RemoteBeanAccessorTest extends GroovyTestCase {
    void testGetProperty() {
        def context = [
            poller: [getBeanPoller: { rb -> [remoteBean: rb] } ]
        ]
        def rba = new RemoteBeanAccessor(context: context, remoteBean: 'remoteBean')
        def foo = rba.foo
        assert foo.beanPoller.remoteBean == 'remoteBean'
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
        def context = new ExecutionContext(model: model)
        context.poller = new Expando([
            pollInstances: { x, y -> [x, y] },
        ])

        assert context.pollInstances('test3') == [['test1', 'test2'], 'test3']
    }
}

