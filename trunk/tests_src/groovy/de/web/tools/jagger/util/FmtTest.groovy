/*  jagger - Formatting helper unit tests

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
 
import groovy.util.GroovyTestCase;


/**
 *  Unit tests for Jagger text formatting helpers.
 */
class FmtTest extends GroovyTestCase {
    final static double DELTA = 1E-9

    private void checkData(String methodName, List data) {
        data.each { result, input ->
            assertEquals(result, Fmt.invokeMethod(methodName, input as Object[]))
        }
    }

    void testRoundTo() {
        def data = [
            [1.0, [1.234, 0]],
            [1.2, [1.234, 1]],
            [1.23, [1.234, 2]],
            [1.24, [1.235, 2]],
            [1.24, [1.236, 2]],

            [100.0, [123.45, -2]],
        ]

        data.each { result, input ->
            assertEquals(result, Fmt.invokeMethod('roundTo', input as Object[]), DELTA)
        }
 	}

    void testHoursTime() {
        checkData('hoursTime', [
            ['0:00:00', 0],
            ['0:01:00', 60],
            ['0:01:00', 60.0],
            ['0:01:00', 60.9],

            ['0:12:34', 12*60 + 34],
            ['1:00:00', 3600],

            ['9:59:59', 36000-1],
            ['10:00:00', 36000],

            ['23:59:59', 86400-1],
            ['24:00:00', 86400],

            ['99:59:59', 360000-1],
            ['100:00:00', 360000],
        ])
    }
    
    void testDaysTime() {
        checkData('daysTime', [
            ['0:00:00', 0],
            ['0:01:00', 60],
            ['0:01:00', 60.0],
            ['0:01:00', 60.9],

            ['23:59:59', 86400-1],
            ['1d 0:00:00', 86400],
            ['1d 10:00:00', 86400 + 36000],
        ])
    }
    
    void testHumanTime() {
        def data = [
            ['   0.0 ms ', 0],
            ['   0.1 ms ', 0.05],
            ['   0.1 ms ', 0.1],
            ['   0.1 ms ', 0.149],
            [' 999.0 ms ', 999],
            ['   1.0 s  ', 1000],
            ['   1.0 s  ', 1000.0],
            ['  59.0 s  ', 1000 * (60-1)],
            ['   1.0 min', 1000 * (60)],
            ['   1.0 min', 1000 * (62)],
            ['   1.0 min', 1000 * (62.999)],
            ['   1.1 min', 1000 * (63)],
            ['   1.1 min', 1000 * (66)],
            ['  59.9 min', 1000 * (3600-4)],
            ['  60.0 min', 1000 * (3600-3)],
            ['   1.0 h  ', 1000 * (3600)],
            ['  24.0 h  ', 1000 * (86400-1)],
            ['   1.0 d  ', 1000 * (86400)],
            ['   7.0 d  ', 1000 * (7*86400-1)],
            ['   1.0 wk ', 1000 * (7*86400)],
        ]

        data.each { result, time ->
            assertEquals(result, Fmt.humanTime(time))
            assertEquals(result, Fmt.humanTime(time, false))
            assertEquals(result.replaceAll('\\s+$', ''), Fmt.humanTime(time, true))
            //assertEquals(result.replaceAll('\\s+$', ''), Fmt.humanTime(msecs: time, stripunit: true))
        }
    }
    
    void testHumanTimeDiff() {
        checkData('humanTimeDiff', [
            ['   1.0 ms ', [1, 2]],
            ['   1.0 ms ', [1, 2, false]],
            ['   1.0 ms',  [1, 2, true]],
            ['   0.0 ms ', [2, 1]],
            ['   0.0 ms ', [1000, 1]],
        ])
    }
    
    void testHumanSize() {
        checkData('humanSize', [
            ['    0  bytes', 0],
            [' 1023  bytes', 1023],
            ['    1.00 KiB', 1024],
            [' 1024.00 KiB', 1024*1024-1],
            ['    1.00 MiB', 1024*1024],
            [' 1024.00 MiB', 1024*1024*1024-1],
            ['    1.00 GiB', 1024*1024*1024],
            [' 1024.00 GiB', 1024.0*1024*1024*1024-1],
            [' 1024.00 GiB', 1024.0*1024*1024*1024],
            ['10240.00 GiB', 1024.0*1024*1024*1024*10],
        ])
    }
    
    void testHumanCount() {
        checkData('humanCount', [
            ['0', 0],
            ['1', 1],
            ['999', 999],
            ['1,000', 1000],
            ['123,456,789', 123456789],
        ])
    }
    
    void testShorten() {
        checkData('shorten', [
            ['', ['', 10]],
            ['1...0', ['1234567890', 5]],
            ['12...0', ['1234567890', 6]],
            ['12...90', ['1234567890', 7]],
            ['12...890', ['1234567890', 8]],
            ['123...890', ['1234567890', 9]],
            ['1234567890', ['1234567890', 10]],
            ['1234567890', ['1234567890', 11]],
        ])
    }
    
    void testPercent() {
        checkData('percent', [
            ['0.0%', 0],
            ['0.0%', 0.0],
            ['100.0%', 100],
            ['100.0%', 100.0],

            ['0.0%', [0, 0]],
            ['0.0%', [42, 0]],
            ['20.0%', [7, 35]],

            ['20.0%', [7.0, 35]],
            ['20.0%', [7, 35.0]],
            ['20.0%', [7.0, 35.0]],

            ['000%', [0, 1, '%03.0f%%']],
            ['000%', [0, 1, '%03.0f%%']],
        ])
    }
}

