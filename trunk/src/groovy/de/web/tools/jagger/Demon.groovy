/*  jagger - Demon Mode Command Line Interface

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

import de.web.tools.jagger.jmx.JmxConfigReader;
import de.web.tools.jagger.jmx.execution.ExecutionContext;


/**
 *  Command line interface to the JMX demon.
 */
class Demon extends CLISupport {
    private static Log log = LogFactory.getLog(Demon.class)


    /**
     *  Demon instances are only created by main().
     */
    private Demon() {}


    /**
     *  Add jagger's options to CLI builder.
     *
     *  @param cli CLI builder instance
     */
    protected void addOptions(cli) {
        //cli.u(longOpt: 'username', args: 1, argName: 'USER',    'JMX username.')
        //cli.w(longOpt: 'password', args: 1, argName: 'PWD',     'JMX password.')
    }    


    /**
     *  Start everything up, coordinate the running threads and
     *  finally try to shut down cleanly.
     *
     *  @param cli CLI builder instance
     *  @param options parsed options
     *  @return exit code
     */
    protected mainloop(cli, options) {
        // log proper startup
        log.info("Jagger demon startup initiated by ${System.getProperty('user.name')}")

        def args = options.arguments()
        def configFilename
        if (args) {
            configFilename = args[0]
        } else {
            println "FATAL: You must specify a config filename!"
            return 1
        }

        def cr = new JmxConfigReader()
        def model
        try {
            model = cr.loadModel(configFilename)
        } catch (ScriptException ex) {
            println "FATAL: ${ex.message}"
            return 1
        }
        println '~'*78
        println model.toString()
        println '~'*78

        new ExecutionContext(model: model).register()

        println 'Waiting forever...'
        Thread.sleep(Long.MAX_VALUE)

        // log proper shutdown
        log.info("Jagger demon shutdown initiated by ${System.getProperty('user.name')}")

        return 0
    }


    /**
     *  The jagger Demon main.
     *
     *  @param args command line argument array
     *  @return exit code
     */
    public static main(args) {
        // delegate to instance of this class
        return new Demon().process(args)
    }
}

