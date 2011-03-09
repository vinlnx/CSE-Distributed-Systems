#!/bin/bash

rm -rf storage
rm -f *.log
rm -f *.replay
xterm -geometry 150x30+0+0 -e "./router.pl -p 1025; bash" &
sleep 3
# note that this is for 3 nodes (aka RIOTester should have NUM_NODES = 3)
xterm -geometry 150x30+1000+0 -e "./execute.pl -e -n RIOTester -router-port 1025 -a 0 -f 0 -c scripts/RIOTest_emu; bash" &
xterm -geometry 150x30+0+500 -e "./execute.pl -e -n RIOTester -router-port 1025 -a 1 -f 0 -c scripts/RIOTest_emu; bash" &
xterm -geometry 150x30+1000+500 -e "./execute.pl -e -n RIOTester -router-port 1025 -a 2 -f 0 -c scripts/RIOTest_emu; bash" &
