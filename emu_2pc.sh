#!/bin/bash

rm -rf storage
rm -f *.log
rm -f *.replay
xterm -geometry 150x30+0+0 -e "./router.pl -p 1025; bash" &
sleep 3
xterm -geometry 150x30+1000+0 -e "./execute.pl -e -n Node2PC -router-port 1025 -f 0 -l 2pc_0.log -o 2pc_0.replay; bash" &
xterm -geometry 150x30+0+500 -e "./execute.pl -e -n Node2PC -router-port 1025 -f 0 -l 2pc_1.log -o 2pc_1.replay; bash" &
xterm -geometry 150x30+1000+500 -e "./execute.pl -e -n Node2PC -router-port 1025 -f 0 -l 2pc_2.log -o 2pc_2.replay; bash" &
