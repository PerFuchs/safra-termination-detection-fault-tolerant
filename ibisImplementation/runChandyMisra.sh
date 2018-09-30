#!/usr/bin/env bash
numberOfNodes=$1
instancesPerNode=$2
instancesInTotal=$(($1 * $2))
repetitions=$3
outputFolder=$4
faultPercentage=$5
faultTolerance=$6
basicAlgorithm=$7
serverPort=$8
prunArguments=$9

faultGroup=${faultPercentage}
if [ ${faultTolerance} == 'fs' ]
then
  faultGroup="fs"
fi
outputFolder="${outputFolder}/${instancesInTotal}-${faultGroup}-${repetitions}.run"

rm -fr ${outputFolder}
mkdir -p ${outputFolder}

date > out.log
echo $0 $@ >> out.log

CLASSPATH=lib/cell1D.jar \
prun -np ${numberOfNodes} ${prunArguments} \
./runForSingleNode.sh \
${instancesPerNode} ${instancesInTotal} ${repetitions} ${outputFolder} \
${faultPercentage} ${faultTolerance} ${basicAlgorithm} ${serverPort} \
${numberOfNodes} >> out.log