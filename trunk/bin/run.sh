#!/bin/sh

RAW=true

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
CLASSPATH=$(ls -1 $SCRIPTROOT/lib/*.jar | tr "\n" : | sed -e 's/:$//')${CLASSPATH:+:$CLASSPATH}
#echo $CLASSPATH

# Start program
. "$GROOVY_HOME/bin/startGroovy"

JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote"
JAVA_OPTS="$JAVA_OPTS -Dproject.name=@project.name@"
JAVA_OPTS="$JAVA_OPTS -Dproject.version=@project.version@"
JAVA_OPTS="$JAVA_OPTS -Dproject.home=$SCRIPTROOT"

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

