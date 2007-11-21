/*  jagger - JMX remote bean polling tests

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

package de.web.tools.jagger.jmx.polling;


class PollingContextTest extends GroovyTestCase {
    void testPollInstances() {
        def accessor = new Expando(
            injectValues: { result, agent ->
                result << "iV:$agent"
                result
            }
        )
        def poller = new PollingContext()

        def emc = new ExpandoMetaClass(poller.class)
        emc.getAgent = { [url: "gA:$it"] }
        emc.initialize()
        poller.metaClass = emc

        poller.pollInstances([[url: 'test1'], [url: 'test2']], accessor).eachWithIndex { val, idx ->
            assertEquals(['iV:["url":gA:["url":"test1"]]', 'iV:["url":gA:["url":"test2"]]'][idx], val)
        }
    }
}

