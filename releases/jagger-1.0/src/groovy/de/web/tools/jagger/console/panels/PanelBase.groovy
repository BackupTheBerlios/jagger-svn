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

    $Id$
*/

package de.web.tools.jagger.console.panels;

import de.web.tools.jagger.util.Fmt;


/**
 *  Base class for all panel implementations.
 */
abstract class PanelBase {
    // usual length of a label (padding length)
    final static LABEL_LENGTH = 17

    // the controller showing this panel
    def controller

    /**
     *  Generates the panel's content.
     *
     *  @param content List the formatted lines get appended to.
     */
    abstract void generate(content)

    /**
     *  Format a 1st level heading.
     *
     *  @param headline The heading text.
     *  @return Formatted heading.
     */
    String h1(String headline) {
        "${controller.view.Ansi.BOLD}${headline}${controller.view.Ansi.NORMAL}"
    }

    /**
     *  Format a 2nd level heading.
     *
     *  @param headline The heading text.
     *  @return Formatted heading.
     */
    String h2(String headline) {
        "${' ' * LABEL_LENGTH}${controller.view.Ansi.LIGHT_GREEN}${headline}${controller.view.Ansi.NORMAL}"
    }

    /**
     *  Format a label.
     *
     *  @param label The label's text.
     *  @return Formatted label.
     */
    String label(String label) {
        "${controller.view.Ansi.LIGHT_GRAY}${label.padLeft(LABEL_LENGTH)}${controller.view.Ansi.NORMAL}"
    }

    /**
     *  Formats a text so that it visually stands out.
     *
     *  @param text The important text.
     *  @return Formatted text.
     */
    String alert(String text) {
        "${controller.view.Ansi.ALERT}${text}${controller.view.Ansi.NORMAL}"
    }

    /**
     *  Dump composite JMX memory pool information.
     *
     *  @param name Pool name.
     *  @param usage Pool usage.
     *  @param peak Peak usage, if available.
     *  @return List of content lines.
     */
    Collection dumpMem(name, usage, peak = null) {
        // format a memory attribute, including percentage of the max. value
        def memfmt = { mem, attr ->
            "${Fmt.humanSize(mem[attr])} ${Fmt.percent(mem[attr], mem.max, '%5.1f')}%"
        }

        // generate pool info
        def result = [
            "${label(name)} used      ${memfmt(usage, 'used')}",
            "${label('  ')} committed ${memfmt(usage, 'committed')}",
            "${label('  ')} initial   ${Fmt.humanSize(usage.init)}",
            "${label('  ')} maximal   ${Fmt.humanSize(usage.max)}",
        ]

        // add peak info, if available
        if (peak) {
            result[0] += "  (peak ${memfmt(peak, 'used')})"
            result[1] += "  (peak ${memfmt(peak, 'committed')})"
        }

        return result
    }
}

