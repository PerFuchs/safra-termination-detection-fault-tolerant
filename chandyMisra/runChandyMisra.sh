#!/usr/bin/env bash
CLASSPATH=lib/cell1D.jar \
prun -np $1 \
$IPL_HOME/scripts/ipl-run \
-Dibis.server.address=10.100.255.254 \
-Dibis.pool.name=test \
-Dibis.pool.size=$1 \
ibis.ipl.apps.cell1d.IbisNode