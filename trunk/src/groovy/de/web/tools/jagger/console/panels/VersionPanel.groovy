/*  jagger - Version panel

    Copyright (c) 2007 by 1&1 Internet AG

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as
    published by the Free Software Foundation. A copy of this license is
    included with this software in the file "LICENSE.txt".

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    $Id: VersionPanel.groovy 122671 2007-07-06 08:27:43Z jhe $
*/

package de.web.tools.jagger.console.panels;

import de.web.tools.jagger.util.Fmt;


class VersionPanel extends PanelBase {
    static final name = 'Versions'
    static final description = 'Component versions'

    void generate(content) {
        content << ''
        content << h1('Runtime versions')
        def dumpVersion = { name, version ->
            content << "${name.padLeft(10)} $version"
        }
        controller.jvm.versions.each(dumpVersion)
        controller.tomcat.versions.each(dumpVersion)

        content << ''
        content << h1('Component versions')

    }
}

