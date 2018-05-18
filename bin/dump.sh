#!/bin/bash

DIR=`dirname $0`
RELAY_DIR="$DIR/../relay-server"
SYSPROPS="-Dcom.sun.management.jmxremote -Djava.net.preferIPv4Stack=true -Dlog4j.configurationFile=/home/bela/log4j2.xml"

cd $RELAY_DIR && mvn -o $SYSPROPS exec:java -Dexec.mainClass=org.jgroups.relay_server.Dump -Dexec.args="$*"