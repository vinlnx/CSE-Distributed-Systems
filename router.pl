#!/usr/bin/perl

# Simple script to start a Router

main();

sub main {
    
    $classpath = "jars/plume.jar:jars/lib.jar";
    
    $args = join " ", @ARGV;

    exec("java -cp $classpath edu.washington.cs.cse490h.lib.Router $args");
}

