/*  jagger - Remote JMX JVM Access

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

package de.web.tools.jagger.jmx;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 *  Facade to remote JVM.
 *  This hides some of the more gory details of JMX data structures and
 *  caches data where possible.
 */
class JvmFacade {
    // on-demand cache for certain immutable MBeans, see cachable() helper
    private cache = [:]

    // remote beans
    private runtimeBean = null
    private threadingBean = null
    private memoryBean = null
    private classLoaderBean = null
    private osBean = null

    // bean pools sorted by name
    private poolBeans = null
    private gcBeans = null

    // used to split remote classpaths
    private remotePathsep = null

    // reference to JMX agent proxy
    def agent = null


    /**
     *  Changes the JMX agent to a new one, effectively changing the
     *  JMX connection. This reloads several cached entities, that only
     *  change when the underlying JVM is a different one.
     *
     *  @param agent The new JMX agent.
     */
    void setAgent(agent) {
        this.agent = agent

        // reset cache
        cache = [:]

        // get beans for this agent
        runtimeBean = agent.getBean('java.lang:type=Runtime')
        memoryBean = agent.getBean('java.lang:type=Memory')
        threadingBean = agent.getBean('java.lang:type=Threading')
        classLoaderBean = agent.getBean('java.lang:type=ClassLoading')
        osBean = agent.getBean('java.lang:type=OperatingSystem')

        // query memory pools
        poolBeans = new TreeMap()
        agent.queryBeans('java.lang:type=MemoryPool,*') { name, bean ->
            poolBeans[bean.Name] = bean
        }

        // query GC beans
        gcBeans = new TreeMap()
        agent.queryBeans('java.lang:type=GarbageCollector,*') { name, bean ->
            gcBeans[bean.Name] = bean
        }

        // get remote pathsep from remote system properties
        remotePathsep = runtimeBean.SystemProperties[['path.separator'] as Object[]].contents.value
    }

    /**
     *  Helper to remember cachable results. Checks the cache for an entry
     *  for "key" and if none is found, call the passed in closure and stores
     *  its return value in the cache for further calls.
     *
     *  @param key Key of the cache entry.
     *  @param generator Closure to generate the value if needed.
     *  @return The value for "key".
     */
    private cachable(key, generator) {
        if (!cache.containsKey(key)) {
            cache[key] = generator()
        }
        return cache[key]
    }

    /**
     *  Returns the JVM uptime.
     *
     *  @return Seconds, with milliseconds in the fractional part.
     */
    def getUptime() { runtimeBean.Uptime / 1000.0 }

    /**
     *  Returns the JVM startup time.
     *
     *  @return UNIX timestamp, but in milliseconds.
     */
    def getStartTime() { runtimeBean.StartTime }

    /**
     *  Returns the peak number of threads.
     *
     *  @return Thread count.
     */
    def getPeakThreadCount() { threadingBean.PeakThreadCount }
 
    /**
     *  Returns the current number of threads.
     *
     *  @return Thread count.
     */
    def getThreadCount() { threadingBean.ThreadCount }

    /**
     *  Returns the CPU time used by this JVM in its lifetime.
     *
     *  @return Time in seconds, with nanoseconds in the fractional part.
     */
    def getCPUTime() { osBean.ProcessCpuTime / 1000000000.0 }

    /**
     *  Returns the open file handles.
     *
     *  @return Handle count.
     */
    def getOpenHandles() { osBean.OpenFileDescriptorCount }

    /**
     *  Returns the maximum open file handles.
     *
     *  @return Handle count.
     */
    def getMaxHandles() { osBean.MaxFileDescriptorCount }

    /**
     *  Returns the classloader counts.
     *
     *  @return Map of class counts: loaded, unloaded, total.
     */
    def getClassLoader() {
        [
            loaded: classLoaderBean.LoadedClassCount,
            unloaded: classLoaderBean.UnloadedClassCount,
            total: classLoaderBean.TotalLoadedClassCount,
        ]
    }

    /**
     *  Returns the heap memory info.
     *
     *  @return JMX data (Used, Committed, Max, Initial).
     */
    def getHeap() {
        memoryBean.HeapMemoryUsage.contents
    }

    /**
     *  Returns the native (non-heap) memory info.
     *
     *  @return JMX data (Used, Committed, Max, Initial).
     */
    def getNonHeap() {
        memoryBean.NonHeapMemoryUsage.contents
    }

    /**
     *  Returns the number of objects in finalization queue.
     *
     *  @return Object count.
     */
    def getZombieCount() {
        memoryBean.ObjectPendingFinalizationCount
    }

    /**
     *  Returns the memory pools.
     *
     *  @return Memory pool beans sorted by name.
     */
    def getPools() { poolBeans }

    /**
     *  Returns the garbage collectors.
     *
     *  @return Garbage collector beans sorted by name.
     */
    def getGC() { gcBeans }

    /**
     *  Returns the ID indentifying the remote JVM.
     *
     *  @return JVM ID (PID@HOST).
     */
    def getID() { runtimeBean.Name }

    /**
     *  Returns the remote JVM boot classpath.
     *
     *  @return Path list.
     */
    List getBootClassPath() {
        cachable('BootClassPath') {
            runtimeBean.BootClassPath.split(remotePathsep)
        }
    }

    /**
     *  Returns the remote JVM classpath.
     *
     *  @return Path list.
     */
    List getClassPath() {
        cachable('ClassPath') {
            runtimeBean.ClassPath.split(remotePathsep)
        }
    }

    /**
     *  Returns the remote JVM library path.
     *
     *  @return Path list.
     */
    List getLibraryPath() {
        cachable('LibraryPath') {
            runtimeBean.LibraryPath.split(remotePathsep)
        }
    }

    /**
     *  Returns the remote JVM command line arguments.
     *
     *  @return Argument list.
     */
    List getInputArguments() {
        cachable('InputArguments') {
            runtimeBean.InputArguments
        }
    }

    /**
     *  Returns the remote JVM system properties.
     *
     *  @return Property map sorted by name.
     */
    def getSystemProperties() {
        cachable('SystemProperties') {
            def sysProps = new TreeMap()
            runtimeBean.SystemProperties.each { key, composite ->
                sysProps[composite.contents.key] = composite.contents.value
            }
            return sysProps
        }
    }

    /**
     *  Returns the remote system runtime versions.
     *
     *  @return Version map: os, jvm.
     */
    def getVersions() {
        cachable('Versions') {
            [
                os: "${osBean.Name} ${osBean.Version} (${osBean.Arch})",
                jvm: "${runtimeBean.VmVersion} (${runtimeBean.VmVendor} ${runtimeBean.VmName})",
            ]
        }
    }

    /**
     *  Returns the remote component version info (using WEB.DE standards).
     *  <p>
     *  Each component is described by a map with at least the three fields
     *  "name", "type" and "version".
     *  See "src/conf/applicationContext.xml" for an example on how to add
     *  such Mbeans and further details on their structure.
     *
     *  @return List of component version information.
     */
    def getComponents() {
        def result = []
        agent.queryBeans('de.web.management:type=VersionInfo,*') { name, bean ->
            result << bean.Value
        }
        return result
    }
}

