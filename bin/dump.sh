#!/bin/bash

DIR=`dirname $0`
RELAY_DIR="$DIR/../upgrade-server"
SYSPROPS="-Dcom.sun.management.jmxremote -Djava.net.preferIPv4Stack=true"
TARGET="$RELAY_DIR/target"
CP="$TARGET/classes:$TARGET/lib/*"
#DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

java $SYSPROPS $DEBUG -cp $CP org.jgroups.upgrade_server.Dump $*