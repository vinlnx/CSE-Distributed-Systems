package edu.washington.cs.cse490h.lib;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.net.SocketException;

import edu.washington.cs.cse490h.lib.Manager.FailureLvl;

/**
 * <pre>   
 * Class with main method that starts up a Manager. Either an Emulator or a Simulator
 * Usage:  java MessageLayer <simulate> <num nodes> [fishnet file] [timescale]
 *         or
 *         java MessageLayer <emulate> <trawler host name> <trawler port> <local port to use> [fishnet file]
 *         
 *         Arguments in <> are required and arguments in [] are optional. MessageLayer file is a file with commands for a node
 * </pre>   
 */
public class MessageLayer {

	private static void usage() {
		System.out.println("Usage:  java MessageLayer <simulate> <nodeclass> [-R{0|1|2|3|4} [seed]] [-c commandfile]\n" + 
				"or\n" + 
				"java MessageLayer <emulate> <nodeclass> <trawler host name> <trawler port> <local port to use> [-R{0|1|2|3|4} [seed]] [-c commandfile]\n\n" +          
				"Arguments in <> are required and arguments in [] are optional.\n" +  
				"command file is a file with commands for a node");
	}

	/**
	 * The main method. Entry point to start a Manager
	 */
	public static void main(String[] args) {
		if(args.length < 2) {
			System.err.println("Missing arguments");
			usage();
			return;
		}

		try {
			Manager manager = null;

			URLClassLoader nodeLoader = new URLClassLoader(new URL[] {ClassLoader.getSystemResource("../proj/")});
			Class<? extends Node> nodeImpl = nodeLoader.loadClass(args[1]).asSubclass(Node.class);

			if(args[0].equals("simulate")) {
				FailureLvl failureGen = FailureLvl.EVERYTHING;
				String commandFile = null;
				Long seed = null;

				for(int i = 2; i < args.length; ++i){
					if(args[i].charAt(0) == '-'){
						if(args[i].charAt(1) == 'R'){
							if(args[i].substring(1).equals("R0")){
								failureGen = FailureLvl.NOTHING;
							}else if(args[i].substring(1).equals("R1")){
								failureGen = FailureLvl.CRASH;
							}else if(args[i].substring(1).equals("R2")){
								failureGen = FailureLvl.DROP;
							}else if(args[i].substring(1).equals("R3")){
								failureGen = FailureLvl.DELAY;
							}else{
								failureGen = FailureLvl.EVERYTHING;
							}
							
							if(i+1 < args.length){
								try{
									seed = Long.parseLong(args[i+1]);
									++i;
								}catch(NumberFormatException e){
									seed = null;
								}
							}
						}else if(args[i].substring(1).equals("c")){
							if(++i < args.length){
								commandFile = args[i];
							}else{
								System.err.println("No command file specified");
							}
						}else{
							System.err.println("Unknown option: " + args[i]);
						}
					}else{
						System.err.println("Unknown argument: " + args[i]);
					}
				}

				try {
					if(commandFile != null){
						manager = new Simulator(nodeImpl, failureGen, commandFile, seed);
					}else{
						manager = new Simulator(nodeImpl, failureGen, seed);
					}
				}catch(IllegalArgumentException e) {
					System.err.println("Illegal arguments given to Simulator. Exception: " + e);
					return;
				}catch(FileNotFoundException e) {
					System.err.println("Incorrect command file name given to Simulator. Exception: " + e);		    
					return;
				}

			}else if(args[0].equals("emulate")) {
				if(args.length < 5) {
					System.err.println("Missing arguments to emulator");
					usage();
					return;
				}
				String trawlerName = args[2];
				int trawlerPort = Integer.parseInt(args[3]);
				int localUDPPort = Integer.parseInt(args[4]);
				
				FailureLvl failureGen = FailureLvl.EVERYTHING;
				String commandFile = null;
				Long seed = null;

				for(int i = 5; i < args.length; ++i){
					if(args[i].charAt(0) == '-'){
						if(args[i].charAt(1) == 'R'){
							if(args[i].substring(1).equals("R0")){
								failureGen = FailureLvl.NOTHING;
							}else if(args[i].substring(1).equals("R1")){
								failureGen = FailureLvl.CRASH;
							}else if(args[i].substring(1).equals("R2")){
								failureGen = FailureLvl.DROP;
							}else if(args[i].substring(1).equals("R3")){
								failureGen = FailureLvl.DELAY;
							}else{
								failureGen = FailureLvl.EVERYTHING;
							}
							
							if(i+1 < args.length){
								try{
									seed = Long.parseLong(args[i+1]);
									++i;
								}catch(NumberFormatException e){
									seed = null;
								}
							}
						}else if(args[i].substring(1).equals("c")){
							if(++i < args.length){
								commandFile = args[i];
							}else{
								System.err.println("No command file specified");
							}
						}else{
							System.err.println("Unknown option: " + args[i]);
						}
					}else{
						System.err.println("Unknown argument: " + args[i]);
					}
				}
				
				
				try {
					if(commandFile != null){
						manager = new Emulator(nodeImpl, trawlerName, trawlerPort, localUDPPort, failureGen, commandFile, seed);
					}else{
						manager = new Emulator(nodeImpl, trawlerName, trawlerPort, localUDPPort, failureGen, seed);
					}
				}catch(UnknownHostException e) {
					System.err.println("CentralHandler host name is unkown! Exception: " + e);
					return;
				}catch(SocketException e) {
					System.err.println("Could not bind to the given local port: " + localUDPPort + ". Exception: " + e);
					return;
				}catch(IOException e) {
					System.err.println("Encountered exception while trying to connect to CentralHandler. Exception " + e);
					return;
				}catch(IllegalArgumentException e) {
					System.err.println("Illegal arguments given to Emulator. Exception: " + e);
					return;
				}
			}else {
				System.err.println("Unknown arguments");
				usage();
				return;
			}

			manager.start();
		}catch(Exception e) {
			System.err.println("Exception occured in MessageLayer!! Exception: " + e);
			e.printStackTrace();
		}
	}
}
