#!/usr/bin/env bash
CLASSPATH=lib/cell1D.jar \
prun -np $1 $4 \
/home/pfs250/ibisImplemenation/runForSingleNode.sh $2 $(($1 * $2)) $3 > out.log