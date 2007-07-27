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

import de.web.tools.jagger.util.Fmt;


/**
 *  Panel displaying Tomcat connectors.
 */
class ConnectorPanel extends PanelBase {
    static final name = 'Connectors'
    static final description = 'Tomcat connectors'
            
    void generate(content) {
        // usually, there's two (HTTP and AJP)
        controller.tomcat.connectors.each { name, bean ->
            BigDecimal reqs = bean.requests.requestCount
            BigDecimal errs = bean.requests.errorCount
            BigDecimal err_pc = 0
            BigDecimal avg_time = 0
            if (reqs > 0) {
                err_pc = errs * 100.0 / reqs
                avg_time = bean.requests.processingTime / reqs
            }

            def error_info = "${Fmt.humanCount(errs)} (${Fmt.percent(errs, reqs)})"
            if (err_pc > (controller.config.props.'threshold.request.errors' as BigDecimal))
                error_info = alert(error_info)

            BigDecimal tp_busy_pc = bean.threads.currentThreadsBusy * 100.0 / bean.threads.maxThreads
            BigDecimal tp_slot_pc = bean.threads.currentThreadCount * 100.0 / bean.threads.maxThreads

            content << ''
            content << h1("Connector '$name'") + " @ ${controller.currentHost}"
            content << "${label('Requests')} ${Fmt.humanCount(reqs)} / errors $error_info"
            content << "${label('Processing time')} avg. ${Fmt.humanTime(avg_time)} / max. ${Fmt.humanTime(bean.requests.maxTime)} / total ${Fmt.daysTime(bean.requests.processingTime / 1000.0)}"
            content << "${label('Thread pool')} busy ${Fmt.humanCount(bean.threads.currentThreadsBusy)} (${Fmt.percent(tp_busy_pc)}) / size ${Fmt.humanCount(bean.threads.currentThreadCount)} (${Fmt.percent(tp_slot_pc)}) / max. ${Fmt.humanCount(bean.threads.maxThreads)}"
            content << "${label('Traffic')} in ${Fmt.humanSize(bean.requests.bytesReceived)} / out ${Fmt.humanSize(bean.requests.bytesSent)}"
            //content << "${label('Proxy')} ${proxyName}:${proxyPort} [${redirectPort}]"
        }
    }
}

