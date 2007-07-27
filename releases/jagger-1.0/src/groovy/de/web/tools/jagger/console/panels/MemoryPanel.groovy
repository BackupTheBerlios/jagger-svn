/*  jagger - Memory panel

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

package de.web.tools.jagger.console.panels;

import de.web.tools.jagger.util.Fmt;


/**
 *  Panel displaying detailed memory and garbage collection statistics.
 */
class MemoryPanel extends PanelBase {
    static final name = 'Memory'
    static final description = 'Detailed memory statistics'

    void generate(content) {
        content << ''
        content << h1('Overview')
        dumpMem('Heap', controller.jvm.heap).each { content << it }
        dumpMem('Non-Heap', controller.jvm.nonHeap).each { content << it }

        content << ''
        content << h1('Garbage Collection')
        content << "${label('GC Queue')} ${Fmt.humanCount(controller.jvm.zombieCount)} unfinalized objects"

        // collection time info is relative to startup, so this is our "now"
        def uptime = controller.jvm.uptime * 1000

        controller.jvm.GC.each { name, gc ->
            // calculate amount of memory freed by last GC
            def freed = 0
            gc.MemoryPoolNames.each { poolname ->
                if (gc.LastGcInfo != null) {
                    Object[] key = [poolname]
                    def before = gc.LastGcInfo.memoryUsageBeforeGc[key]
                    def after = gc.LastGcInfo.memoryUsageAfterGc[key]
                    freed += before.value.used - after.value.used
                }
            }

            if (gc.CollectionCount == 0) {
                content << "${label(name)} not collected yet"
            } else {
                content << "${label(name)} ${Fmt.humanCount(gc.CollectionCount).padLeft(14)} collections took  ${Fmt.humanTime(gc.CollectionTime)} (avg. ${Fmt.humanTime(gc.CollectionTime / gc.CollectionCount, true)})"
                if (gc.LastGcInfo == null) {
                    content << "${label('')} no info on last collection available"
                } else {
                    content << "${label('')} last    ${Fmt.humanTimeDiff(gc.LastGcInfo.endTime, uptime)} ago     took  ${Fmt.humanTimeDiff(gc.LastGcInfo.startTime, gc.LastGcInfo.endTime)} in ${Fmt.humanCount(gc.LastGcInfo.GcThreadCount)} thread(s)"
                    content << "${label('')}                           freed ${Fmt.humanSize(freed)}"
                }
            }
        }

        content << ''
        content << h1('Memory Pools')
        controller.jvm.pools.each { name, it ->
            content << "${label(name)} type ${it.Type}"
            dumpMem('', it.Usage.contents, it.PeakUsage.contents).each { content << it }
        }
    }
}

