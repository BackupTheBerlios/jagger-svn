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
    
    void testRoundTo() {
        assertEquals(1.0,  Fmt.roundTo(1.234, 0), DELTA)
        assertEquals(1.2,  Fmt.roundTo(1.234, 1), DELTA)
        assertEquals(1.23, Fmt.roundTo(1.234, 2), DELTA)
        assertEquals(1.24, Fmt.roundTo(1.235, 2), DELTA)
        assertEquals(1.24, Fmt.roundTo(1.236, 2), DELTA)

        assertEquals(100.0, Fmt.roundTo(123.45, -2), DELTA)
 	}

/*
    static private Double roundTo(Double value, Integer places) {
    static hoursTime(BigDecimal seconds) {
    static daysTime(BigDecimal seconds) {
    static humanTime(msecs, stripunit = false) {
    static humanTimeDiff(msecsStart, msecsEnd, stripunit = false) {
    static humanSize(Long bytes) {
    static humanCount(count) {
    static shorten(String text, Integer maxlen) {
    static percent(part, whole = 100.0, fmtstr = null) {
*/
}

