/*  jagger - Command Line Interface Support

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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.commons.cli.GnuParser;

import de.web.tools.jagger.util.License;


/**
 *  Command line interface to the JMX text console.
 *  (and later, possibly to the demon via a mode switch).
 *
 *  This reads the command line options, merges them with
 *  the config files and starts the terminal controller with
 *  the result.
 */
abstract class CLISupport {
    private static Log log = LogFactory.getLog(CLISupport.class)


    /**
     *  CLISupport instances are only created by main().
     */
    protected CLISupport() {}


    /**
     *  Add specific options to CLI builder.
     *
     *  @param cli CLI builder instance
     */
    abstract protected void addOptions(cli);


    /**
     *  Main command loop.
     *
     *  @param cli CLI builder instance
     *  @param options parsed options
     *  @return exit code
     */
    abstract protected mainloop(cli, options);

    /**
     *  Check for --debug mode.
     *
     *  @return true for debugging mode.
     */
    public boolean isDebugging() {
        System.getProperty('project.debug') == 'true'
    }

    /**
     *  Check for --trace mode.
     *
     *  @return true for tracing mode.
     */
    public boolean isTracing() {
        System.getProperty('project.trace') == 'true'
    }

    /**
     *  Print text to console.
     *
     *  @param text Text to print.
     */
    public void console(text) {
        println text
    }

    /**
     *  Process command line options and start mainloop.
     *
     *  @param args command line argument array
     *  @return exit code
     */
    protected process(args) {
        // describe common CLI options
        def cli = new CliBuilder(
            usage: "${License.APPNAME} [options]",
            writer: new PrintWriter(System.out),
            parser: new GnuParser()
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
            console 'Error in processing command line options.'
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
        if (options.v) { console "${License.APPNAME} ${License.APPVERSION}" ; return 1 }
        if (options.Y) { console "${License.BANNER}\n${License.WARRANTY}" ; return 1 }
        if (options.Z) { console "${License.BANNER}\n${License.LICENSE}" ; return 1 }

        // get things going
        return mainloop(cli, options)
    }
}

