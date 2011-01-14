import edu.washington.cs.cse490h.lib.Node;
import edu.washington.cs.cse490h.lib.Utility;


public class TwoGenerals extends Node {
	@Override
	public void start() {
	}

	@Override
	public void onReceive(Integer from, int protocol, byte[] msg) {
		String msgString = Utility.byteArrayToString(msg);

		printOutput("received " + msgString + " let's let the other general know we got it");
		send(from, 0, Utility.stringToByteArray("ack " + msgString));
	}

	@Override
	public void onCommand(String command) {
		if(matchProposeCommand(command)){
			return;
		}

		System.err.println("Unrecognized command: " + command);
	}

	private boolean matchProposeCommand(String command) {
		if(!command.equals("propose")){
			return false;
		}

		String decision;

		if (Utility.getRNG().nextBoolean()) {
			long time = Utility.getRNG().nextLong();
			decision = "attack at " + time;
		} else {
			decision = "retreat";
		}

		printOutput("Proposing to " + decision);
		send(addr == 0? 1 : 0, 0, Utility.stringToByteArray(decision));
		return true;
	}
	
	public void printOutput(String output) {
		System.out.println("Node " + addr + ": " + output);
	}
}
