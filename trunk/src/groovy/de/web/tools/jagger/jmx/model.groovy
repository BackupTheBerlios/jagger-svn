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
     *  @param hostname Hostname, or JMX URL.
     *  @param port Port of JMX RMI registry, if a hostname is given.
     *  @throws AssertionError If port number not in range 1..65535
     */
    public JmxInstance (cluster, hostname, port) {
        this.cluster = cluster

        if (hostname.startsWith('service:jmx:')) {
            this.url = hostname
        } else {
            assert (1..65535).contains(port as Integer), "Illegal port number '${port}'" 
            this.url = "${hostname}:${port}"
        }

        cluster.children << this
    }

    /**
     *  Returns a list of instances in this object.
     *
     *  @return List containing this one instance.
     */
    def getInstances() {
        [this]
    }

    /**
     *  Generates a textual description of this object.
     *
     *  @return String representation of this object.
     */
    def String toString() {
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
    def String toString() {
        // append all children's representation, indenting them by one level
        def members = children
            .inject([]) { r, c -> r + c.toString().tokenize('\n') }
            .join(INDENT)

        return "Cluster '$name'$INDENT$members"
    }
}


/**
 *  Named bean on remote JVM.
 */
class JmxRemoteBean {
    // logical name
    String name

    // JMX object name
    ObjectName objectName

    // the model we're part of
    def model

    // scaled or computed values
    def aliases = [:]


    /**
     *  Creates a new remote bean reference.
     *
     *  @param model Model containing the bean reference.
     *  @param name Name of the new bean.
     *  @param objectName Object name in the remote JVM.
     *  @throws IllegalArgumentException For duplicate names.
     */
    public JmxRemoteBean(model, name, objectName) {
        if (model.remoteBeans.containsKey(name)) {
            throw new IllegalArgumentException("Remote bean with name '$name' already defined!")
        }

        def jmxObjectName
        try {
            jmxObjectName = objectName as ObjectName
        } catch (GroovyCastException) {
            jmxObjectName = new ObjectName(objectName)
        }

        this.model = model
        this.name = name
        this.objectName = jmxObjectName

        model.remoteBeans[name] = this
    }

    /**
     *  Generates a textual description of this object.
     *
     *  @return String representation of this object.
     */
    def String toString() {
        "Remote bean $name: mbean='$objectName'; aliases=${aliases.keySet().join(', ')}"
    }
}


/**
 *  Aggregated bean in the local JVM.
 */
class JmxTargetBean {
    // logical name
    String name

    // the model we're part of
    def model

    // description of the bean
    String description = ''

    // description of the bean
    def attributes = [:]


    /**
     *  Creates a new target bean.
     *
     *  @param model Model containing the bean reference.
     *  @param name Name of the new bean.
     *  @throws IllegalArgumentException For duplicate names.
     */
    protected JmxTargetBean(model, name) {
        if (model.targetBeans.containsKey(name)) {
            throw new IllegalArgumentException("Target bean with name '$name' already defined!")
        }

        this.model = model
        this.name = name

        model.targetBeans[name] = this
    }

    /**
     *  Ensures the description is never empty.
     *
     *  @return Description set from outside, or a default text.
     */
    public getDescription() {
        description ? description : "Managed object '$name'"
    }

    /**
     *  Generates a textual description of this object.
     *
     *  @return String representation of this object.
     */
    def String toString() {
        def result = ["Target bean $name '$description'"]
        attributes.each { key, attr ->
            result << "    Attribute ${attr.name} '${attr.description}'"
        }
        result.join('\n')
    }
}


/**
 *  Complete JMX data model.
 */
class JmxModel {
    // map of clusters in this model
    final clusters = [:]

    // map of remote beans in this model
    final remoteBeans = [:]

    // root cluster
    final rootCluster = new JmxCluster(this, null, '')

    // definition of target beans
    final targetBeans = [:]


    /**
     *  Generates a textual description of this object.
     *
     *  @return String representation of this object.
     */
    def String toString() {
        def result = [rootCluster.toString()]

        remoteBeans.each { key, bean ->
            result << bean.toString()
        }

        targetBeans.each { key, bean ->
            result << bean.toString()
        }

        return result.join('\n')
    }
}

