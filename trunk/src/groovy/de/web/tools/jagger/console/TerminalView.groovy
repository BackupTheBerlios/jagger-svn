/*  jagger - Console view

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

package de.web.tools.jagger.console;

import java.text.SimpleDateFormat


class TerminalView {
    // ANSI sequences
    final Ansi = [
        // Cursor movement
        CLRSCR          : '\033[2J',
        HOME            : '\033[0;0H',

        // Colors and attributes
        NORMAL          : '\033[0m',
        BOLD            : '\033[1m',
        ITALICS         : '\033[3m',
        UNDERLINE       : '\033[4m',
        BLINK           : '\033[5m',
        INVERSE         : '\033[7m',
        CONCEALED       : '\033[8m',
        STRIKE          : '\033[9m',

        BOLD_OFF        : '\033[22m',
        ITALICS_OFF     : '\033[23m',
        UNDERLINE_OFF   : '\033[24m',
        //BLINK_OFF       : '\033[25m',
        INVERSE_OFF     : '\033[27m',
        //CONCEALED_OFF   : '\033[28m',
        STRIKE_OFF      : '\033[29m',

        BLACK           : '\033[0;30m',
        BLUE            : '\033[0;34m',
        GREEN           : '\033[0;32m',
        CYAN            : '\033[0;36m',
        RED             : '\033[0;31m',
        PURPLE          : '\033[0;35m',
        BROWN           : '\033[0;33m',
        LIGHT_GRAY      : '\033[0;37m',

        DARK_GRAY       : '\033[1;30m',
        LIGHT_BLUE      : '\033[1;34m',
        LIGHT_GREEN     : '\033[1;32m',
        LIGHT_CYAN      : '\033[1;36m',
        LIGHT_RED       : '\033[1;31m',
        LIGHT_PURPLE    : '\033[1;35m',
        YELLOW          : '\033[1;33m',
        WHITE           : '\033[1;37m',

        BG_BLACK        : '\033[0;40m',
        BG_BLUE         : '\033[0;44m',
        BG_GREEN        : '\033[0;42m',
        BG_CYAN         : '\033[0;46m',
        BG_RED          : '\033[0;41m',
        BG_PURPLE       : '\033[0;45m',
        BG_BROWN        : '\033[0;43m',
        BG_LIGHT_GRAY   : '\033[0;47m',

        // Logical sequences
        WINDOW          : '\033[1;37;44m',
        ALERT           : '\033[1;3;33;41m',
    ]

    // Constants
    final DEFAULT_ROWS = 24
    final DEFAULT_COLS = 79
    final SYS_ROWS = 2
    final BEEP_WAIT = 100
    final String EDGE_LEFT = "[ "
    final String EDGE_RIGHT = " ]"
    final DATEFMT = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss')

    // Properties
    String title = ""
    String status = ""
    String panel = ""

    // Status
    private content = []
    private Integer row_offset = 0
    private Boolean beepOnUpdate = false

    // current window size
    def ROWS = DEFAULT_ROWS
    def COLS = DEFAULT_COLS


    /** get current window size from environent, if possible
     */
    private void getWindowSize() {
        def rows = System.getProperty('terminal.rows')
        def cols = System.getProperty('terminal.cols')

        if (!''.equals(rows)) {
            try { ROWS = new Integer(rows) - 1 }
            catch (NumberFormatException ex) {}
        }
        if (!''.equals(cols)) {
            try { COLS = new Integer(cols) - 1 }
            catch (NumberFormatException ex) {}
        }
    }

    /** force row_offset into limits, return true if correction was necessary
     */
    private Boolean checkOffset() {
        def oldval = row_offset
        
        // order is important here!
        row_offset = [row_offset, content.size() - ROWS + SYS_ROWS].min()
        row_offset = [0, row_offset].max()

        return oldval != row_offset
    }

    /** make line exactly COLS characters long, taking invisible
        ANSI sequences into account.
     */
    private String padOrChop(String line) {
        if (line.length() <= COLS) {
            return ' ' * (COLS+1) + '\r' + line
        }

        def idx = 0
        def pos = 0
        Boolean ansi = false
        for (ch in line) {
            switch (ch as Character) {
                case '\033' as Character:
                    ansi = true
                    break
                    
                case {'a' <= it && it <= 'z' || 'A' <= it && it <= 'Z'}:
                    if (ansi) {
                        ansi = false
                        break
                    }
                    // fallthrough

                default:
                    if (!ansi) pos += 1
                    break
            }
            idx += 1
            //println "$pos $idx $ansi ${(ch as Character)}"
            if (pos >= COLS) break
        }
        //println "$pos $idx ${line.length()} $ansi"

        // line longer than COLS only due to ANSI seqs
        if (pos < COLS || idx == line.length()) {
            return ' ' * COLS + '\r' + line
        }

        return line[0..<idx] + Ansi.DARK_GRAY + '>' + Ansi.NORMAL
    }

    void init() {
        print Ansi.CLRSCR
        print Ansi.HOME
        getWindowSize()
    }

    void beep() {
        beepOnUpdate = true
    }
    
    void clear() {
        row_offset = 0

        println Ansi.HOME
        for (i in 1..(ROWS-SYS_ROWS)) {
            println ' ' * COLS
        }
        getWindowSize()
    }

    private List getScreen() {
        def stamp = { buf, pos, txt ->
            def extra_chars = EDGE_LEFT.length() + EDGE_RIGHT.length()
            if (pos < 0)
                pos -= txt.length() + extra_chars - 1
            buf[pos..(pos + txt.length() + extra_chars - 1)] = "$EDGE_LEFT$txt$EDGE_RIGHT"
        }

        def screen = []
        checkOffset()

        // header
        def titlebar = new StringBuffer('=' * COLS)
        def now = DATEFMT.format(new Date())
        stamp(titlebar, -2, "'?' = help | $now")
        stamp(titlebar, 1, "$panel - $title")
        screen << Ansi.WINDOW + titlebar + Ansi.NORMAL

        // content
        for (row in 0..<(ROWS - SYS_ROWS)) {
            def line = ''
            if (row_offset + row < content.size()) {
                line = content[row_offset + row]
            }

            screen << padOrChop(line)
        }
        
        // footer
        def statusbar = new StringBuffer('=' * COLS)
        stamp(statusbar, 1, status)

        if (content.size() > ROWS - SYS_ROWS) {
            def end = row_offset + ROWS - SYS_ROWS
            def up = ''
            def down = ''
            if (row_offset > 0) up = '^ '
            if (content.size() > end) down = ' v'
            def position = "$up${row_offset+1}..$end/${content.size()}$down"
            stamp(statusbar, -2, position)
        }

        screen << Ansi.WINDOW + statusbar + Ansi.NORMAL
        return screen
    }
    
    void update(new_content) {
        content = new_content
        def screen = getScreen()

        if (beepOnUpdate) {
            beepOnUpdate = false
            print '\007' + Ansi.HOME + Ansi.INVERSE
            print screen.join('\n' + Ansi.INVERSE)
            try {
                Thread.sleep(BEEP_WAIT)
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        print Ansi.HOME
        print screen.join('\n')
    }

    void close() {
        print Ansi.CLRSCR
        print Ansi.HOME
    }

    void pageUp() {
        row_offset -= ROWS - SYS_ROWS - 1
        if (checkOffset()) beep()
    }

    void pageDown() {
        row_offset += ROWS - SYS_ROWS - 1
        if (checkOffset()) beep()
    }

    void lineUp() {
        row_offset--
        if (checkOffset()) beep()
    }

    void lineDown() {
        row_offset++
        if (checkOffset()) beep()
    }
}

