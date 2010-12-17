package edu.washington.cs.cse490h.lib;

/**
 * <pre>   
 * Parser for the Emulator
 * Emulator is interested in all commands except topology commands
 * </pre>   
 */
//FIXME: make this make sense, ignore stuff that isnt for me 
public class EmulationCommandsParser extends CommandsParser {
	/**
	 * Process one line of topology file or keyboard input.
	 * @param line A command line.
	 * @param now The current time in microseconds
	 * @return How long to defer further processing. Returns -1 if do not have to defer
	 */
	protected Event parseLine(String line) {
		line = line.trim();
		
		if(skipLine(line)) {
			return null;
		}

		String[] cmd = line.split("\\s+");

		return parseCommonCmds(cmd);
	}  

	protected Event parseNodeCmd(String[] cmd) {
		String msg = "";
		for(int i = 0; i < cmd.length; i++) {
			msg += cmd[i] + " ";
		}
		// remove last space added by above loop
		msg = msg.substring(0, msg.length() - 1);
		
		// Node addr does not matter for emulator
		return new Event(-1, msg.toString());
	}
}
