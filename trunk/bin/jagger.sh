#!/bin/sh
#
# jagger - Startup wrapper for UNIX
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

# make our own JMX agent available? (used for testing)
JMX_REMOTE_PORT=$(grep jmx.remote.port= jagger.properties 2>/dev/null | sed -e 's/jmx.remote.port=//g')

if test -z $GROOVY_HOME; then
    echo >&2 "You must set GROOVY_HOME!"
    exit 1 
fi

# Get the fully qualified path to the script
case $0 in
    /*) SCRIPT="$0" ;;
    *)  SCRIPT="$(pwd)/$0" ;;
esac
SCRIPTROOT=$(dirname $(dirname $SCRIPT))

# Generate the classpath
CLASSPATH=$(ls -1 $SCRIPTROOT/@lib_path@/*.jar | tr "\n" : | sed -e 's/:$//')${CLASSPATH:+:$CLASSPATH}
if test -d $SCRIPTROOT/@conf_path@ ; then
    CLASSPATH=$SCRIPTROOT/@conf_path@${CLASSPATH:+:$CLASSPATH}
fi
#echo $CLASSPATH; exit 1

# Start program
. "$GROOVY_HOME/bin/startGroovy"


##JAVA_OPTS="$JAVA_OPTS -Djava.util.logging.config.class=de.web.tools.jagger.util.LogManager"
JAVA_OPTS="$JAVA_OPTS -Djava.util.logging.config.file=$SCRIPTROOT/@conf_path@/logging.properties"
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote"
JAVA_OPTS="$JAVA_OPTS -Dproject.name=@project.name@"
JAVA_OPTS="$JAVA_OPTS -Dproject.version=@project.version@"
JAVA_OPTS="$JAVA_OPTS -Dproject.home=$SCRIPTROOT"
JAVA_OPTS="$JAVA_OPTS -Dterminal.rows=$(tput lines)"
JAVA_OPTS="$JAVA_OPTS -Dterminal.cols=$(tput cols)"

if test -n $JMX_REMOTE_PORT; then
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.port=$JMX_REMOTE_PORT"
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.authenticate=false"
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.ssl=false"
fi

if $RAW; then
    SAVED_MODES=$(stty -g)
    trap "stty $SAVED_MODES" EXIT SIGHUP SIGINT SIGQUIT SIGABRT SIGSEGV SIGTERM
    stty raw -echo ignbrk brkint inlcr icrnl opost isig
fi

# start in subshell so stty reset is ensured
( startGroovy @project.package@.CLI "$@" ) 

if $RAW; then
    stty $SAVED_MODES
fi

