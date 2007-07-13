/*  jagger - Configuration

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

package de.web.tools.jagger.util;

import org.springframework.context.support.ClassPathXmlApplicationContext;


class Config {
    final String APPLICATION_CONTEXT = 'applicationContext.xml'
    final String USER_CONTEXT = 'userContext.xml'

    /// the spring context
    private volatile springContext = null

    /// configuration properties (jagger.properties / cmd line)
    def props = [:]

    def getContext() {
        if (springContext == null) {
            synchronized (this) {
                if (springContext == null) {
                    springContext = new ClassPathXmlApplicationContext(
                        [APPLICATION_CONTEXT, USER_CONTEXT] as String[]
                    )
                }
            }
        }
        return springContext
    }
}

