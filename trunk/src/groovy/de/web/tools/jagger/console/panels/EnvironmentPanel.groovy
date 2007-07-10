/*  jagger - Environment panel

    Copyright (c) 2007 by 1&1 Internet AG

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as
    published by the Free Software Foundation. A copy of this license is
    included with this software in the file "LICENSE.txt".

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    $Id: EnvironmentPanel.groovy 122671 2007-07-06 08:27:43Z jhe $
*/

package de.web.tools.jagger.console.panels;

import de.web.tools.jagger.util.Fmt;


class EnvironmentPanel extends PanelBase {
    static final name = 'Environment'
    static final description = 'Runtime environment'

    void generate(content) {
        def dumpPath = { label, path ->
            if (label != null) {
                content << "${' ' * controller.INDENT}${controller.view.Ansi.LIGHT_GRAY}${label}${controller.view.Ansi.NORMAL}"
            }
            path.each {
                content << "${' ' * controller.INDENT}${Fmt.shorten(it, controller.COLS - controller.INDENT)}"
            }
        }

        content << ''
        content << h1('Paths')
        dumpPath('Class path', controller.jvm.classPath)
        dumpPath('Boot class path', controller.jvm.bootClassPath)
        dumpPath('Library path', controller.jvm.libraryPath)

        content << ''
        content << h1('Command line arguments')
        dumpPath(null, controller.jvm.inputArguments)
 

        content << ''
        content << h1('System Properties')
        controller.jvm.systemProperties.each { key, val ->
            def safe = val.inspect()[1..-2]
            def line = "${key} = ${safe}"
            if (line.length() > controller.COLS) {
                content << "${Fmt.shorten(key, controller.COLS - 4)} = \\"
                content << "${' ' * controller.INDENT}${Fmt.shorten(safe, controller.COLS - controller.INDENT)}"
            } else {
                content << line
            }
        }
    }
}

