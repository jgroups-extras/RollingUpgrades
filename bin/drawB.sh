#!/bin/bash

DIR=`dirname $0`
RELAY_DIR="$DIR/../jgroups-4.x"

cd $RELAY_DIR && mvn -o exec:java -Dexec.mainClass=org.jgroups.demos.Draw \
 -Dexec.args="-props config.xml -name B"