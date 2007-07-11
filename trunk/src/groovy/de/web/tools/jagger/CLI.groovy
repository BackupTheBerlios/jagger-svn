/*  jagger - Command Line Interface

    Copyright (c) 2007 by 1&1 Internet AG

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as
    published by the Free Software Foundation. A copy of this license is
    included with this software in the file "LICENSE.txt".

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    $Id: CLI.groovy 122671 2007-07-06 08:27:43Z jhe $
*/

package de.web.tools.jagger;

import java.io.IOException;
import java.rmi.ConnectException;
import javax.management.JMException;

import org.springframework.context.support.ClassPathXmlApplicationContext;

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

    def context
    def config
    def mainThread
    volatile Boolean killed = false


    TerminalController() {
        context = new ClassPathXmlApplicationContext(
            ['applicationContext.xml', 'userContext.xml'] as String[]
        )
        //def bean = ctx.getBean("bean")
        //System.exit(1)
    }


    def getCurrentHost() {
        def result = hosts[currentHostIdx]
        if (!result.contains(':')) {
            result = "${result}:${config.p}"
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
        def new_agent = new JMXAgentFacade(url: serverUrl, username: config.u, password: config.w)

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
        hosts = config.ns

        Boolean error = true
        view = new TerminalView(title: "${License.APPNAME} ${License.APPVERSION}")
        this.COLS = view.COLS
        this.ROWS = view.ROWS
        panel = config.startPanel

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


class CLI {
    static final JOIN_TIMEOUT = 10000
    static final DEFAULT_PROPERTIES = 'jagger.properties'

    def config

    private readDefaults() {
        def defaults = new Properties()
        def defaults_file = new File(DEFAULT_PROPERTIES)
        if (defaults_file.canRead()) {
            defaults.load(defaults_file.newInputStream())
        }
        return defaults
    }    

    private addOptions(cli) {
        cli.n(longOpt: 'hostname', args: 9, argName: 'DOMAIN',  'Tomcat hostname.',
              valueSeparator: ',' as char)
        cli.p(longOpt: 'port',     args: 1, argName: 'NNN',     'Tomcat JMX port.')
        cli.u(longOpt: 'username', args: 1, argName: 'USER',    'JMX username.')
        cli.w(longOpt: 'password', args: 1, argName: 'PWD',     'JMX password.')
    }    

    private setConfig(cli, options) {
        def defaults = readDefaults()
        //defaults.store(cli.writer, DEFAULT_PROPERTIES)
        //println options.arguments()

        // set defaults, if no cmd line option given
        config = [:]
        [
            ns: 'jagger.host',
            p:  'jagger.port',
            u:  'jagger.monitor.user',
            w:  'jagger.monitor.password',
        ].each { key, propname ->
            if (options[key])
                config[key] = options[key]
            else
                config[key] = defaults[propname]
        }

        // convert hostlist string to host list
        if (String.isInstance(config.ns)) {
            if (config.ns.contains(','))
                config.ns = config.ns.tokenize(',')
            else
                config.ns = config.ns.tokenize()
            config.ns = config.ns.collect { it.trim() }
        }

        // decode base64 password, if given in that format
        if (config.w.startsWith('b64:')) {
            config.w = new String(config.w[4..-1].decodeBase64())
        }

        // set other values (not settable on cmd line)
        [
            startPanel: 'j',
            ('threshold.request.errors'): '1.0',
        ].each { key, defaultValue ->
            if (defaults.containsKey('jagger.'+key))
                config[key] = defaults['jagger.'+key]
            else
                config[key] = defaultValue
        }
    }    

    private mainloop(cli, options) {
        setConfig(cli, options)

        def terminal = new TerminalController(
            name: "Jagger Terminal",
            daemon: true,
            mainThread: Thread.currentThread(),
            config: config)
        terminal.start()

        while (!terminal.killed) {
            // Wait for user input
            def key = (System.in.read() as Character) as String
            //print "KEY $key ${key[0] as Integer}  "
            switch (key) {
                case 'q': case 'Q':
                case 'x': case 'X':
                    terminal.killed = true
                    terminal.interrupt()
                    break

                case '?':
                    terminal.keyPressed('h')
                    break

                case '\033':
                    if (System.in.available()) {
                        def buf = '   ' as byte[]
                        def buflen = System.in.read(buf, 0, 3)
                        def keyseq = new String(buf)

                        switch (keyseq) {
                            case '[A ': key = 'UP';     break
                            case '[B ': key = 'DOWN';   break
                            case '[C ': key = 'RIGHT';  break
                            case '[D ': key = 'LEFT';   break
                            case '[5~': key = 'PGUP';   break
                            case '[6~': key = 'PGDN';   break
                        }
                        //print "<${key}>  "
                    }
                    // fallthrough

                default:
                    terminal.keyPressed(key)
                    break
            }
        }
        terminal.join(JOIN_TIMEOUT)

        return 0
    }

    private process(args) {
        // describe CLI options
        def cli = new CliBuilder(
            usage: "${License.APPNAME} [options]",
            writer: new PrintWriter(System.out)
        )
        cli.h(longOpt: 'help', 'Show this help message.')
        cli.v(longOpt: 'version', 'Show version information.')

        // hmmm, no way to have long option only...
        cli.y(longOpt: 'warranty', 'Show warranty information.')
        cli.z(longOpt: 'license', 'Show license information.')

        addOptions(cli)

        // parse options and do standard processing
        def options = cli.parse(args)
        if (options == null) {
            println('Error in processing command line options.')
            return 1
        }
        if (options.h) {
            // why doesn't CliBuilder offer the "header" parameter?!
            cli.formatter.printHelp(cli.writer, cli.formatter.defaultWidth,
                cli.usage, License.COPYRIGHT + ' \u00A0\nOptions:', cli.options,
                cli.formatter.defaultLeftPad, cli.formatter.defaultDescPad, '')
            cli.writer.flush()
            return 1
        }
        if (options.v) { println "${License.APPNAME} ${License.APPVERSION}" ; return 1 }
        if (options.y) { println "${License.BANNER}\n${License.WARRANTY}" ; return 1 }
        if (options.z) { println "${License.BANNER}\n${License.LICENSE}" ; return 1 }

        return mainloop(cli, options)
    }
         
    public static main(args) {
        new CLI().process(args)
    }
}

