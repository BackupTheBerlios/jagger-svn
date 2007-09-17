/*  jagger - Demon Mode Command Line Interface

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
import javax.management.ObjectName;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.ReflectionException;
import javax.management.openmbean.OpenMBeanInfoSupport;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanAttributeInfoSupport;
import javax.management.openmbean.SimpleType;

//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;

import de.web.tools.jagger.jmx.JMXAgentFacade;


class DynamicTargetMBean implements DynamicMBean {
    private bean
    private objectName
    private context
    private delegate
    
    public DynamicTargetMBean(bean) {
        this.bean = bean
        objectName = new ObjectName("de.web.management:type=Aggregator,name=${bean.name}")
        context = new ExecutionContext(model: bean.model)
        delegate = new ModelDelegate(context: context)
    }

    private getAttributeValue(name) {
        def attribute = bean.attributes[name]
        attribute.expression.delegate = delegate
        //attribute.expression.resolveStrategy = Closure.DELEGATE_ONLY
        return attribute.expression.call() as String
    }

    public getObjectName() { objectName }

    public registerWithServer(mbs) {
        mbs.registerMBean(this, objectName)
    }

    public Object getAttribute(String name)
            throws AttributeNotFoundException {
        //println "!!! GET ${bean.name}($name)"
        if (!bean.attributes.containsKey(name)) {
            throw new AttributeNotFoundException("No such property: $name")
        }
        return getAttributeValue(name)
    }

    public void setAttribute(Attribute attribute)
            throws InvalidAttributeValueException, MBeanException, AttributeNotFoundException {
        throw new MBeanException('This mbean is immutable')
    }

    public AttributeList getAttributes(String[] names) {
        def result = new AttributeList(names.size())
        //println "!!! GET ${bean.name}($names.join(', '))"
        names.each { name ->
            if (bean.attributes.containsKey(name)) {
                result << new Attribute(name, getAttributeValue(name))
            }
        }
        return result
    }

    public AttributeList setAttributes(AttributeList list) {
        []
    }

    public Object invoke(String name, Object[] args, String[] sig)
            throws MBeanException, ReflectionException {
        throw new ReflectionException(new NoSuchMethodException(name))
    }
    
    public MBeanInfo getMBeanInfo() {
        def attrs = []
        bean.attributes.each { attributeName, attribute ->
            attrs << new OpenMBeanAttributeInfoSupport(
                attribute.name,
                attribute.description ? attribute.description : "Managed attribute '$attributeName'",
                SimpleType.STRING,
                true,   // isReadable
                false,  // isWritable
                false)  // isIs
        }

        return new OpenMBeanInfoSupport(
            bean.'class'.name, bean.description, attrs as OpenMBeanAttributeInfo[], null, null, null)
    }
}


class AttributeAccessor {
    def remoteBean
    def property

    public toString() {
        return "$property@${remoteBean.objectName}"
    }    
}


class RemoteBeanAccessor {
    def context
    def remoteBean

    public Object getProperty(final String property) {
        //println "GET $property@${remoteBean.name}"
        new AttributeAccessor(remoteBean: remoteBean, property: property)
    }    
}


class ModelDelegate {
    def context

    private doAggregation(name, accessor, aggregator) {
        def values = context.pollInstances(accessor)

        "${values.inject(0, aggregator)} = $name($values)"
    }

    public Object getProperty(final String property) {
        //println "Accessing bean $property"
        return new RemoteBeanAccessor(context: context, remoteBean: context.model.remoteBeans[property])
    }

    public sum(accessor) {
        doAggregation("sum", accessor) { a, b -> a + b }
    }

    public avg(accessor) {
        // XXX TODO: does not work that way!
        doAggregation("avg", accessor) { a, b -> a + b }
    }

    public min(accessor) {
        doAggregation("min", accessor) { a, b -> [a, b].min() }
    }

    public max(accessor) {
        doAggregation("max", accessor) { a, b -> [a, b].max() }
    }
}


class ExecutionContext {
    private agentCache = [:]
    private beanCache = [:]

    Boolean tracing = true
    
    def model

    public trace(messageGenerator) {
        if (tracing) {
            println "TRACE: ${messageGenerator()}"
        }
    }

    public getAgent(instance) {
        if (!agentCache.containsKey(instance.url)) {
            trace{"Connecting to ${instance.toString()}..."}
            def agent = new JMXAgentFacade(url: instance.url, username: instance.username, password: instance.password)
            agent.openConnection()
            agentCache[instance.url] = agent
        }

        return agentCache[instance.url]
    }

    public pollInstances(accessor) {
        model.rootCluster.instances.inject([]) { result, instance ->
            def agent = getAgent(instance)
            if (!beanCache.containsKey(agent.url)) {
                beanCache[agent.url] = [:]
            }
            
            def cachedBeans = beanCache[agent.url]
            if (!cachedBeans.containsKey(accessor.remoteBean.name)) {
                cachedBeans[accessor.remoteBean.name] = accessor.remoteBean.lookupBeans(agent)
                def names = cachedBeans[accessor.remoteBean.name].collect { it.name().canonicalName }
                trace {
                    "Lookup for '$accessor.remoteBean.name' returned ${names.join(', ')}"
                }
            }

            cachedBeans[accessor.remoteBean.name].each {
                def val = it.getProperty(accessor.property)
                //trace { "${instance.url}:${accessor.toString()} = $val" }
                result << val
            }

            result
        }
    }
}


class Executor {
    def model

    public run() {
        def mbs = ManagementFactory.getPlatformMBeanServer()
        model.targetBeans.each { beanName, bean ->
            def mb = new DynamicTargetMBean(bean)
            mb.registerWithServer(mbs)
        }

        /*
        def context = new ExecutionContext(model: model)
        model.targetBeans.each { beanName, bean ->
            bean.attributes.each { attributeName, attribute ->
                attribute.expression.delegate = new ModelDelegate(context: context)
                //attribute.expression.resolveStrategy = Closure.DELEGATE_ONLY
                println "${attribute.name} = ${attribute.expression.call()}"
            }
        }
        */
    }
}

