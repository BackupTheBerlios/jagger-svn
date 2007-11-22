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

import java.text.SimpleDateFormat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.management.ManagementFactory;

import de.web.tools.jagger.util.Fmt;
import de.web.tools.jagger.jmx.JmxConfigReader;
import de.web.tools.jagger.jmx.execution.ExecutionContext;


/**
 *  Command line interface to the JMX demon.
 */
class Demon extends CLISupport {
    private static Log log = LogFactory.getLog(Demon.class)

    private final DATESTAMPFMT = new SimpleDateFormat('yyyy-MM-dd-HHmmss')
    private final DATESTAMP = DATESTAMPFMT.format(new Date())
    private final CSV_DELIM = ';'


    /**
     *  Demon instances are only created by main().
     */
    private Demon() {}

    void dumpTargetBeansToCSV(model, destdir) {
        def mbs = ManagementFactory.getPlatformMBeanServer()
        def now = DATESTAMPFMT.format(new Date())

        model.targetBeans.values().each { bean ->
            def gmb = new GroovyMBean(mbs, "de.web.management:type=Aggregator,name=${bean.name}")
            def outfile = new File(destdir, "${DATESTAMP}-${bean.name}.csv")
            def attrs = gmb.info().attributes as List
            attrs.sort { a, b -> a.name.compareTo(b.name) }

            if (!outfile.exists()) {
                def headers1 = attrs.collect { it.name }
                def headers2 = attrs.collect { it.description }
                outfile.append("Time;${headers1.join(CSV_DELIM)}\n")
                //outfile.append("    ;${headers2.join(CSV_DELIM)}\n")
            }
            
            def values = attrs.collect { attr ->
                try {
                    return gmb."${attr.name}"
                } catch (Exception ex) {
                    return 'Unavailable'
                }
            }
            outfile.append("$now;${values.join(CSV_DELIM)}\n")

            println "$now ${Fmt.humanSize(outfile.size())} ${outfile.name}"
        }
    }

    void dumpTargetBeans(model) {
        def mbs = ManagementFactory.getPlatformMBeanServer()
        model.targetBeans.values().each { bean ->
            def gmb = new GroovyMBean(mbs, "de.web.management:type=Aggregator,name=${bean.name}")
            //println gmb.dump()
            println gmb.name()
            gmb.info().attributes.each { attr ->
                def val
                try {
                    val = gmb."${attr.name}"
                } catch (Exception ex) {
                    val = 'Unavailable'
                }
                    
                println "    ${bean.name}.${attr.name} = ${val} '${attr.description}'"
            }
        }
    }

    private void visualWait(sleepTime) {
        def now = System.currentTimeMillis()
        def endTime = now + sleepTime
        def interval = 1000L
        def count = 0

        while (now < endTime) {
            print "${'-\\|/'[count % 4]} ${((endTime - now) / 1000) as Integer} secs       \r"
            Thread.sleep([interval, endTime - now].min())
            now = System.currentTimeMillis()
            count++
        }
        print "${' ' * 78}\r"
    }
    
    /**
     *  Add jagger's options to CLI builder.
     *
     *  @param cli CLI builder instance
     */
    protected void addOptions(cli) {
        cli.p(longOpt: 'poll', args: 1, argName: 'DESTDIR', 'Destination directory for poll data.')
        cli.d(longOpt: 'poll-delay', args: 1, argName: 'SECS', 'Wait time between polls.')
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
        log.info { "Jagger demon startup initiated by ${System.getProperty('user.name')}" }

        def args = options.arguments()
        def configFilename
        if (args.size() == 1) {
            configFilename = args[0]
        } else {
            println "FATAL: You must specify exactly one config filename!"
            return 1
        }

        def cr = new JmxConfigReader()
        def model
        try {
            model = cr.loadModel(configFilename)
        } catch (java.io.FileNotFoundException ex) {
            println "FATAL: Can't read model from $configFilename (${ex.message})"
            return 1
        } catch (ScriptException ex) {
            println "FATAL: ${ex.message}"
            return 1
        }
        println '~'*78
        println model.toString()
        println '~'*78

        new ExecutionContext(model: model).register()
        dumpTargetBeans(model)
        println '~'*78

        if (options.p) {
            while (true) {
                def start = System.currentTimeMillis()
                dumpTargetBeansToCSV(model, options.p)
                def took = System.currentTimeMillis() - start
                println "Took ${took / 1000.0} secs"

                visualWait(1000L * options.d.toLong() - took)
            }
        } else {
            println 'Waiting forever...'
            Thread.sleep(Long.MAX_VALUE)
        }

        // log proper shutdown
        log.info { "Jagger demon shutdown initiated by ${System.getProperty('user.name')}" }

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

