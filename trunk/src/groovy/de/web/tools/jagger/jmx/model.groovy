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


    public JmxInstance (cluster, hostname, port) {
        assert (1..65535).contains(port as Integer), "Illegal port number '${port}'" 

        this.cluster = cluster
        this.url = "${hostname}:${port}"

        cluster.children << this
    }

    def getInstances() {
        [this]
    }


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
    private final INDENT = '\n    '

    // the model we're part of
    def model

    // our parent cluster (or null for root)
    def parent

    // name of this cluster
    String name

    // what's in the cluster (instances and sub-clusters)
    def children = []

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


    def getInstances() {
        children.inject([]) { result, child -> result + child.getInstances() }
    }


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
class JmxMBean {
    // name
    String name

    // name
    ObjectName objectName

    // the model we're part of
    def model


    protected JmxMBean(model, name, objectName) {
        if (model.mbeans.containsKey(name)) {
            throw new IllegalArgumentException("MBean with name '$name' already defined!")
        }

        this.model = model
        this.name = name
        this.objectName = objectName

        model.mbeans[name] = this
    }

    def toString() {
        "MBean $name = '$objectName'"
    }

    static JmxMBean create(model, name, objectName) {
        def jmxObjectName
        try {
            jmxObjectName = objectName as ObjectName
        } catch (GroovyCastException) {
            jmxObjectName = new ObjectName(objectName)
        }

        if (jmxObjectName.isPropertyPattern()) {
            return new JmxMBeanGroup(model, name, jmxObjectName)
        } else {
            return new JmxSimpleMBean(model, name, jmxObjectName)
        }
    }
}


/**
 *  Simple (scalar) MBean.
 */
class JmxSimpleMBean extends JmxMBean {
    protected JmxSimpleMBean(model, name, objectName) {
        super(model, name, objectName)
    }


    def lookupBeans(agent) {
        [agent.getBean(objectName)]
    }
}


/**
 *  A group of releated MBeans.
 */
class JmxMBeanGroup extends JmxMBean {
    // the primary keys identifying each group bean
    def filters = [:]


    protected JmxMBeanGroup(model, name, objectName) {
        super(model, name, objectName)

        // this might seem overly complex, but real-life experience shows
        // that queries for 'key=*' or 'key=prefix*' don't work, just ones
        // with a trailing ',*'
        def literals = []
        //def quote = ObjectName.&quote
        def quote = { it }
        objectName.keyPropertyList.each { key, val ->
            if (val.endsWith('*')) {
                filters[key] = val[0..-2]
            } else {
                literals << "${quote(key)}=${quote(val)}"
            }
        }
        this.objectName = new ObjectName("${quote(objectName.domain)}:${literals.join(',')},*")
    }

    def toString() {
        "${super.toString()} filters=${filters}"
    }


    private Boolean passesFilter(objectName) {
        null == filters.find {
            !objectName.getKeyProperty(it.key).startsWith(it.value)
        }
    }


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
    final mbeans = [:]

    // root cluster
    final rootCluster = new JmxCluster(this, null, '')

    // definition of aggregation beans
    def beans

    def toString() {
        def result = [rootCluster.toString()]

        mbeans.each { key, bean ->
            result << bean.toString()
        }

        result << beans.inspect()

        return result.join('\n')
    }
}

