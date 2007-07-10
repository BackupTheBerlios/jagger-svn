/*  jagger - About panel

    Copyright (c) 2007 by 1&1 Internet AG

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as
    published by the Free Software Foundation. A copy of this license is
    included with this software in the file "LICENSE.txt".

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    $Id: HelpPanel.groovy 122039 2007-07-04 16:16:52Z jhe $
*/

package de.web.tools.jagger.console.panels;

import de.web.tools.jagger.util.Fmt;
import de.web.tools.jagger.util.License;


class AboutPanel extends PanelBase {
    static final name = 'About'
    static final description = 'About this program'

    void generate(content) {
        """
${License.APPNAME} is a Java application monitoring tool using JMX technology
to aggregate, archive and visualize monitoring data for larger computer
clusters, giving developers and administrators both a succinct and
comprehensive view into their systems, which normal JMX consoles cannot do
due to information overflow.

See http://developer.berlios.de/ for more information.
""".split('\n').each { content << it }

        content << ''
        content << h1('Copyright')
        License.COPYRIGHT.split('\n').each { content << it }

        content << ''
        content << h1('License')
        License.LICENSE.split('\n').each { content << it }

        content << ''
        content << h1('Warranty')
        License.WARRANTY.split('\n').each { content << it }
    }
}

