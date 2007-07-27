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

    $Id$
*/

package de.web.tools.jagger.util;


/**
 *  Helper class with static text formatting methods.
 *  <p>
 *  These functions generally use the US locale, i.e. "." as the
 *  decimal point, regardless of environment settings.
 */
class Fmt {

    /** You cannot create any instance of this class. */
    private Fmt() {}

    /**
     *  Rounds a value to a given number of decimal places.
     *
     *  @param value Value to be rounded.
     *  @param places Number of digits to keep.
     *  @return Value rounded to required decimal places.
     */
    static private Double roundTo(Double value, Integer places) {
        def scale = 10.0 ** places
        return (value * scale).round() / scale
    }

    /**
     *  Formats a time value in seconds to a display in hours, where
     *  the number of digits for the hour is undetermined.
     * 
     *  @param seconds Time value to format.
     *  @return Formatted time as "HHH:MM:SS".
     */
    static hoursTime(BigDecimal seconds) {
        Long hours = seconds / 3600
        Long mins = (seconds / 60).remainder(60)
        Long secs = seconds.remainder(60)

        return String.format('%d:%02d:%02d', hours, mins, secs)
    }

    /**
     *  Formats a time value in seconds to a display in days and hours,
     *  in the format "Dd H:MM:SS".
     *  If less than 24 hours are passed in, the day part is ommitted.
     *
     *  @param seconds Time value to format.
     *  @return Formatted time as "[Dd ][H]H:MM:SS".
     */
    static daysTime(BigDecimal seconds) {
        if (seconds < 86400)
            return hoursTime(seconds)
            
        Long days = seconds / 86400
        Long secs = seconds.remainder(86400)

        return String.format('%dd %s', days, hoursTime(secs))
    }

    /**
     *  Formats a time value in milliseconds to a fuzzy display that
     *  chooses an appropriate unit and precision, depending on the
     *  time value given. Units are "ms", "s", "min", "h", "d" and "wk".
     *
     *  @param msecs Time value to format.
     *  @param stripunit If true, don't pad unit with spaces.
     *  @return Formatted time.
     */
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

    /**
     *  Formats a time difference (duration) to a fuzzy display with
     *  appropriate unit and precision.
     *
     *  @param msecsStart Start time [msec].
     *  @param msecsEnd End time [msec].
     *  @param stripunit If true, don't pad unit with spaces.
     *  @return Formatted time difference.
     *  @see Fmt.humanTime
     */
    static humanTimeDiff(msecsStart, msecsEnd, stripunit = false) {
        Fmt.humanTime([0, msecsEnd - msecsStart].max(), stripunit)
    }

    /**
     *  Formats a byte size to a fuzzy display that
     *  chooses an appropriate unit and precision, depending on the
     *  value given. Units are the (IEC/IEEE standard) "bytes", "KiB", "MiB"
     *  and "GiB".
     *
     *  @param bytes Data amount to format.
     *  @return Formatted size.
     */
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

    /**
     *  Formats a simple integer counter value to a thousands-separated
     *  display. Separation character is always a comma (US locale).
     *
     *  @param count Counter value.
     *  @return Formatted counter value.
     */
    static humanCount(count) {
        String.format(Locale.US, '%,.0f', count as BigDecimal)
    }

    /**
     *  Shortens a text to a given maximal length, taking out a part of
     *  the original and inserting "..." instead if necessary.
     *
     *  @param text Text to be limited.
     *  @param maxlen Maximum length of result.
     *  @return Limited text.
     */
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

    /**
     *  Format a percentage value, given as two values (part / whole) or
     *  as a precalculated percentage (normalized to 0..100).
     *  By default, a precision of one digit after the decimal point is used.
     *
     *  @param part Partial value, or percentage.
     *  @param whole Maximal value (default 100.0).
     *  @param fmtstr Optional format string to use (must use "%f").
     *  @return Formatted percentage, including "%" character.
     */
    static percent(part, whole = 100.0, fmtstr = null) {
        if (fmtstr == null) fmtstr = "%.1f%%"

        Double pc = 0.0
        if (whole > 0) {
            pc = roundTo(part * 100.0 / whole, 3)
        }
        
        return String.format(Locale.US, fmtstr, pc)
    }
}

