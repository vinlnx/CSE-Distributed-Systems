#!/bin/bash

javac lib/edu.washington.cs.cse490h/lib/*.java proj/*.java
jar cvf lib.jar lib/*.class

exit
