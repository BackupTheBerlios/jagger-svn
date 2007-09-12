/*  jagger - JMX model description

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


/**
 *  A single JVM on a host.
 */
class JmxInstance {
    // agent URL
    String url

    // username
    String username

    // password
    String password

    // the cluster we belong to
    def cluster


    /**
     *  Creates a JVM instance in the model.
     *  The new instance is automatically weaved into the model.
     *
     *  @param cluster Cluster this instance is a member of.
     *  @param hostname Hostname.
     *  @param port Port of JMX RMI registry.
     *  @throws AssertionError If port number not in range 1..65535
     */
    public JmxInstance (cluster, hostname, port) {
        assert (1..65535).contains(port as Integer), "Illegal port number '${port}'" 

        this.cluster = cluster
        this.url = "${hostname}:${port}"

        cluster.children << this
    }

    /**
     *  Returns a list of instances in this object.
     *
     *  @return List containig this one instance.
     */
    def getInstances() {
        [this]
    }


    /**
     *  Generates a textual description of this object.
     *
     *  @return String representation of this object.
     */
    def toString() {
        "Instance '$url'"
    }
}


/**
 *  A single cluster.
 *
 *  This is modelled after the composite pattern, a cluster can contain either
 *  other clusters or JMXInstances.
 */
class JmxCluster {
    // indentation for toString()
    private final INDENT = '\n    '

    // the model we're part of
    def model

    // our parent cluster (or null for root)
    def parent

    // name of this cluster
    String name

    // what's in the cluster (instances and sub-clusters)
    def children = []


    /**
     *  Creates a cluster in the model.
     *  The new cluster is automatically weaved into the model.
     *
     *  @param model Model containing the new cluster.
     *  @param parent Parent cluster or null for root.
     *  @param name Name of the new cluster.
     *  @throws IllegalArgumentException For duplicate names.
     */
    public JmxCluster(model, parent, name) {
        if (model.clusters.containsKey(name)) {
            throw new IllegalArgumentException("Cluster with name '$name' already defined!")
        }

        this.model = model
        this.parent = parent
        this.name = name

        model.clusters[name] = this
        if (parent) parent.children << this
    }

    /**
     *  Returns a list of instances in this object.
     *
     *  @return Recursively collected list of instances in this cluster.
     */
    def getInstances() {
        children.inject([]) { result, child -> result + child.getInstances() }
    }

    /**
     *  Generates a textual description of this object.
     *
     *  @return String representation of this object.
     */
    def toString() {
        // append all children's representation, indenting them by one level
        def members = children
            .inject([]) { r, c -> r + c.toString().tokenize('\n') }
            .join(INDENT)

        return "Cluster '$name'$INDENT$members"
    }
}


/**
 *  Base class for named MBeans.
 */
class JmxRemoteBean {
    // logical name
    String name

    // JMX object name
    ObjectName objectName

    // the model we're part of
    def model


    /**
     *  Create a new remote bean reference.
     *
     *  @param model Model containing the bean reference.
     *  @param name Name of the new bean.
     *  @param objectName Object name in the remote JVM.
     *  @throws IllegalArgumentException For duplicate names.
     */
    protected JmxRemoteBean(model, name, objectName) {
        if (model.remoteBeans.containsKey(name)) {
            throw new IllegalArgumentException("MBean with name '$name' already defined!")
        }

        this.model = model
        this.name = name
        this.objectName = objectName

        model.remoteBeans[name] = this
    }

    /**
     *  Generates a textual description of this object.
     *
     *  @return String representation of this object.
     */
    def toString() {
        "MBean $name = '$objectName'"
    }

    /**
     *  Factory to create the correct kind of bean reference, depending
     *  on whether the object name is a literla reference or a pattern
     *  containing '*'.
     *
     *  @param model Model containing the bean reference.
     *  @param name Name of the new bean.
     *  @param objectName Object name in the remote JVM.
     *  @return New bean reference.
     *  @throws IllegalArgumentException For duplicate names.
     */
    static JmxRemoteBean create(model, name, objectName) {
        def jmxObjectName
        try {
            jmxObjectName = objectName as ObjectName
        } catch (GroovyCastException) {
            jmxObjectName = new ObjectName(objectName)
        }

        if (jmxObjectName.isPropertyPattern()) {
            return new JmxRemoteBeanGroup(model, name, jmxObjectName)
        } else {
            return new JmxSimpleRemoteBean(model, name, jmxObjectName)
        }
    }
}


/**
 *  Simple (scalar) MBean.
 */
class JmxSimpleRemoteBean extends JmxRemoteBean {
    /**
     *  Create a new simple remote bean reference.
     *
     *  @param model Model containing the bean reference.
     *  @param name Name of the new bean.
     *  @param objectName Object name in the remote JVM.
     *  @throws IllegalArgumentException For duplicate names.
     */
    protected JmxSimpleRemoteBean(model, name, objectName) {
        super(model, name, objectName)
    }

    /**
     *  Direct lookup of the specified objectname in the remote JVM.
     *
     *  @param agent Remote JVM.
     *  @return List containing the remote bean.
     */
    def lookupBeans(agent) {
        [agent.getBean(objectName)]
    }
}


/**
 *  A group of releated MBeans.
 */
class JmxRemoteBeanGroup extends JmxRemoteBean {
    // the primary keys identifying each group bean
    def filters = [:]


    /**
     *  Create a new reference to a group of remote beans matching the
     *  patterns in the objectname.
     *
     *  @param model Model containing the bean reference.
     *  @param name Name of the new bean.
     *  @param objectName Object name query for the remote JVM.
     *  @throws IllegalArgumentException For duplicate names.
     */
    protected JmxRemoteBeanGroup(model, name, objectName) {
        super(model, name, objectName)

        // the following might seem overly complex, but real-life experience
        // shows that queries for 'key=*' or 'key=prefix*' don't work, just ones
        // with a trailing ',*'

        // Should work in theory, leads to failed queries in real life
        //def quote = ObjectName.&quote
        def quote = { it }

        // divide keys into literal ones and patterns
        def literals = []
        objectName.keyPropertyList.each { key, val ->
            if (val.endsWith('*')) {
                filters[key] = (val =='*') ? '' : val[0..-2]
            } else {
                literals << "${quote(key)}=${quote(val)}"
            }
        }

        // put literal keys into query for remote filtering
        this.objectName = new ObjectName("${quote(objectName.domain)}:${literals.join(',')},*")
    }

    /**
     *  Generates a textual description of this object.
     *
     *  @return String representation of this object.
     */
    def toString() {
        "${super.toString()} filters=${filters}"
    }

    /**
     *  Checks whether the given objectname passes the filter criteria.
     *
     *  @param objectName JMX objectname to be checked.
     *  @return True if all criteria match.
     */
    private Boolean passesFilter(objectName) {
        // filter is passed if no mismatch is found
        null == filters.find {
            !objectName.getKeyProperty(it.key).startsWith(it.value)
        }
    }

    /**
     *  Lookup list of concrete beans matching the query in the remote JVM.
     *
     *  @param agent Remote JVM.
     *  @return List containing the remote beans.
     */
    def lookupBeans(agent) {
        def result = []
        //println "Query $objectName"
        agent.queryBeans(objectName) { name, bean ->
            //println name
            if (passesFilter(name)) {
                result << bean
            }
        }
        result
    }
}


/**
 *  Complete JMX data model.
 */
class JmxModel {
    // map of clusters in this model
    final clusters = [:]

    // map of mbeans in this model
    final remoteBeans = [:]

    // root cluster
    final rootCluster = new JmxCluster(this, null, '')

    // definition of target beans
    def targetBeans = [:]


    /**
     *  Generates a textual description of this object.
     *
     *  @return String representation of this object.
     */
    def toString() {
        def result = [rootCluster.toString()]

        remoteBeans.each { key, bean ->
            result << bean.toString()
        }

        result << targetBeans.inspect()

        return result.join('\n')
    }
}

