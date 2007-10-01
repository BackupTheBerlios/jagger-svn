#!/bin/sh
#
# jagger demon - Startup wrapper for UNIX
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

# make our own JMX agent available? (used for testing)
JMX_REMOTE_PORT=$(grep -e '^ *jmx.remote.port *[=:]' jagger.properties 2>/dev/null | sed -e 's/ *jmx.remote.port *[=:] *//g')
#echo $JMX_REMOTE_PORT; exit 1

if test -z $GROOVY_HOME; then
    echo >&2 "You must set GROOVY_HOME!"
    exit 1 
fi

# Get the fully qualified path to the script
case $0 in
    /*) SCRIPT="$0" ;;
    *)  SCRIPT="$PWD/$0" ;;
esac

# Get the real path to this script, resolving any symbolic links
SCRIPTROOT=
for C in $(echo $SCRIPT | tr " /" ": "); do
    SCRIPTROOT="$SCRIPTROOT/$C"
    while [ -h "$SCRIPTROOT" ] ; do
        LINK=$(expr "$(ls -ld "$SCRIPTROOT")" : '.*-> \(.*\)$')
        if expr "$LINK" : '/.*' >/dev/null; then
            SCRIPTROOT="$LINK"
        else
            SCRIPTROOT="$(dirname "$SCRIPTROOT")/$LINK"
        fi
    done
done

# Change ":" chars back to spaces and resolve ".." and ".".
SCRIPTROOT=$(cd "$(dirname "$(dirname "$(echo "$SCRIPTROOT" | tr : " ")")")" && pwd)

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

if test $JMX_REMOTE_PORT; then
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.port=$JMX_REMOTE_PORT"
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.ssl=false"
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.authenticate=true"
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.password.file=$SCRIPTROOT/@conf_path@/jmxremote.password.properties"
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.access.file=$SCRIPTROOT/@conf_path@/jmxremote.access.properties"
fi

startGroovy @project.package@.Demon "$@"

