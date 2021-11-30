#!/bin/bash

export SYSPROPS="-Dsite=SFC -Dmcast_addr=230.4.5.6 -Dmcast_port=8600"

./run.sh jgroups-4 org.jgroups.demos.relay.RelayDemo -props relay.xml -name sfc1 $*