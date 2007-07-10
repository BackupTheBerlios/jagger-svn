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

    $Id: JvmFacade.groovy 122570 2007-07-06 07:58:43Z jhe $
*/

package de.web.tools.jagger.jmx;

class JvmFacade {
    private cache = [:]
    private runtimeBean = null
    private threadingBean = null
    private memoryBean = null
    private classLoaderBean = null
    private osBean = null
    private poolBeans = null
    private gcBeans = null
    private remote_pathsep = null

    def agent = null

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

        poolBeans = new TreeMap()
        agent.queryBeans('java.lang:type=MemoryPool,*') { name, bean ->
            poolBeans[bean.Name] = bean
            //println name.dump()
            //println bean.dump()
        }

        gcBeans = new TreeMap()
        agent.queryBeans('java.lang:type=GarbageCollector,*') { name, bean ->
            gcBeans[bean.Name] = bean
        }

        remote_pathsep = runtimeBean.SystemProperties[['path.separator'] as Object[]].contents.value
    }

    private cachable(key, generator) {
        if (!cache.containsKey(key)) {
            cache[key] = generator()
        }
        return cache[key]
    }

    // JVM uptime in seconds with milliseconds
    def getUptime() { runtimeBean.Uptime / 1000.0 }

    // JVM start time as UNIX timestamp in milliseconds
    def getStartTime() { runtimeBean.StartTime }

    // Peak number of threads
    def getPeakThreadCount() { threadingBean.PeakThreadCount }
 
    // Current number of threads
    def getThreadCount() { threadingBean.ThreadCount }

    // Used CPU time in seconds with nanoseconds
    def getCPUTime() { osBean.ProcessCpuTime / 1000000000.0 }

    // Open handles
    def getOpenHandles() { osBean.OpenFileDescriptorCount }

    // Max. handles
    def getMaxHandles() { osBean.MaxFileDescriptorCount }

    // Classloader counts
    def getClassLoader() {
        [
            loaded: classLoaderBean.LoadedClassCount,
            unloaded: classLoaderBean.UnloadedClassCount,
            total: classLoaderBean.TotalLoadedClassCount,
        ]
    }

    // Heap memory (Used, Committed, Max, Initial)
    def getHeap() {
        memoryBean.HeapMemoryUsage.contents
    }

    // Native memory (Used, Committed, Max, Initial)
    def getNonHeap() {
        memoryBean.NonHeapMemoryUsage.contents
    }

    // # of objects in finalization queue
    def getZombieCount() {
        memoryBean.ObjectPendingFinalizationCount
    }

    // Memory pools
    def getPools() { poolBeans }

    // Garbage collectors
    def getGC() { gcBeans }

    // JVM ID (PID@HOST)
    def getID() { runtimeBean.Name }

    // JVM paths
    List getBootClassPath() {
        cachable('BootClassPath') {
            runtimeBean.BootClassPath.split(remote_pathsep)
        }
    }
    List getClassPath() {
        cachable('ClassPath') {
            runtimeBean.ClassPath.split(remote_pathsep)
        }
    }
    List getLibraryPath() {
        cachable('LibraryPath') {
            runtimeBean.LibraryPath.split(remote_pathsep)
        }
    }

    // JVM command line args
    List getInputArguments() {
        cachable('InputArguments') {
            runtimeBean.InputArguments
        }
    }

    // JVM System Properties
    def getSystemProperties() {
        cachable('SystemProperties') {
            def sysProps = new TreeMap()
            runtimeBean.SystemProperties.each { key, composite ->
                sysProps[composite.contents.key] = composite.contents.value
            }
            return sysProps
        }
    }

    def getVersions() {
        cachable('Versions') {
            [
                os: "${osBean.Name} ${osBean.Version} (${osBean.Arch})",
                jvm: "${runtimeBean.VmVersion} (${runtimeBean.VmVendor} ${runtimeBean.VmName})",
            ]
        }
    }
}

