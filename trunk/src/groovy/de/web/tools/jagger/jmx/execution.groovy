/*  jagger - JMX model execution (evaluation)

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
import de.web.tools.jagger.jmx.model.ModelEvaluationCategory;


/**
 *  Base class for target bean attribute closure accessors.
 */
abstract class AttributeAccessorBase {
    /**
     *  Poll all remote beans in the given agent.
     *
     *  @param result List the new values get injected into.
     *  @param agent Remote agent (connection).
     *  @return Extended list.
     */
    abstract public injectValues(result, agent)

    public plus(rhs)     { new OperationAccessor(this, rhs, { a, b -> a + b }) }
    public minus(rhs)    { new OperationAccessor(this, rhs, { a, b -> a - b }) }
    public multiply(rhs) { new OperationAccessor(this, rhs, { a, b -> a * b }) }
    public div(rhs)      { new OperationAccessor(this, rhs, { a, b -> a / b }) }
}


/**
 *  Object returned for 2nd-level names (remote bean attributes) when evaluating
 *  target bean attribute closures.
 */
class RemoteAttributeAccessor extends AttributeAccessorBase {
    // cache for resolved remote mbeans (lists of GroovyMBeans) indexed by bean name
    private beanCache = [:]

    // proxy for remote bean
    def remoteBean

    // name of the remote attribute
    def attribute


    /**
     *  Poll all remote beans in the given agent.
     *
     *  @param result List the new values get injected into.
     *  @param agent Remote agent (connection).
     *  @return Extended list.
     */
    public injectValues(result, agent) {
        def beans
        synchronized (beanCache) {
            if (!beanCache.containsKey(agent.url)) {
                beanCache[agent.url] = [:]
            }
            def cachedBeans = beanCache[agent.url]
            if (!cachedBeans.containsKey(remoteBean.name)) {
                cachedBeans[remoteBean.name] = remoteBean.lookupBeans(agent)
                //println "Lookup for '$remoteBean.name' returned ${cachedBeans[remoteBean.name].collect { it.name().canonicalName }.join(', ')}" }
            }
            beans = cachedBeans[remoteBean.name]
        }

        beans.each {
            def val = it.getProperty(attribute)
            //println "${instance.url}:${toString()} = $val"
            result << val
        }

        return result
    }

    /**
     *  Converts metainfo to text for debugging.
     *
     *  @return Name of bean and attribute.
     */
    public toString() {
        return "$attribute@${remoteBean.objectName}"
    }    
}


/**
 *  Object returned for arithmetic operations on accessors.
 */
class OperationAccessor extends AttributeAccessorBase {
    // left-hand side operand
    def lhs

    // right-hand side operand
    def rhs

    // operator to use (as a closure)
    def operator


    public OperationAccessor(lhs, rhs, operator) {
        this.lhs = lhs
        this.rhs = rhs
        this.operator = operator
    }

    /**
     *  Poll all remote beans in the given agent and create result list.
     *
     *  @param result List the new values get injected into.
     *  @param agent Remote agent (connection).
     *  @return Extended list.
     */
    public injectValues(result, agent) {
        def lhsValues = lhs.injectValues([], agent)
        def rhsValues
        try {
            // accessor?
            rhsValues = rhs.injectValues([], agent)
        } catch (MissingMethodException ex) {
            // assume scalar
            rhsValues = [rhs] * lhsValues.size()
        }

        assert lhsValues.size() == rhsValues.size()
        lhsValues.eachWithIndex { lhsValue, idx ->
            result << operator.call(lhsValue, rhsValues[idx])
        }

        return result
    }
}


/**
 *  Object returned for top-level names (remote beans) when evaluating
 *  target bean attribute closures.
 */
class RemoteBeanAccessor {
    // the execution context
    def context

    // remote bean to access
    def remoteBean

    /**
     *  Returns accessor for the given property name.
     *
     *  @param attribute Name of remote bean attribute.
     *  @return Accessor for <bean>.<attribute>.
     */
    public Object getProperty(final String attribute) {
        //println "GET $attribute@${remoteBean.name}"
        new RemoteAttributeAccessor(remoteBean: remoteBean, attribute: attribute)
    }    
}


/**
 *  Delegate for evaluation of the target bean attribute closures.
 *
 *  Since this is shared by all attributes of one target mbean, it has
 *  to be thread-safe.
 */
class ModelDelegate {
    // the execution context
    def context


    /**
     *  Returns accessor for the given remote bean name.
     *
     *  @param property Name of remote bean.
     *  @return Accessor for <property>.
     */
    public Object getProperty(final String property) {
        //println "Accessing bean $property"
        return new RemoteBeanAccessor(context: context, remoteBean: context.model.remoteBeans[property])
    }

    /**
     *  Aggregator method for the sum of all remote values.
     *
     *  @param accessor Accessor for the remote attribute.
     *  @return Aggregated value.
     */
    public sum(accessor) {
        context.pollInstances(accessor).sum()
    }

    /**
     *  Aggregator method for the minimum of all remote values.
     *
     *  @param accessor Accessor for the remote attribute.
     *  @return Aggregated value.
     */
    public min(accessor) {
        context.pollInstances(accessor).min()
    }

    /**
     *  Aggregator method for the maximum of all remote values.
     *
     *  @param accessor Accessor for the remote attribute.
     *  @return Aggregated value.
     */
    public max(accessor) {
        context.pollInstances(accessor).max()
    }

    /**
     *  Aggregator method for the average of all remote values.
     *
     *  @param accessor Accessor for the remote attribute.
     *  @return Aggregated value.
     */
    public avg(accessor) {
        def values = context.pollInstances(accessor)
        if (!values) return

        return values.sum() / values.size()
    }

    /**
     *  Aggregator method for the median of all remote values.
     *
     *  @param accessor Accessor for the remote attribute.
     *  @return Aggregated value.
     */
    public median(accessor) {
        def values = context.pollInstances(accessor)
        if (!values) return
        values.sort()

        def m = (values.size() / 2) as Integer
        if (values.size() & 1) {
            return values[m]
        } else {
            return ((values[m-1] + values[m]) / 2).asType(values[m].class)
        }
    }
}


/**
 *  Adaptor of a target mbean definition to the dynamic mbean interface.
 *
 *  All methods of the DynamicMBean interface MUST be thread-safe.
 */
class DynamicTargetMBean implements DynamicMBean {
    private static runtimePackages = [
        'sun.reflect.',
        'java.lang.reflect.',
        'org.codehaus.groovy.',
        'groovy.lang.',
    ]

    // the target bean
    private bean

    // exported object name
    private objectName

    // delegate for closure evaluation
    private modelDelegate

    // mbean metadata
    private mbeanInfo


    /**
     *  Creates a dynamic mbean instance for the given target bean definition.
     *
     *  @param delegate Model delegate.
     *  @param bean Target bean.
     */
    public DynamicTargetMBean(delegate, bean) {
        this.bean = bean
        this.modelDelegate = delegate
        objectName = new ObjectName("de.web.management:type=Aggregator,name=${bean.name}")
    }

    /**
     *  Helper to calculate the current value for attribute "name".
     *
     *  @param name Name of the attribute.
     *  @return Aggregated value.
     */
    private getAttributeValue(name) {
        def result
        def attribute = bean.attributes[name]

        try {
            // XXX Check whether this critical section could be smaller
            synchronized (attribute) {
                attribute.expression.delegate = modelDelegate
                //attribute.expression.resolveStrategy = Closure.DELEGATE_ONLY
                use(ModelEvaluationCategory) {
                    result = attribute.expression.call()

                    // unaggregated values?
                    if (result instanceof AttributeAccessorBase) {
                        // poll them and return list
                        result = modelDelegate.getContext().pollInstances(result)
                    }
                }
            }
            
            result = result as String
        } catch (Exception ex) {
            // log evaluation errors to console
            println 'v' * 78
            println "Error while evaluating ${bean.name}.$name"
            println "$ex"
            ex.stackTrace.each { frame ->
                if (null == runtimePackages.find { frame.className.startsWith(it) }) {
                    println "    ${frame}"
                }
            }
            println '^' * 78
            throw ex
        }

        return result
    }

    /**
     *  Read-only property for the calculated objectname.
     */
    public getObjectName() { objectName }

    /**
     *  Registers this mbean with the given agent.
     *
     *  @param mbs MBean server.
     */
    public registerWithServer(mbs) {
        mbs.registerMBean(this, objectName)
    }

    /**
     *  Get a single attribute.
     *
     *  @param name Name of the attribute.
     *  @return Value of the attribute.
     */
    public Object getAttribute(String name)
            throws AttributeNotFoundException {
        if (!bean.attributes.containsKey(name)) {
            throw new AttributeNotFoundException("No such property: $name")
        }
        return getAttributeValue(name)
    }

    /**
     *  Set a single attribute; since this mbean is immutable, always throws
     *  an MBeanException.
     */
    public void setAttribute(Attribute attribute)
            throws InvalidAttributeValueException, MBeanException, AttributeNotFoundException {
        throw new MBeanException('This mbean is immutable')
    }

    /**
     *  Get a specified list of attributes.
     *
     *  @param names Array of requested attribute names.
     *  @return List of available attributes (name/value pairs).
     */
    public AttributeList getAttributes(String[] names) {
        def result = new AttributeList(names.size())
        names.each { name ->
            if (bean.attributes.containsKey(name)) {
                result << new Attribute(name, getAttributeValue(name))
            }
        }
        return result
    }

    /**
     *  Sets a list of attributes and returns those set successfully; since
     *  this mbean is immutable, always returns an empty list.
     */
    public AttributeList setAttributes(AttributeList list) {
        []
    }

    /**
     *  Invoke a method on this bean, always throws a ReflectionException.
     */
    public Object invoke(String name, Object[] args, String[] sig)
            throws MBeanException, ReflectionException {
        throw new ReflectionException(new NoSuchMethodException(name))
    }
    
    /**
     *  Returns the meta-data of this mbean,
     *
     *  @return Metadata adhering to the Open MBean specification.
     */
    public synchronized MBeanInfo getMBeanInfo() {
        if (mbeanInfo == null) {
            // create a list of attribute descriptors
            def attrs = []
            bean.attributes.values().each { attribute ->
                attrs << new OpenMBeanAttributeInfoSupport(
                    attribute.name,
                    attribute.description ? attribute.description : "Managed attribute '$attribute.name'",
                    SimpleType.STRING,
                    true,   // isReadable
                    false,  // isWritable
                    false)  // isIs
            }

            // create the mbean info
            mbeanInfo = new OpenMBeanInfoSupport(
                bean.'class'.name, bean.description,
                attrs as OpenMBeanAttributeInfo[],
                null, null, null)
        }

        // return (pre-)created metadata
        return mbeanInfo
    }
}


/**
 *  Context that holds runtime information during the aggregation process and
 *  manages the model evaluation.
 *
 *  Since this is shared by all target beans, it has to be thread-safe.
 */
class ExecutionContext {
    // cache for connection facades to remote agents, indexed by URL
    private agentCache = [:]

    // the model to execute
    def model


    /**
     *  Defines and registers a dynamic mbean for each target bean defined
     *  in the model.
     */
    public void register() {
        def mbs = ManagementFactory.getPlatformMBeanServer()
        def delegate = new ModelDelegate(context: this)
        model.targetBeans.values().each { bean ->
            def mb = new DynamicTargetMBean(delegate, bean)
            mb.registerWithServer(mbs)
        }
    }

    /**
     *  Return agent facade for the given model instance.
     *
     *  @param instance Instance object in the model.
     *  @return Agent facade (connection).
     */
    public synchronized getAgent(instance) {
        if (!agentCache.containsKey(instance.url)) {
            //println "Connecting to ${instance.toString()}..."
            def agent = new JMXAgentFacade(url: instance.url, username: instance.username, password: instance.password)
            agent.openConnection()
            agentCache[instance.url] = agent
        }

        return agentCache[instance.url]
    }

    /**
     *  Poll all defined instances for the values of the given bean attribute
     *  accessor.
     *
     *  @param accessor RemoteAttributeAccessor object.
     *  @return List of current values.
     */
    public pollInstances(accessor) {
        model.rootCluster.instances.inject([]) { result, instance ->
            accessor.injectValues(result, getAgent(instance))
        }
    }
}

