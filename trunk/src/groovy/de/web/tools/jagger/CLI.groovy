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


/**
 *  Command line interface to the JMX text console.
 *  (and later, possibly to the demon via a mode switch).
 *
 *  This reads the command line options, merges them with
 *  the config files and starts the terminal controller with
 *  the result.
 */
class CLI {
    /* Timeout for thread joining on shutdown. */
    static final JOIN_TIMEOUT = 10000

    /* Name of the properties file with option defaults. */
    static final DEFAULT_PROPERTIES = 'jagger.properties'

    /* The loaded configuration. */
    def config


    /**
     *  CLI instances are only created by main().
     */
    private CLI() {}

    /**
     *  Read option defaults from properties file, if one is found.
     *
     *  @return The loaded properties.
     */
    private Properties readDefaults() {
        def defaults = new Properties()
        def defaults_file = new File(DEFAULT_PROPERTIES)
        if (defaults_file.canRead()) {
            defaults.load(defaults_file.newInputStream())
        }
        return defaults
    }    


    /**
     *  Add jagger's options to CLI builder.
     *
     *  @param cli CLI builder instance
     */
    private void addOptions(cli) {
        cli.n(longOpt: 'hostname', args: 9, argName: 'DOMAIN,...', 'Comma-separated list of hostnames.',
              valueSeparator: ',' as char)
        cli.p(longOpt: 'port',     args: 1, argName: 'NNNNN',   'JMX port.')
        cli.u(longOpt: 'username', args: 1, argName: 'USER',    'JMX username.')
        cli.w(longOpt: 'password', args: 1, argName: 'PWD',     'JMX password.')
    }    


    /**
     *  Load configuration (from cmd line and config file) into the
     *  "config" property.
     *
     *  XXX Should go to Config class!
     *
     *  @param cli CLI builder instance
     *  @param options parsed options
     *  @return error message, or null on success.
     */
    private String setConfig(cli, options) {
        // first, read default values
        def defaults = readDefaults()

        // collect options, use default if not given on cmd line
        config = [:]
        [
            // list of option names and property names to check
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
            // split by comma if possible, else by space
            if (config.ns.contains(','))
                config.ns = config.ns.tokenize(',')
            else
                config.ns = config.ns.tokenize()

            // if we split by comma, remove extraneous spacing
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

        if (config.p == null) {
            def have_ports = config.ns.inject(true) {b, i -> b && i.contains(':')}
            if (!have_ports) return 'No port number given'
        } else {
            def portno
            try { portno = new Integer(config.p) }
            catch (NumberFormatException exc) { return "Bad port number ${config.p} (${exc})" }

            if (!(portno in 1..65535)) return "Port number not in range 1..65535"
        }

        // decode base64 password, if given in that format
        if (config.w.startsWith('b64:')) {
            config.w = new String(config.w[4..-1].decodeBase64())
        }

        // all is fine and dandy
        return null
    }    


    /**
     *  Start everything up, coordinate the running threads and
     *  finally try to shut down cleanly.
     *
     *  @param cli CLI builder instance
     *  @param options parsed options
     *  @return exit code
     */
    private mainloop(cli, options) {
        // load merged config
        def configError = setConfig(cli, options)
        if (configError != null) {
            println("Configuration error: ${configError}")
            return 1
        }

        // create terminal controller and start it
        def terminal = new TerminalController(
            name: 'Jagger Terminal',
            daemon: true,
            mainThread: Thread.currentThread(),
            config: new Config(props: config))
        terminal.start()

        // loop while not asked to shut down
        while (!terminal.killed) {
            // Wait for user input
            def key = (System.in.read() as Character) as String
            //print "KEY $key ${key[0] as Integer}  "

            // handle some global keys here
            switch (key) {
                // exit
                case 'q': case 'Q':
                case 'x': case 'X':
                    // set shutdown flag and wake up controller
                    terminal.killed = true
                    terminal.interrupt()
                    break

                // map '?' to 'h'
                case '?':
                    terminal.keyPressed('h')
                    break

                // handle function and special keys
                case '\033':
                    // single escape or escape sequence?
                    if (System.in.available()) {
                        // read up to 3 more bytes
                        def buf = '   ' as byte[]
                        def buflen = System.in.read(buf, 0, 3)
                        def keyseq = new String(buf)

                        // map ANSI sequences to "virtual" keys
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
                    // hand anything else off to controller
                    terminal.keyPressed(key)
                    break
            }
        }

        // wait for controller to shut down; if timeout strikes, we're good
        // since controller is a daemon.
        terminal.join(JOIN_TIMEOUT)

        return 0
    }


    /**
     *  Process command line options and start mainloop.
     *
     *  @param args command line argument array
     *  @return exit code
     */
    private process(args) {
        // describe common CLI options
        def cli = new CliBuilder(
            usage: "${License.APPNAME} [options]",
            writer: new PrintWriter(System.out)
        )
        cli.h(longOpt: 'help', 'Show this help message.')
        cli.v(longOpt: 'version', 'Show version information.')
        cli.Y(longOpt: 'warranty', 'Show warranty information.')
        cli.Z(longOpt: 'license', 'Show license information.')

        // add tool specific options
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
        if (options.Y) { println "${License.BANNER}\n${License.WARRANTY}" ; return 1 }
        if (options.Z) { println "${License.BANNER}\n${License.LICENSE}" ; return 1 }

        // get things going
        return mainloop(cli, options)
    }

 
    /**
     *  The jagger main.
     *
     *  @param args command line argument array
     *  @return exit code
     */
    public static main(args) {
        // delegate to instance of this class
        return new CLI().process(args)
    }
}

