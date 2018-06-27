#!/usr/bin/env bash
CLASSPATH=lib/cell1D.jar \
prun -np $1 $3 \
/home/pfs250/ibisImplemenation/runForSingleNode.sh $2 $(($1 * $2)) > out.log