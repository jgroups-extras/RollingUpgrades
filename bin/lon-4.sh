#!/bin/bash

export SYSPROPS="-Dsite=LON -Dmcast_addr=230.1.2.3 -Dmcast_port=8500"

./run.sh jgroups-4 org.jgroups.demos.relay.RelayDemo -props relay.xml -name lon1 $*