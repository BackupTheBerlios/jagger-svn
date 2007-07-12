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

import de.web.tools.jagger.util.Fmt;
import de.web.tools.jagger.util.License;
import de.web.tools.jagger.jmx.*;
import de.web.tools.jagger.console.TerminalView;
import de.web.tools.jagger.console.panels.*;


class TerminalController extends Thread {
    static final Integer INDENT = 4
    static final FROZEN_TAG = ' [FROZEN]'

    Boolean DEBUG = true
    Integer COLS
    Integer ROWS

    final panels = [
        ('!'): AboutPanel,
        c: ConnectorPanel,
        d: DatabasePanel,
        e: EnvironmentPanel,
        h: HelpPanel,
        j: JvmPanel,
        m: MemoryPanel,
        v: VersionPanel,
        w: WebappPanel,
    ]

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
                if (DEBUG) { killed = true; throw ex }
            } catch (JMException ex) {
                // jmx_error
                if (DEBUG) { killed = true; throw ex }
            } catch (ConnectException ex) {
                // jmx_error
                if (DEBUG) { killed = true; throw ex }
            } catch (Throwable t) {
                killed = true
                throw t
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
            }

            if (currentPanelClass != oldPanelClass) {
                view.clear()
                oldPanelClass = currentPanelClass
            }

            if (!frozen) {
                assert agent != null || errorMessage.size() > 0
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
                // Ignore, we're just asked to refresh immediately
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

        def serverUrl = "service:jmx:rmi:///jndi/rmi://${currentHost}/jmxrmi"
        def new_agent = new JMXAgentFacade(url: serverUrl, username: config.props.u, password: config.props.w)

        try {
            new_agent.openConnection()
        } catch (IOException ex) {
            if (DEBUG) { killed = true; throw ex }

            errorMessage = (ex as String).tokenize(':')
            new_agent = null
            view.clear()
        }

        if (new_agent) {
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

