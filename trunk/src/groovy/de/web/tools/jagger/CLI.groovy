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

    $Id$
*/

package de.web.tools.jagger;

import de.web.tools.jagger.util.License;
import de.web.tools.jagger.util.Config;
import de.web.tools.jagger.console.TerminalController;


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


    /** Load configuration (from cmd line and config file) into the
        "config" property.

        Return error message, or null on success.
     */
    private String setConfig(cli, options) {
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

        // check for configuration errors
        if (config.ns == null || config.ns.empty) return 'Host list is empty'
        if (config.u == null) return 'No JMX user given'
        if (config.w == null) return 'No JMX password given'

        // decode base64 password, if given in that format
        if (config.w.startsWith('b64:')) {
            config.w = new String(config.w[4..-1].decodeBase64())
        }

        return null
    }    


    private mainloop(cli, options) {
        def configError = setConfig(cli, options)
        if (configError != null) {
            println("Configuration error: ${configError}")
            return 1
        }

        def terminal = new TerminalController(
            name: 'Jagger Terminal',
            daemon: true,
            mainThread: Thread.currentThread(),
            config: new Config(props: config))
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

