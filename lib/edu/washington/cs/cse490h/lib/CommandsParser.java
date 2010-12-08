package edu.washington.cs.cse490h.lib;

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * <pre>
 * CommandsParser -- parses topology and keyboard commands
 * 
 * COMMANDS ARE CASE SENSITIVE. 
 * 
 * Pay attention to the spaces.
 * Commands are parsed by using the spaces as delimiters thus it is very important
 * to put spaces where required.
 *
 * The topology file and the keyboard input file have the same format;
 * all the same commands can be entered from either one.  Both
 * are line-oriented (one command per line). 
 * Nodes (e.g., a, b) are referred to by their FishnetAddress (0..254).
 *
 *	[// | #] <comment>  -- any line starting with // or # is ignored
 *	edge a b [lossRate <double>] [delay <long>] [bw <int>]
 *		-- this creates an edge between a and b, with the
 *		specified loss rate, delay (in milliseconds), and bw (in KB/s),
 *		or changes the specifics for an existing link
 *		defaults: 0 lossRate, 1 msec delay, and 10Kb/s bw
 *	time [+ ]x  -- any subsequent command is delayed until simulation/real
 *			has reached x (or now + x, if + is used), in milliseconds from start
 *                    NOTE: IF + IS USED THERE MUST BE A SPACE BETWEEN + AND x
 *	fail a [b] -- this removes node a (if b is not specified) or an edge (if it is)
 *	restart a [b]  -- this restarts a node or edge.  previous information about
 *		the node/edge is preserved
 *	echo text -- print the text 
 *	exit  -- cleanly stop the simulation/emulation run and print statistics
 *	a <msg>  -- deliver text <msg> to node a (for simulation mode only)
 *	<msg> -- deliver text <msg> to this node (for emulation mode only)
 *		Note that msg cannot start with any keyword defined above
 *
 * To avoid a race condition with respect to starting up the user protocol code,
 * the simulator will only process keyboard commands at time >  0.
 * </pre>
 */

public abstract class CommandsParser {

	private String filename;
	private BufferedReader reader;

	protected CommandsParser() {
		this.filename = null;
		this.reader = null;
	}

	/**
	 * Open and process a topology file.
	 * @param filename The name of the command file.
	 * @throws FileNotFoundException If the named filed does not exist, is a directory rather than a regular file, or
	 *                               for some other reason cannot be opened for reading     
	 */
	public ArrayList<Event> parseFile(String filename) throws FileNotFoundException {
		if(filename == null) {
			throw new FileNotFoundException("null filename");
		}
		this.filename = filename;
		reader = new BufferedReader(new FileReader(filename));
		return parse();
	}

	/**
	 * Parse the command file.
	 */
	public ArrayList<Event> parse(){
		try {
			ArrayList<Event> eventQueue = new ArrayList<Event>();
			String line;
			while((line = reader.readLine()) != null) {
				Event e = parseLine(line);
				if(e == null){
					System.err.println("not a valid line: " + line);
				}else{
					eventQueue.add(e);
				}
			}
			return eventQueue;
		}catch(IOException e) {
			System.err.println("IOException occured while trying to read file: " + filename + "\nException: " + e);
		}
		
		return null;
	}

	/**
	 * Process one line of topology file or keyboard input.
	 * @param line A command line.
	 * @param now The current time in microseconds
	 * @return How long to defer further processing. Returns -1 if do not have to defer
	 * @DEPRECATED
	 */
	protected Event parseLine(String line) {
		line = line.trim();
		
		if(skipLine(line)) {
			return null;
		}

		String[] cmd = line.split("\\s+");
		Event e;
		
		if((e = parseFail(cmd)) != null){
			return e;
		}
		
		if((e = parseStart(cmd)) != null) {
			return e;
		}

		return parseCommonCmds(cmd);
	}


	/******************** Protected Functions ********************/

	/**
	 * Call manager.stop() if the command is exit. Actually will accept exit followed by anything
	 * Thus "exit" and "exit blahblah" will both cause fishnet to exit.
	 * However "exitblahblah" will not.
	 */
	protected Event exit(String[] cmd) {
		return new Event(-1, Event.EventType.EXIT);
	}

	protected abstract Event parseNodeCmd(String[] cmd);

	/**
	 * Returns true if line should be skipped, either because its empty or is a comment
	 */
	protected boolean skipLine(String line) {
		if(line.equals("")) {
			return true;
		}
		String[] cmd = line.split("\\s+");

		if(cmd[0].startsWith("//") || cmd[0].startsWith("#")) {
			return true;
		}
		return false;
	}

	/**
	 * Parses exit, echo and node commands
	 * @param cmd
	 */
	protected Event parseCommonCmds(String[] cmd) {
		if(cmd[0].equals("exit")) {
			return exit(cmd);
		}

		if(cmd[0].equals("time")) {
			return time(cmd);
		}
		
		if(cmd[0].equals("echo")) {
			return echo(cmd);
		}
		
		return parseNodeCmd(cmd);
	}

	// These following functions are overriden by TrawlerCommandsParser so that it can notify trawler of change

	protected void printStrArray(String[] strArray, int startIndex, int endIndex, PrintStream stream) {
		if(strArray == null || stream == null) {
			return;
		}
		endIndex = Math.min(endIndex, strArray.length);
		for(int i = startIndex; i < endIndex; i++) {
			stream.print(strArray[i] + " ");
		}
		stream.println();
	}

	protected void printStrArray(String[] strArray, PrintStream stream) {
		printStrArray(strArray, 0, strArray.length, stream);
	}

	/******************** Private Functions ********************/

	/**
	 * @deprecated
	 */
	private Event parseFail(String[] cmd) {
		if(cmd[0].equals("fail")) {
			return new Event(Integer.parseInt(cmd[1]), Event.EventType.FAILURE);
		}
		return null;
	}

	private Event parseStart(String[] cmd) {
		if(cmd[0].equals("start")) {
			return new Event(Integer.parseInt(cmd[1]), Event.EventType.START);
		}
		return null;
	}

	private Event time(String[] cmd) {
		return new Event(Event.EventType.TIME, cmd);
	}
	
	// Echo string if cmd is echo
	// Return value indicates whether command was echo or not
	private Event echo(String[] cmd) {
		//printStrArray(cmd, 1, cmd.length, System.out);
		return new Event(Event.EventType.ECHO, cmd);
	}
}
