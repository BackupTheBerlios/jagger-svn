/*  jagger - Formatting helper

    Copyright (c) 2007 by 1&1 Internet AG

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as
    published by the Free Software Foundation. A copy of this license is
    included with this software in the file "LICENSE.txt".

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    $Id: Fmt.groovy 122571 2007-07-06 08:10:51Z jhe $
*/

package de.web.tools.jagger.util;

class Fmt {

    static private Double roundTo(Double value, Integer places) {
        def scale = 10.0 ** places
        return (value * scale + 0.5).round() / scale
    }

    static hoursTime(BigDecimal seconds) {
        Long hours = seconds / 3600
        Long mins = (seconds / 60).remainder(60)
        Long secs = seconds.remainder(60)

        return String.format('%d:%02d:%02d', hours, mins, secs)
    }

    static daysTime(BigDecimal seconds) {
        if (seconds < 86400)
            return hoursTime(seconds)
            
        Long days = seconds / 86400
        Long secs = seconds.remainder(86400)

        return String.format('%dd %s', days, hoursTime(secs))
    }

    static humanTime(msecs, stripunit = false) {
        static final units = [
            [name: 's  ', scale: 1000.0],
            [name: 'min', scale:   60.0],
            [name: 'h  ', scale:   60.0],
            [name: 'd  ', scale:   24.0],
            [name: 'wk ', scale:    7.0],
        ]
        Double value = msecs
        def unit = 'ms '

        for (u in units) {
            if (value < u.scale) break
            value /= u.scale
            unit = u.name
        }
        if (stripunit) unit = unit.trim()

        //return "$value $unit"
        return String.format(Locale.US, '%6.1f %s', roundTo(value, 2), unit)
    }

    static humanTimeDiff(msecsStart, msecsEnd, stripunit = false) {
        Fmt.humanTime([0, msecsEnd - msecsStart].max(), stripunit)
    }

    static humanSize(Long bytes) {
        if (bytes < 1024)
            return String.format(Locale.US, '%5d  bytes', bytes)

        Double value = bytes
        def unit = null

        for (it in ['KiB', 'MiB', 'GiB']) {
            if (value < 1024) break
            value /= 1024.0
            unit = it
        }
    
        return String.format(Locale.US, '%8.2f %s', roundTo(value, 3), unit)
    }

    static humanCount(count) {
        String.format(Locale.US, '%,.0f', count as BigDecimal)
    }

    // shorten a text to a certain length
    static shorten(String text, Integer maxlen) {
        if (text.length() > maxlen) {
            // show 1/3 of the start and 2/3 of the end
            Integer cutpoint = maxlen / 3
            def shortened = text[0..cutpoint] + '...' + text.substring(text.length()-maxlen+cutpoint+4)
            assert maxlen == shortened.length()
            return shortened
        } else {
            return text
        }
    }

    static percent(part, whole, fmtstr = null) {
        if (fmtstr == null) fmtstr = "%.1f%%"

        Double pc = 0.0
        if (whole > 0) {
            pc = roundTo(part * 100.0 / whole, 3)
        }
        
        return String.format(Locale.US, fmtstr, pc)
    }
}

