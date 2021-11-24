#!/bin/bash

## Script to run a demo
## Parameter $1: JGroups directory
## Parameter $2: classname
## Rest: additional parameters


DIR=`dirname $1`
RELAY_DIR="$DIR/../$1"
CP=$RELAY_DIR/target/classes:$RELAY_DIR/target/lib/*

shift
EXECUTABLE=$1
shift

JGROUPS_VERSION=`java -cp $CP org.jgroups.Version | grep 'Version:'`
echo "Running: $EXECUTABLE, $JGROUPS_VERSION, args: $@"

java $SYSPROPS -cp $CP $EXECUTABLE $@
