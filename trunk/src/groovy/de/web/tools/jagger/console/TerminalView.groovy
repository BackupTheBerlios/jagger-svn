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

import java.text.SimpleDateFormat;


/**
 *  Visualization of panel content on an ANSI terminal, including paging,
 *  visual beep and other supporting features.
 */
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

    // default terminal size, if no better value available
    final DEFAULT_ROWS = 24

    // default terminal size, if no better value available
    final DEFAULT_COLS = 79

    // lines used by the terminal view itself (title, status)
    final SYS_ROWS = 2

    // wait time for visial beep [msec]
    final BEEP_WAIT = 100

    // left edge of title / status inserts
    final String EDGE_LEFT = "[ "

    // right edge of title / status inserts
    final String EDGE_RIGHT = " ]"

    // format of time display in title
    final DATEFMT = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss')

    // the title of the window
    String title = ""

    // text for status line insert
    String status = ""

    // the name of the currently display panel
    String panel = ""

    // list of content lines
    private content = []

    // zero-based offset into content, first line to be displayed
    private Integer row_offset = 0

    // if true, do a visual beep on the next update, then reset to false
    private Boolean beepOnUpdate = false

    // current terminal size (number of lines)
    def ROWS = DEFAULT_ROWS

    // current terminal size (number of columns)
    def COLS = DEFAULT_COLS


    /**
     *  Gets current window size from environent, if possible.
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

    /**
     *  Forces "row_offset" into allowed limits. Must be called after
     *  every unchecked manipulation of "row_offset".
     *
     *  @return true if correction was necessary.
     *  @todo Could be enforced by refactoring to a setter.
     */
    private Boolean checkOffset() {
        def oldval = row_offset
        
        // order is important here!
        row_offset = [row_offset, content.size() - ROWS + SYS_ROWS].min()
        row_offset = [0, row_offset].max()

        return oldval != row_offset
    }

    /**
     *  Makes "line" exactly COLS glyphs long, taking invisible
     *  ANSI sequences into account. This is important so that
     *  neither other lines get overwritten nor older content remains
     *  on the console.
     *
     *  @param line The line to be canonicalized.
     *  @return Line limited in visual length.
     */
    private String padOrChop(String line) {
        def result = null

        // skip detailed checks if line obviously shorter than COLS
        if (line.length() > COLS) {
            // string is longer than COLS, could still be visually shorter
            def idx = 0
            def pos = 0
            Boolean ansi = false

            // count visible glyphs (in pos), ignoring ANSI sequences, and
            // bookkeep a tied offset into the line (in idx)
            for (ch in line) {
                switch (ch as Character) {
                    case '\033' as Character:
                        // within ANSI sequence
                        ansi = true
                        break
                    
                    case {'a' <= it && it <= 'z' || 'A' <= it && it <= 'Z'}:
                        // any letter ends the ANSI sequence
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

                // break out if reached end of terminal line
                if (pos >= COLS) break
            }
            //println "$pos $idx ${line.length()} $ansi"

            // line visually longer than COLS?
            if (pos >= COLS && idx > line.length()) {
                result = line[0..<idx] + Ansi.DARK_GRAY + '>' + Ansi.NORMAL
            }
        }
        
        if (result == null) {
            // pad with erasing whitespace
            // XXX could be relaced by "delete to EOL" ANSI sequence?!
            result = ' ' * (COLS+1) + '\r' + line
        }
        
        return result
    }

    /**
     *  Initializes and fully clears the terminal.
     */
    void init() {
        print Ansi.CLRSCR
        print Ansi.HOME
        getWindowSize()
    }

    /**
     *  Visually beeps terminal on next update.
     */
    void beep() {
        beepOnUpdate = true
    }
    
    /**
     *  Clears the terminal content (keeps title and status).
     */
    void clear() {
        row_offset = 0

        println Ansi.HOME
        for (i in 1..(ROWS-SYS_ROWS)) {
            println ' ' * COLS
        }
        getWindowSize()
    }

    /**
     *  Takes the content and prepares a list of lines ready to be
     *  printed to the terminal in order to replace the repviously
     *  displayed content with no or minimal flickering.
     *
     *  @return List of lines (without EOLs).
     */
    private List getScreen() {
        /*  Helper to stamp little sections of info into the header
         *  and footer lines.
         *
         *  @param buf Current line.
         *  @param pos Position of insert, can be negative for right-side offsets.
         *  @param txt Text to insert.
         *  @return Decorated line.
         */
        def stamp = { buf, pos, txt ->
            // length of characters we add for visual purposes
            def extra_chars = EDGE_LEFT.length() + EDGE_RIGHT.length()

            if (pos < 0) {
                // right-sided offsets are relative to the right side (END)
                // of the insert, fix into a relative string position
                // of the START of the insert
                pos -= txt.length() + extra_chars - 1
            }
            
            // replace relevant portion of "buf" by text insert
            buf[pos..(pos + txt.length() + extra_chars - 1)] = "$EDGE_LEFT$txt$EDGE_RIGHT"
        }

        def screen = []
        checkOffset()

        // build header
        def titlebar = new StringBuffer('=' * COLS)
        def now = DATEFMT.format(new Date())
        stamp(titlebar, -2, "'?' = help | $now")
        stamp(titlebar, 1, "$panel - $title")
        screen << Ansi.WINDOW + titlebar + Ansi.NORMAL

        // build content
        for (row in 0..<(ROWS - SYS_ROWS)) {
            def line = ''

            // if not beyond the end of content...
            if (row_offset + row < content.size()) {
                line = content[row_offset + row]
            }

            screen << padOrChop(line)
        }
        
        // build footer
        def statusbar = new StringBuffer('=' * COLS)
        stamp(statusbar, 1, status)

        if (content.size() > ROWS - SYS_ROWS) {
            // add scroll info to footer, when we have something to scroll
            def end = row_offset + ROWS - SYS_ROWS
            def up = ''
            def down = ''
            if (row_offset > 0) up = '^ '
            if (content.size() > end) down = ' v'
            def position = "$up${row_offset+1}..$end/${content.size()}$down"
            stamp(statusbar, -2, position)
        }

        screen << Ansi.WINDOW + statusbar + Ansi.NORMAL

        // return the assembled terminal screen
        return screen
    }
    
    /**
     *  Updates the terminal view with new content.
     *
     *  @param new_content List of lines to be displayed.
     */
    void update(new_content) {
        // save content for later (scrolling), and prepare for output
        content = new_content
        def screen = getScreen()

        // has someone requested a beep?
        if (beepOnUpdate) {
            // beep only once per update event
            beepOnUpdate = false

            // display INVERSE content for some short time, including
            // an audible beep
            print '\007' + Ansi.HOME + Ansi.INVERSE
            print screen.join('\n' + Ansi.INVERSE)
            try {
                Thread.sleep(BEEP_WAIT)
            } catch (InterruptedException e) {
                // Ignore (new content was signalled)
            }
        }

        // plain display of new content, overwriting the old one
        print Ansi.HOME
        print screen.join('\n')
    }

    /**
     *  Closes terminal, leaving it blank.
     */
    void close() {
        print Ansi.CLRSCR
        print Ansi.HOME
    }

    /**
     *  Scrolls content up a page (previous page with one line overlap).
     *  Does NOT update the screen.
     */
    void pageUp() {
        row_offset -= ROWS - SYS_ROWS - 1
        if (checkOffset()) beep()
    }

    /**
     *  Scrolls content down a page (next page with one line overlap).
     *  Does NOT update the screen.
     */
    void pageDown() {
        row_offset += ROWS - SYS_ROWS - 1
        if (checkOffset()) beep()
    }

    /**
     *  Scrolls content up a line (previous line).
     *  Does NOT update the screen.
     */
    void lineUp() {
        row_offset--
        if (checkOffset()) beep()
    }

    /**
     *  Scrolls content down a line (next line).
     *  Does NOT update the screen.
     */
    void lineDown() {
        row_offset++
        if (checkOffset()) beep()
    }
}

