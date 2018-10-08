#!/usr/bin/env bash

ant

rm -rf /var/scratch/pfs250/big_test
mkdir -p /var/scratch/pfs250/big_test

reservation=`cat ~/reservation.txt`
echo ${reservation}

echo "Running 50s"
echo "90"
./runChandyMisra.sh 1 50 10 /var/scratch/pfs250/big_test 0.90 ft aky 8888 "-reserve $reservation"
echo "0.0"
./runChandyMisra.sh 1 50 10 /var/scratch/pfs250/big_test 0.00 fs aky 8888 "-reserve $reservation"
./runChandyMisra.sh 1 50 10 /var/scratch/pfs250/big_test 0.00 ft aky 8888 "-reserve $reservation"
echo "5n"
./runChandyMisra.sh 1 50 10 /var/scratch/pfs250/big_test 0.10 ft aky 8888 "-reserve $reservation"

echo "Running 2000s"
./runChandyMisra.sh 20 100 10 /var/scratch/pfs250/big_test 0.00 fs aky 8888 "-reserve $reservation"
./runChandyMisra.sh 20 100 10 /var/scratch/pfs250/big_test 0.00 ft aky 8888 "-reserve $reservation"
echo "5n"
./runChandyMisra.sh 20 100 10 /var/scratch/pfs250/big_test 0.0025 ft aky 8888 "-reserve $reservation"
echo "90"
./runChandyMisra.sh 20 100 10 /var/scratch/pfs250/big_test 0.90 ft aky 8888 "-reserve $reservation"


echo "Running 200s"
./runChandyMisra.sh 4 50 2 /var/scratch/pfs250/big_test 0.00 fs aky 8888 "-reserve $reservation"
./runChandyMisra.sh 4 50 10 /var/scratch/pfs250/big_test 0.00 ft aky 8888 "-reserve $reservation"
echo "5n"
./runChandyMisra.sh 4 50 10 /var/scratch/pfs250/big_test 0.025 ft aky 8888 "-reserve $reservation"
echo "90"
./runChandyMisra.sh 4 50 10 /var/scratch/pfs250/big_test 0.90 ft aky 8888 "-reserve $reservation"





