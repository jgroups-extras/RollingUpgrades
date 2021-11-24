#!/bin/bash

export SYSPROPS="-Dcom.sun.management.jmxremote -Djava.net.preferIPv4Stack=true -Dlog4j.configurationFile=/home/bela/log4j2.xml"

./run.sh jgroups-4 org.jgroups.tests.perf.UPerf -props config.xml $*