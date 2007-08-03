/*  jagger - Webapp panel

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
 *  Panel displaying web applications and their servlets.
 */
class WebappPanel extends PanelBase {
    static final name = 'Webapps'
    static final description = 'Web applications'

    void generate(content) {
        if (!haveTomcat(content)) return

        // remote "now" in seconds
        def now = controller.jvm.startTime / 1000.0 + controller.jvm.uptime

        // iterate over all web contexts, showing a section per context
        controller.tomcat.contexts.each { name, bean ->
            // application state (1 = running)
            def state = bean.webapp.state

            content << ''
            content << h1(name)
            if (state) {
                content << "${label('Started')} ${Fmt.daysTime(now - bean.webapp.startTime / 1000.0)} ago in ${Fmt.humanTime(bean.webapp.startupTime)}  total time ${Fmt.daysTime(bean.webapp.processingTime / 1000.0)}"
            } else {
                content << "${label('State')} ${alert('not running')}"
            }

            // session information
            content << "${label('Sessions')} active ${Fmt.humanCount(bean.session.activeSessions)} / peak ${Fmt.humanCount(bean.session.maxActive)} / rejected ${Fmt.humanCount(bean.session.rejectedSessions)} / created ${Fmt.humanCount(bean.session.sessionCounter)}"
            content << "${label('Lifetimes')} avg. ${Fmt.humanTime(bean.session.sessionAverageAliveTime * 1000)} / max. ${Fmt.humanTime(bean.session.sessionMaxAliveTime * 1000)}"

            // add a line for idle servlets, and collect them in a list
            def unused_idx = content.size()
            def unused = []
            content << label('Idle servlets') + ' '

            // iterate over all servlets in this context
            bean.webapp.servlets.each { servletobject ->
                def servlet = controller.agent.getBean(servletobject)
                def servletname = servlet.name().getKeyProperty('name')
                def reqs = servlet.requestCount

                // idle means no requests yet
                if (reqs == 0) {
                    unused << servletname
                } else {
                    BigDecimal errs = servlet.errorCount
                    BigDecimal err_pc = errs * 100.0 / reqs
                    BigDecimal avg_time = servlet.processingTime / reqs

                    def error_info = "${Fmt.humanCount(errs)} (${Fmt.percent(errs, reqs)})"
                    if (err_pc > (controller.config.props.'threshold.request.errors' as BigDecimal))
                        error_info = alert(error_info)

                    // add servlet info
                    content << "${h2(servletname)} (loaded in ${Fmt.humanTime(servlet.loadTime).trim()})"
                    content << "${label('Requests')} ${Fmt.humanCount(reqs)} / errors $error_info"
                    content << "${label('Processing time')} avg. ${Fmt.humanTime(avg_time)} / max. ${Fmt.humanTime(servlet.maxTime)} / total ${Fmt.daysTime(servlet.processingTime / 1000.0)}"
                }
            }

            // append list of idle servlets to previously created line
            content[unused_idx] += unused.join(', ')
        }
    }
}

