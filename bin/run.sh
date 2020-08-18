#!/bin/bash

## Script to run a demo
## Parameter $1: JGroups directory
## Parameter $2: classname


DIR=`dirname $0`
RELAY_DIR="$DIR/../$1"

JGROUPS_VERSION=`cd $RELAY_DIR && mvn exec:java -Dexec.mainClass=org.jgroups.Version|grep Version:`

echo "Running $2 in $1 ($JGROUPS_VERSION)"

cd $RELAY_DIR && mvn $SYSPROPS exec:java -Dexec.mainClass=$2 \
 -Dexec.args="-props config.xml"