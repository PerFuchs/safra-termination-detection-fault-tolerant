#!/usr/bin/env bash
CLASSPATH=lib/cell1D.jar \
prun -np $1 $6 \
/home/pfs250/ibisImplemenation/runForSingleNode.sh $2 $(($1 * $2)) $3 "/var/scratch/pfs250/safraExperiment/$4" $5 > out.log