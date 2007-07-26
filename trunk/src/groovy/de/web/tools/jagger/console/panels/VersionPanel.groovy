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

    $Id$
*/

package de.web.tools.jagger.console.panels;

import de.web.tools.jagger.util.Fmt;


/**
 *  Panel displaying versions of the runtime environment and registered
 *  application components.
 *  See "src/conf/applicationContext.xml" for details on component registration.
 */
class VersionPanel extends PanelBase {
    // indent for version info
    static final INDENT = 10

    // maximum length of a version string (gets truncated if longer)
    static final MAX_VERSION_LENGTH = 15

    static final name = 'Versions'
    static final description = 'Component versions'

    void generate(content) {
        content << ''
        content << h1('Runtime versions')
        def dumpVersion = { name, version ->
            content << "${name.padLeft(INDENT)} $version"
        }
        controller.jvm.versions.each(dumpVersion)
        controller.tomcat.versions.each(dumpVersion)

        // assemble component version info
        def componentsByType = new TreeMap()
        controller.jvm.components.each {
            if (!componentsByType.containsKey(it.type)) {
                componentsByType[it.type] = new TreeMap()
            }
            componentsByType[it.type][it.name] = it.version
        }

        // display versions grouped by component type
        def padding = controller.view.COLS - INDENT - MAX_VERSION_LENGTH
        componentsByType.each { componentType, componentMap ->
            content << ''
            content << h1("${componentType} versions")

            // components of this type...
            componentMap.each { name, version ->
                content << "${''.padLeft(INDENT)} ${name.padRight(padding)} $version"
            }
        }
    }
}

