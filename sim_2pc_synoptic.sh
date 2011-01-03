#!/bin/bash

rm -rf storage
rm -f *.log
rm -f *.replay
./execute.pl -L total.log -l partial.log -s -n Node2PC -f 0 -c scripts/2PC -o 2pc.replay
