/*  jagger - Terminal controller

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

package de.web.tools.jagger.console;

import javax.management.JMException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.web.tools.jagger.util.Fmt;
import de.web.tools.jagger.util.License;
import de.web.tools.jagger.jmx.*;
import de.web.tools.jagger.console.TerminalView;
import de.web.tools.jagger.console.panels.HelpPanel;
import de.web.tools.jagger.console.panels.AboutPanel;


/**
 *  Mediator for the interaction between user (keyboard), JMX facades,
 *  data views and the screen (terminal display).
 *  <p>
 *  The controller's mainloop runs in its own daemon thread, which
 *  usually sleeps and is only woken up if state changes 
 *  induced by method calls of its interface make a display
 *  update necessary.
 * 
 */
class TerminalController extends Thread {
    private static Log log = LogFactory.getLog(TerminalController.class)

    static final Integer INDENT = 4
    static final FROZEN_TAG = ' [FROZEN]'

    Boolean DEBUG = false
    Integer COLS
    Integer ROWS

    // panels are loaded from userContext.xml
    def panels = [:]

    private view
    private agent
    private jvm
    private tomcat
    private hosts = []
    private currentHostIdx = 0
    private panel
    private panelCache = [:]
    private List errorMessage = []
    private Boolean frozen = false

    def config
    def mainThread
    volatile Boolean killed = false


    def getCurrentHost() {
        def result = hosts[currentHostIdx]
        if (!result.contains(':')) {
            result = "${result}:${config.props.p}"
        }
        return result
    }


    void setPanel(panel) {
        synchronized (this.panel) {
            this.panel = panel
        }
    }


    private synchronized makePanel(currentPanelClass) {
        assert agent != null || errorMessage.size() > 0

        def content = []

        if (agent == null) {
            content << ''
            errorMessage.each {
                content << view.Ansi.ALERT + it.trim() + view.Ansi.NORMAL
            }

            view.status = "Can't connect to ${currentHost} (SPACE to retry)"
            view.panel = "Error"
        } else {
            if (!panelCache.containsKey(currentPanelClass)) {
                panelCache[currentPanelClass] = currentPanelClass.newInstance(controller: this)
            }
            def currentPanel = panelCache[currentPanelClass]

            Boolean jmx_error = true
            try {
                currentPanel.generate(content)
                jmx_error = false
            } catch (IOException ex) {
                // jmx_error
                log.error("JMX I/O error while talking to ${currentHost}", ex)
                if (DEBUG) { killed = true; throw ex }
            } catch (JMException ex) {
                // jmx_error
                log.error("JMX error while talking to ${currentHost}", ex)
                if (DEBUG) { killed = true; throw ex }
            } catch (ConnectException ex) {
                // jmx_error
                log.error("JMX connection error to ${currentHost}", ex)
                if (DEBUG) { killed = true; throw ex }
            } catch (ThreadDeath td) {
                throw td
            } catch (Throwable ex) { // catch, log and throw
                log.fatal("Unexpected exception, shutting down", ex)
                killed = true
                throw ex
            }

            if (jmx_error) {
                // try to reconnect, if that fails we'll get an error msg
                selectHost(currentHostIdx)
                return null
            }

            view.panel = currentPanel.name
            view.status = "Connected to ${currentHost} | up ${Fmt.daysTime(jvm.uptime)}"
        }

        return content
    }


    private mainloop() {
        def oldPanelClass
        def content = []

        while (!killed) {
            def currentPanelClass
            synchronized (panel) {
                currentPanelClass = panels[panel]
                if (currentPanelClass == null) {
                    // can happen when unknown start panel is configured
                    panel = 'h'
                    currentPanelClass = panels[panel]
                }
            }

            if (currentPanelClass != oldPanelClass) {
                view.clear()
                oldPanelClass = currentPanelClass
            }

            if (!frozen) {
                content = makePanel(currentPanelClass)
                if (content == null) continue
            } else if (!view.panel.endsWith(FROZEN_TAG)) {
                view.panel += FROZEN_TAG
            }
            view.update(content)

            try {
                // Sleep till next refresh
                Thread.sleep(frozen ? Long.MAX_VALUE : 1000)
            } catch (InterruptedException e) {
                // Ignore, we were just asked to refresh immediately
            }
        }
    }


    synchronized void selectHost(hostidx) {
        agent = null
        jvm = null
        tomcat = null
        frozen = false
        errorMessage = []
        currentHostIdx = hostidx

        def serviceUrl = currentHost
        if (!currentHost.startsWith('service:jmx:')) {
            serviceUrl = "service:jmx:rmi:///jndi/rmi://${currentHost}/jmxrmi"
        }
        def new_agent = new JMXAgentFacade(url: serviceUrl, username: config.props.u, password: config.props.w)
        def errorString = null

        try {
            new_agent.openConnection()
        } catch (SecurityException ex) {
            log.error("You're not authorized for ${serviceUrl}", ex)
            if (DEBUG) { killed = true; throw ex }
            errorString = ex as String
        } catch (ThreadDeath td) {
            throw td
        } catch (Throwable ex) { // catch, log and show
            log.error("Can't connect to ${serviceUrl}", ex)
            if (DEBUG) { killed = true; throw ex }
            errorString = ex as String
        }

        if (errorString != null) {
            new_agent = null
            errorMessage = errorString.split(': ')
            view.clear()
        } else {
            jvm = new JvmFacade(agent: new_agent)
            tomcat = new TomcatFacade(agent: new_agent)

            // activate the new agent
            agent = new_agent
        }
    }


    void refresh() {
        this.interrupt()
    }


    void keyPressed(String key) {
        // safe-guard for initialization phase
        if (view == null) return

        Boolean dirty = true
        def lkey = key.toLowerCase()

        if (panels.containsKey(lkey)) {
            if (frozen) {
                // no panel change while frozen
                dirty = false
            } else {
                panel = lkey
            }
        } else switch (key) {
            case 'PGUP': view.pageUp();   break
            case 'PGDN': view.pageDown(); break
            case 'UP':   view.lineUp();   break
            case 'DOWN': view.lineDown(); break

            case ' ':
                if (agent == null) {
                    // reconnect
                    selectHost(currentHostIdx)
                } else {
                    // freeze / unfreeze
                    frozen = !frozen
                }
                break

            case '1'..'9':
                if (frozen) {
                    // no host change while frozen
                    dirty = false
                } else {
                    def idx = (key as char) - ('1' as char)
                    if (idx < hosts.size()) {
                        selectHost(idx)
                    } else {
                        // slot not used
                        dirty = false
                    }
                }
                break

            default:
                dirty = false
                break
        }

        // beep if key did not lead to a state change
        if (!dirty) view.beep()

        // visualize update or beep
        refresh()
    }


    public void run() {
        hosts = config.props.ns

        // get panel configuration from spring and load panel classes
        config.context.getBean('panels').each { key, clazz ->
            panels[key] = this.getClass().forName(clazz, true, this.getClass().getClassLoader())
        }

        // enforce system panels
        panels['h'] = HelpPanel
        panels['!'] = AboutPanel

        Boolean error = true
        view = new TerminalView(title: "${License.APPNAME} ${License.APPVERSION}")
        this.COLS = view.COLS
        this.ROWS = view.ROWS
        panel = config.props.startPanel

        selectHost(0)

        try {
            view.init()
            mainloop()
            error = false
        } finally {
            if (error) {
                killed = true
            } else {
                view.close()
            }
        }
    }
}

