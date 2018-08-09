#!/usr/bin/env bash
date > out.log
echo $0 $@ >> out.log
CLASSPATH=lib/cell1D.jar \
prun -np $1 $8 \
/home/pfs250/ibisImplemenation/runForSingleNode.sh $2 $(($1 * $2)) $3 $4 $5 $6 $7 $1 >> out.log