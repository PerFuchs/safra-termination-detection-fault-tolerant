#!/usr/bin/env bash
rm -f "/var/scratch/pfs250/safraExperiment/$4.zip"
CLASSPATH=lib/cell1D.jar \
prun -np $1 $8 \
/home/pfs250/ibisImplemenation/runForSingleNode.sh $2 $(($1 * $2)) $3 "/var/scratch/pfs250/safraExperiment/$4" $5 $6 $7 > out.log
zip -r "/var/scratch/pfs250/safraExperiment/$4.zip" "/var/scratch/pfs250/safraExperiment/$4"