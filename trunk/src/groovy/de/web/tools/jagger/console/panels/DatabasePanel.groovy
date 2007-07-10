/*  jagger - Database panel

    Copyright (c) 2007 by 1&1 Internet AG

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as
    published by the Free Software Foundation. A copy of this license is
    included with this software in the file "LICENSE.txt".

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    $Id: DatabasePanel.groovy 122671 2007-07-06 08:27:43Z jhe $
*/

package de.web.tools.jagger.console.panels;

import de.web.tools.jagger.util.Fmt;


class DatabasePanel extends PanelBase {
    static final name = 'DB Pools'
    static final description = 'Database pools'

    void generate(content) {
        content << ''
        content << h1('Database pools')

        controller.tomcat.datasources.each { name, bean ->
            def active = bean.numActive
            def idle = bean.numIdle
            def size = bean.maxActive
            content << "${' ' * controller.INDENT}${label(name)}"
            content << String.format('%s active %3d (%6s) / idle  %3d (%6s) / max. %3d',
                label(''), active, Fmt.percent(active, size), idle, Fmt.percent(idle, size), size)
        }
    }
}

