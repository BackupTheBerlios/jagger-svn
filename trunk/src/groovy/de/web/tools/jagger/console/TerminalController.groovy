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
import javax.management.AttributeNotFoundException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.web.tools.jagger.util.Fmt;
import de.web.tools.jagger.util.License;
import de.web.tools.jagger.jmx.JMXAgentFacade;
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
    // logger for this class
    private static Log log = LogFactory.getLog(TerminalController.class)

    // common indent for all panels
    static final Integer INDENT = 4

    // default wait time between display refreshes [msec]
    static final REFRESH_INTERVAL = 1000

    // visual indication of a frozen display
    static final FROZEN_TAG = ' [FROZEN]'

    // activate debugging features (like not catching exceptions into
    // displayed error messages)
    Boolean DEBUG = false

    // size of the terminal (for the panels)
    Integer COLS
    
    // size of the terminal (for the panels)
    Integer ROWS

    // terminal view
    private view
    
    // remote JMX agent
    private agent
    
    // JVM JMX facade
    def jvm = null
    
    // Tomcat JMX facade
    def tomcat = null
    
    // list of hostnames or JMX serivce URLs
    private hosts = []
    
    // the currently selected host (index into "hosts")
    private currentHostIdx = 0
    
    // currently selected panel (panel key, e.g. 'h')
    private panel
    
    // panels as defined in "userContext.xml", maps from key to class name
    def panels = [:]
    
    // cache for created panel instances, map from class name to instance
    private panelCache = [:]
    
    // error message stored from last JMX connection exception
    private List errorMessage = []
    
    // display frozen or not? freezing is useful for cut&paste, among other things.
    private Boolean frozen = false
    
    // loaded configuration
    def config
    
    // signalled to shut down?
    volatile Boolean killed = false


    /**
     *  Returns the selected target host including port number (possibly the
     *  default port).
     *
     *  @return Fully qualified connection target.
     */
    def getCurrentHost() { hosts[currentHostIdx] }

    /**
     *  Sets the current panel in a thread-safe way.
     *
     *  @param panel Panel to be displayed on next update (e.g. 'h').
     */
    void setPanel(panel) {
        synchronized (this.panel) {
            this.panel = panel
        }
    }

    /**
     *  Create the content to be displayed.
     *  This also sets the status and panel properties of the view.
     *
     *  @param currentPanelClass Class to be used as a content generator.
     *  @return List of content lines.
     */
    private synchronized List makePanel(currentPanelClass) {
        // either we're connected or we have a connection error message
        assert agent != null || errorMessage.size() > 0

        // create return value
        def content = []

        if (agent == null) {
            // show connection error
            content << ''
            errorMessage.each {
                content << view.Ansi.ALERT + it.trim() + view.Ansi.NORMAL
            }

            view.status = "Can't connect to ${currentHost} (SPACE to retry)"
            view.panel = "Error"
        } else {
            // get a panel instance
            if (!panelCache.containsKey(currentPanelClass)) {
                panelCache[currentPanelClass] = currentPanelClass.newInstance(controller: this)
            }
            def currentPanel = panelCache[currentPanelClass]

            // create panel contents and catch any occuring JMX errors
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

    /**
     *  Main processing loop of the controller thread. Unless currently
     *  refreshing the display, it waits REFRESH_INTERVAL msecs for the next
     *  update, or endlessly if the display is frozen. The wait time can be
     *  shortened by calling refresh(), resulting in an immediate update.
     */
    private void mainloop() {
        // variables surviving a refresh cycle
        def oldPanelClass
        def content = []

        // refresh endlessly, until a shutdown is requested
        while (!killed) {
            // determine the selected panel
            def currentPanelClass
            synchronized (panel) {
                currentPanelClass = panels[panel]
                if (currentPanelClass == null) {
                    // can happen when unknown start panel is configured
                    panel = 'h'
                    currentPanelClass = panels[panel]
                }
            }

            // recognize panel changes
            if (currentPanelClass != oldPanelClass) {
                // make sure no unwanted content remains
                view.clear()
                oldPanelClass = currentPanelClass
            }

            if (!frozen) {
                // create new content
                content = makePanel(currentPanelClass)
                if (content == null) continue
            } else if (!view.panel.endsWith(FROZEN_TAG)) {
                // add indication of frozen display
                view.panel += FROZEN_TAG
            }

            // display new content
            view.update(content)

            try {
                // sleep till next refresh
                Thread.sleep(frozen ? Long.MAX_VALUE : REFRESH_INTERVAL)
            } catch (InterruptedException e) {
                // ignore, we were just asked to refresh immediately
            }
        }
    }

    /**
     *  Selects a new connection target out of the configured hosts.
     *
     *  @param hostidx Index of new target host.
     */
    synchronized void selectHost(hostidx) {
        // reset state of old connection
        agent = null
        frozen = false
        errorMessage = []
        currentHostIdx = hostidx

        // create new connection factory
        def new_agent = new JMXAgentFacade(url: currentHost, username: config.props.u, password: config.props.w)

        def errorString = null
        try {
            // try to connect
            new_agent.openConnection()

            // switch facades to new agent
            jvm.agent = new_agent

            // XXX probably should make this happen lazily, when the panel needs it
            try {
                tomcat.agent = new_agent
            } catch (InstanceNotFoundException) {
                tomcat.agent = null
            }
        } catch (SecurityException ex) {
            log.error("'${config.props.u}' is not authorized for ${new_agent.url}", ex)
            if (DEBUG) { killed = true; throw ex }
            errorString = ex as String
        } catch (ThreadDeath td) {
            throw td
        } catch (Throwable ex) { // catch, log and show
            log.error("Can't connect to ${new_agent.url}", ex)
            if (DEBUG) { killed = true; throw ex }
            errorString = ex as String
        }

        if (errorString == null) {
            // activate the new agent
            agent = new_agent
        } else {
            new_agent = null
            errorMessage = errorString.split(': ')
            view.clear()
        }
    }

    /**
     *  Triggers an immediate refresh of the display.
     */
    void refresh() {
        this.interrupt()
    }

    /**
     *  Processes any user input.
     *
     *  @param key Pressed key.
     */
    void keyPressed(String key) {
        // safe-guard for initialization phase
        if (view == null) return

        // flag for display refresh, assume one is needed due to user input
        Boolean dirty = true

        def lkey = key.toLowerCase()
        if (panels.containsKey(lkey)) {
            // panel selection change
            if (frozen) {
                // no panel change while frozen
                dirty = false
            } else {
                // select new panel
                panel = lkey
            }
        } else switch (key) {
            // scrolling
            case 'PGUP': view.pageUp();   break
            case 'PGDN': view.pageDown(); break
            case 'UP':   view.lineUp();   break
            case 'DOWN': view.lineDown(); break

            // reconnect or toggle freeze state
            case ' ':
                if (agent == null) {
                    // reconnect
                    selectHost(currentHostIdx)
                } else {
                    // freeze / unfreeze
                    frozen = !frozen
                }
                break

            // select new target host
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

            // unknown keys
            default:
                dirty = false
                break
        }

        // beep if key did not lead to a state change
        if (!dirty) view.beep()

        // visualize update or beep
        refresh()
    }

    /**
     *  Main method of the controller thread.
     */
    void run() {
        // XXX this data structure is ridiculous... and works for now. ;)
        DEBUG = Boolean.valueOf(config.props.defaults.getProperty('jagger.debug', 'false'))
    
        // get panel configuration from spring and load panel classes
        config.context.getBean('panels').each { key, clazz ->
            panels[key] = this.getClass().forName(clazz, true, this.getClass().getClassLoader())
        }

        // enforce system panels
        panels['h'] = HelpPanel
        panels['!'] = AboutPanel

        // set up other properties
        hosts = config.props.ns
        view = new TerminalView(title: "${License.APPNAME} ${License.APPVERSION}")
        this.COLS = view.COLS
        this.ROWS = view.ROWS
        panel = config.props.startPanel

        // select 1st host on startup
        selectHost(0)

        Boolean error = true
        try {
            view.init()
            mainloop()
            error = false
        } finally {
            if (error) {
                // indicate forced shutdown
                killed = true
            } else {
                // clear display only if no errors occured (normal exit)
                view.close()
            }
        }
    }
}

