#!/usr/bin/env bash
startChandyMisraInstance () {
  local networkSize=$1
  $IPL_HOME/scripts/ipl-run \
    -Dibis.server.address=10.100.255.254 \
    -Dibis.pool.name=chandyMisra \
    -Dibis.pool.size=${networkSize} \
    ibis.ipl.apps.cell1d.IbisNode
}

for i in $(seq 1 $1)
do
   startChandyMisraInstance $2 &
done
