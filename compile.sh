#!/bin/bash

javac -cp ./jars/plume.jar lib/edu/washington/cs/cse490h/lib/*.java proj/*.java
jar cvf ./jars/lib.jar lib/*.class

exit
