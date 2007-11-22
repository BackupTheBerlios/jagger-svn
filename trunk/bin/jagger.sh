#!/bin/sh
#
# jagger console - Startup wrapper for UNIX
#
# Copyright (c) 2007 by 1&1 Internet AG
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License version 2 as
# published by the Free Software Foundation. A copy of this license is
# included with this software in the file "LICENSE.txt".
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# $Id$

# if you really like to hit RETURN often, change to false
RAW=true

source $(dirname $0)/jagger-common.rc

JAVA_OPTS="$JAVA_OPTS -Dterminal.rows=$(tput lines)"
JAVA_OPTS="$JAVA_OPTS -Dterminal.cols=$(tput cols)"

if $RAW; then
    SAVED_MODES=$(stty -g)
    trap "stty $SAVED_MODES" EXIT SIGHUP SIGINT SIGQUIT SIGABRT SIGSEGV SIGTERM
    stty raw -echo ignbrk brkint inlcr icrnl opost isig
fi

# start in subshell so stty reset is ensured
( startGroovy @project.package@.Console "$@" ) 

if $RAW; then
    stty $SAVED_MODES
fi

