/*  jagger - Panel base class

    Copyright (c) 2007 by 1&1 Internet AG

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as
    published by the Free Software Foundation. A copy of this license is
    included with this software in the file "LICENSE.txt".

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    $Id: PanelBase.groovy 122671 2007-07-06 08:27:43Z jhe $
*/

package de.web.tools.jagger.console.panels;

import de.web.tools.jagger.util.Fmt;


abstract class PanelBase {
    final static LABEL_LENGTH = 17

    // the controller showing this panel
    def controller

    abstract void generate(content)

    String h1(String headline) {
        "${controller.view.Ansi.BOLD}${headline}${controller.view.Ansi.NORMAL}"
    }

    String h2(String headline) {
        "${' ' * LABEL_LENGTH}${controller.view.Ansi.LIGHT_GREEN}${headline}${controller.view.Ansi.NORMAL}"
    }

    String label(String label) {
        "${controller.view.Ansi.LIGHT_GRAY}${label.padLeft(LABEL_LENGTH)}${controller.view.Ansi.NORMAL}"
    }

    String alert(String text) {
        "${controller.view.Ansi.ALERT}${text}${controller.view.Ansi.NORMAL}"
    }

    Collection dumpMem(name, usage, peak = null) {
        def memfmt = { mem, attr ->
            "${Fmt.humanSize(mem[attr])} ${Fmt.percent(mem[attr], mem.max, '%5.1f')}%"
        }
    
        def result = [
            "${label(name)} used      ${memfmt(usage, 'used')}",
            "${label('  ')} committed ${memfmt(usage, 'committed')}",
            "${label('  ')} initial   ${Fmt.humanSize(usage.init)}",
            "${label('  ')} maximal   ${Fmt.humanSize(usage.max)}",
        ]

        if (peak) {
            result[0] += "  (peak ${memfmt(peak, 'used')})"
            result[1] += "  (peak ${memfmt(peak, 'committed')})"
        }

        return result
    }

}

