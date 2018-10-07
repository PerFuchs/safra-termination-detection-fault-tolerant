#!/usr/bin/env bash

ant

rm -rf /var/scratch/pfs250/test
mkdir -p /var/scratch/pfs250/test

echo "Running 50s"
./runChandyMisra.sh 1 50 2 /var/scratch/pfs250/test 0.00 fs aky 8888
./runChandyMisra.sh 1 50 10 /var/scratch/pfs250/test 0.00 ft aky 8888
echo "5n"
./runChandyMisra.sh 1 50 10 /var/scratch/pfs250/test 0.25 ft aky 8888
echo "90"
./runChandyMisra.sh 1 50 10 /var/scratch/pfs250/test 0.90 ft aky 8888

echo "Running 200s"
./runChandyMisra.sh 4 50 10 /var/scratch/pfs250/test 0.00 fs aky 8888
./runChandyMisra.sh 4 50 10 /var/scratch/pfs250/test 0.00 ft aky 8888
echo "5n"
./runChandyMisra.sh 4 50 10 /var/scratch/pfs250/test 0.25 ft aky 8888
echo "90"
./runChandyMisra.sh 4 50 10 /var/scratch/pfs250/test 0.90 ft aky 8888