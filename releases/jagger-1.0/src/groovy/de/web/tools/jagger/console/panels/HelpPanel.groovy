/*  jagger - Help panel

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

package de.web.tools.jagger.console.panels;

import de.web.tools.jagger.util.Fmt;
import de.web.tools.jagger.util.License;


/**
 *  Display possible keystrokes including a list of configured panels.
 */
class HelpPanel extends PanelBase {
    static final name = 'Help'
    static final description = 'This help panel'

    void generate(content) {
        content << ''
        content << h1('Panel Selection')
        (controller.panels as TreeMap).each { key, val ->
            content << "      ${key} ${val.description}"
        }

        content << ''
        content << h1('Others')
        content << '    q,x Quit program'
        content << '  SPACE Freeze / unfreeze display'
        controller.hosts.eachWithIndex { hostname, idx ->
            if (idx < 9) {
                content << "      ${idx+1} Select host ${hostname.inspect()}"
            }
        }
    }
}

