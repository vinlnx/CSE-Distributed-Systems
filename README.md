Usage: java MessageLayer [options]

General Options:
  -h --help=<boolean>                               - Print usage message [default false]
  -v --version=<boolean>                            - Print program version [default false]

Execution Options:
  -s --simulate=<boolean>                           - Simulate [default false]
  -e --emulate=<boolean>                            - Emulate [default false]
  -n --nodeClass=<string>                           - Node class to use [default ]
  --routerHostname=<string>                         - Router hostname [default localhost]
  --routerPort=<int>                                - Router port [default -1]
  -t --timestep=<long>                              - Time step, in ms [default 1000]
  -r --seed=<long>                                  - Random seed
  -c --commandFile=<string>                         - Command file [default ]
  -f --failureLvlInt=<int>                          - Failure level, a number between 0 and 4 [default 4]

Debugging Options:
  -L --synopticTotallyOrderedLogFilename=<string>   - Synoptic totally ordered log filename [default ]
  -l --synopticPartiallyOrderedLogFilename=<string> - Synoptic partially ordered log filename [default ]
  -o --replayOutputFilename=<string>                - Replay output filename [default ]
  --replayInputFilename=<string>                    - Replay input filename [default ]
