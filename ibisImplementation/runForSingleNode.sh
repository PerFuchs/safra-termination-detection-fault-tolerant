#!/usr/bin/env bash
startChandyMisraInstance () {
  local networkSize=$1
  local repetitionNumber=$2
  $IPL_HOME/scripts/ipl-run \
    -Xmx2g \
    -Dibis.server.address=10.100.255.254 \
    -Dibis.pool.name=chandyMisra \
    -Dibis.pool.size=${networkSize} \
    ibis.ipl.apps.safraExperiment.IbisNode /var/scratch/pfs250/safraExperiment${repetitionNumber}
}

for j in $(seq 1 $3)
 do
    echo "Starting repetition ${j}"
    for i in $(seq 1 $1)
    do
       startChandyMisraInstance $2 ${j} &
       pids[${i}]=$!
    done

    for pid in ${pids[*]}; do
        wait $pid
    done
    echo "Done repetition ${j}"
done

echo "Done"