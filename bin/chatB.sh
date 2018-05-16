#!/bin/bash

DIR=`dirname $0`
RELAY_DIR="$DIR/../jgroups-4.x"
SYSPROPS="-Dcom.sun.management.jmxremote -Djava.net.preferIPv4Stack=true -Dlog4j.configurationFile=/home/bela/log4j2.xml"


cd $RELAY_DIR && mvn $SYSPROPS exec:java -Dexec.mainClass=org.jgroups.demos.Chat \
 -Dexec.args="-props config.xml -name B"