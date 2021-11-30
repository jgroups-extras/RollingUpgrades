#!/bin/bash

export SYSPROPS="-Dsite=LON -Dmcast_addr=230.1.2.3 -Dmcast_port=8500"

./run.sh jgroups-5 org.jgroups.demos.RelayDemo -props relay.xml -name lon1 $*