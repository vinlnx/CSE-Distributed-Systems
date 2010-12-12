#!/usr/bin/perl

# Simple script to start Fishnet

main();

sub main {
    
    $classpath = "./bin/:./jars/plume.jar";
    
    $args = join " ", @ARGV;

    system("rm -rf storage");
    exec("java -cp $classpath edu.washington.cs.cse490h.lib.MessageLayer $args");
    # exec("nice java -cp $classpath Fishnet $fishnetArgs");
}

