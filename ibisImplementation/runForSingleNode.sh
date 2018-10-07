#!/usr/bin/env bash
instancesPerNode=$1
instancesInTotal=$2
repetitions=$3
outputFolder=$4
faultPercentage=$5
faultTolerance=$6
basicAlgorithm=$7
serverPort=$8
numberOfNodes=$9

startChandyMisraInstance () {
  local networkSize=$1
  local outputFolder=$2
  local crashPercentage=$3
  local isFaultTolerant=$4
  local basicAlgorithm=$5
  local serverPort=$6

  $IPL_HOME/scripts/ipl-run \
    -Xmx2g \
    -Dibis.server.address=10.100.255.254:${serverPort} \
    -Dibis.pool.name=chandyMisra \
    -Dibis.pool.size=${networkSize} \
    ibis.ipl.apps.safraExperiment.IbisNode ${outputFolder} ${crashPercentage} ${isFaultTolerant} ${basicAlgorithm} ${repetitions}
}


for i in $(seq 1 ${instancesPerNode})
do
   startChandyMisraInstance ${instancesInTotal} "$outputFolder" ${faultPercentage} ${faultTolerance} ${basicAlgorithm} ${serverPort} &
   pids[${i}]=$!
done
