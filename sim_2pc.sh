#!/bin/bash

rm -rf storage
rm -f *.log
rm -f *.replay
./execute.pl -s -n Node2PC -f 0 -c scripts/2PC -l 2pc.log -o 2pc.replay
