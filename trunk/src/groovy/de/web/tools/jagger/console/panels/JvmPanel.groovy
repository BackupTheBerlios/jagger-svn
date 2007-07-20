/*  jagger - JVM panel

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

import java.text.DateFormat;

import de.web.tools.jagger.util.Fmt;


class JvmPanel extends PanelBase {
    static final name = 'JVM'
    static final description = 'JVM statistics'

    void generate(content) {
        def dtf = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, Locale.UK)
        def cld = controller.jvm.classLoader
        def uld_pc = cld.unloaded * 100.0 / cld.loaded
        def load_pc = ((controller.jvm.CPUTime * 10000.0 / controller.jvm.uptime) as Double).round() / 100.0
        def handle_pc = controller.jvm.openHandles * 100.0 / controller.jvm.maxHandles

        content << ''
        content << h1('General')
        content << "${label('Start time')} ${dtf.format(controller.jvm.startTime)} (up ${Fmt.daysTime(controller.jvm.uptime)})"
        content << "${label('JVM ID')} ${controller.jvm.ID} (#${controller.tomcat.JVMId})"
        content << "${label('Classes')} loaded ${Fmt.humanCount(cld.loaded)} / unloaded ${Fmt.humanCount(cld.unloaded)} (${Fmt.percent(uld_pc)}) / total ${Fmt.humanCount(cld.total)}"
        content << "${label('Threads')} ${Fmt.humanCount(controller.jvm.threadCount)} / peak ${Fmt.humanCount(controller.jvm.peakThreadCount)}"
        content << "${label('CPU time')} ${Fmt.daysTime(controller.jvm.CPUTime)} (${String.format(Locale.US, "%.2f%%", load_pc)})"
        content << "${label('Handles')} ${Fmt.humanCount(controller.jvm.openHandles)} (${Fmt.percent(handle_pc)}) / max. ${Fmt.humanCount(controller.jvm.maxHandles)}"

        content << ''
        content << h1('Memory') + " (more on 'm'emory panel)"
        dumpMem('Heap', controller.jvm.heap).each { content << it }
        dumpMem('Non-Heap', controller.jvm.nonHeap).each { content << it }
        content << "${label('GC Queue')} ${Fmt.humanCount(controller.jvm.zombieCount)} unfinalized objects"
    }
}

