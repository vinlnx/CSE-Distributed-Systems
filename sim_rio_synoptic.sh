#!/bin/bash

rm -rf storage
./execute.pl -s  -L total.log -l partial.log -n RIOTester -f 0 -c scripts/RIOTest

