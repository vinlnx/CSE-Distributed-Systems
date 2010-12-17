package edu.washington.cs.cse490h.lib;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;

import plume.Option;
import plume.Options;
import plume.OptionGroup;

import edu.washington.cs.cse490h.lib.Manager.FailureLvl;

/**
 * <pre>
 * 
 * Class with main method that starts up a Manager. Either an Emulator or a Simulator
 * 
 * Usage: java MessageLayer [options]
 * 
 * General Options:
 *  -h --help=<boolean>               - Print usage message [default false]
 *  -v --version=<boolean>            - Print program version [default false]
 *
 * Execution Options:
 *  -s --simulate=<boolean>           - Simulate [default false]
 *  -e --emulate=<boolean>            - Emulate [default false]
 *  -n --nodeClass=<string>           - Node class to use [default ]
 *  --trawlerHostname=<string>        - Trawler hostname [default ]
 *  --trawlerPort=<int>               - Trawler port [default -1]
 *  -p --localUDPPort=<int>           - Local UDP port [default -1]
 *  -r --seed=<long>                  - Random seed
 *  -c --commandFile=<string>         - Command file [default ]
 *  -f --failureLvlInt=<int>          - Failure level, a number between 0 and 4 [default 4]
 *
 * Synoptic Options:
 * -l --synopticLogFilename=<string> - Synoptic log filename [default ]
 *
 * </pre>   
 */
public class MessageLayer {

	/**
	 * The current version.
	 */
	public static final String versionString = "v0.1";

	////////////////////////////////////////////////////
	/**
	 * Print the usage message.
	 */
	@OptionGroup("General Options")
	@Option(value="-h Print usage message", aliases={"-help"})
	public static boolean help = false;

	/**
	 * Print the current version.
	 */
	@Option(value="-v Print program version", aliases={"-version"})
	public static boolean version = false;
	// end option group "General Options"


	////////////////////////////////////////////////////
	/**
	 * Perform simulation
	 */
	@OptionGroup("Execution Options")
	@Option(value="-s Simulate", aliases={"-simulate"})
	public static boolean simulate = false;

	/**
	 * Perform emulation
	 */
	@Option(value="-e Emulate", aliases={"-emulate"})
	public static boolean emulate = false;

	/**
	 * Node class to use for simulation\emulation
	 */
	@Option(value="-n Node class to use", aliases={"-node-cls"})
	public static String nodeClass = "";

	/**
	 * Trawler hostname
	 */
	@Option(value="Router hostname", aliases={"-router-host"})
	// TODO: specify a sane default        
	public static String routerHostname = "";

	/**
	 * Trawler port
	 */
	@Option(value="Router port", aliases={"-router-port"})
	// TODO: specify a sane default    
	public static int routerPort = -1;

	/**
	 * Seed to use
	 */
	@Option(value="-r Random seed", aliases={"-rand-seed"})
	public static Long seed = null;

	/**
	 * Command file
	 */
	@Option(value="-c Command file", aliases={"-command-file"})
	public static String commandFile = "";

	/**
	 * Failure level setting
	 */
	@Option(value="-f Failure level, a number between 0 and 4", aliases={"-failure-lvl"})
	public static int failureLvlInt = 4;
	// end option group "Execution Options"


	////////////////////////////////////////////////////
	/**
	 * The log filename for synoptic output
	 */
	@OptionGroup("Synoptic Options")
	@Option(value="-l Synoptic log filename", aliases={"-synoptic-logfile"})
	// TODO: specify a sane default
	public static String synopticLogFilename = "";
	// end option group "Synoptic Options"


	/** One line synopsis of usage */
	private static String usage_string
	= "java MessageLayer [options]";


	/**
	 * Prints out an warning message
	 * 
	 * @param msg warning msg string
	 */
	public static void printWarning(String msg) {
		System.err.println("Warning: " + msg);
	}

	/**
	 * Prints out an error message
	 * 
	 * @param msg error msg string
	 */
	public static void printError(String msg) {
		System.err.println("Error: " + msg);
	}


	/**
	 * The main method. Entry point to start a Manager
	 */
	public static void main(String[] args) {
		// this directly sets the static member options of the Main class
		Options options = new Options (usage_string, MessageLayer.class);
		
		@SuppressWarnings("unused")
		String[] cmdLineArgs = options.parse_or_usage(args);

		if (help) {
			options.print_usage();
			return;
		}

		if (version) {
			System.out.println(MessageLayer.versionString);
			return;
		}

		if (!simulate && !emulate) {
			printError("you must specify either -s or -e.");
			return;
		}

		if (nodeClass == "") {
			printError("you must specify a node class with -n.");
			return;
		}

		if (synopticLogFilename == "") {
			printWarning("you did not specify a synoptic log file.");
		} else {
			System.out.println("synopticLogFilename = " + synopticLogFilename);
		}

		FailureLvl failureLvl = FailureLvl.EVERYTHING;
		if (failureLvlInt == -1) {
			printWarning("you did not specify a failure level with -failure-lvl. Using failure-lvl 4 = EVERYTHING");
		} else {
			FailureLvl[] possibleFailureLvls = {
					FailureLvl.NOTHING,    // 0
					FailureLvl.CRASH,      // 1
					FailureLvl.DROP,       // 2
					FailureLvl.DELAY,      // 3
					FailureLvl.EVERYTHING, // 4
			};
			failureLvl = possibleFailureLvls[failureLvlInt];	
		}


		try {
			Manager manager = null;

			URLClassLoader nodeLoader = new URLClassLoader(new URL[] {ClassLoader.getSystemResource("../proj/")}); //FIXME: is this really looking in the right place? It seems to work
			Class<? extends Node> nodeImpl = nodeLoader.loadClass(nodeClass).asSubclass(Node.class);

			if (simulate) {
				try {
					if(!commandFile.equals("")){
						manager = new Simulator(nodeImpl, failureLvl, commandFile, seed);
					} else {
						manager = new Simulator(nodeImpl, failureLvl, seed);
					}
				} catch (IllegalArgumentException e) {
					printError("Illegal arguments given to Simulator. Exception: " + e);
					return;
				} catch (FileNotFoundException e) {
					printError("Incorrect command file name given to Simulator. Exception: " + e);		    
					return;
				}


			} else { //emulate
				if (routerHostname == "" || routerPort == -1) {
					printError("for an emulation you must specify router hostname/port");
					return;
				}

				try {
					if (!commandFile.equals("")) {
						manager = new Emulator(nodeImpl, routerHostname, routerPort, failureLvl, commandFile, seed);
					} else {
						manager = new Emulator(nodeImpl, routerHostname, routerPort, failureLvl, seed);
					}
				} catch(UnknownHostException e) {
					printError("Router host name is unkown! Exception: " + e);
					return;
				} catch(IOException e) {
					printError("Encountered exception while trying to connect to Router. Exception " + e);
					return;
				} catch(IllegalArgumentException e) {
					printError("Illegal arguments given to Emulator. Exception: " + e);
					return;
				}
			}

			manager.start();

		} catch(Exception e) {
			printError("Exception occured in MessageLayer!! Exception: " + e);
			e.printStackTrace();
		}
	}
}
