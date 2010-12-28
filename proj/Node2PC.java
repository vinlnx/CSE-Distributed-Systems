import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Random;

import edu.washington.cs.cse490h.lib.Callback;
import edu.washington.cs.cse490h.lib.Node;
import edu.washington.cs.cse490h.lib.PersistentStorageReader;
import edu.washington.cs.cse490h.lib.PersistentStorageWriter;
import edu.washington.cs.cse490h.lib.Utility;

/**
 * <pre>   
 * Node -- Class defining the data structures and code for the
 * protocol stack for a node participating in the fishnet

 * This code is under student control; we provide a baseline
 * "hello world" example just to show how it interfaces with the FishNet classes.
 *
 * NOTE: per-node global variables are prohibited.
 *  In simulation mode, we execute many nodes in parallel, and thus
 *  they would share any globals.  Please put per-node state in class Node
 *  instead.
 *
 * In other words, all the code defined here should be generic -- it shouldn't
 * matter to this code whether it is executing in a simulation or emulation
 * 
 * This code must be written as a state machine -- each upcall must do its work and return so that 
 * other upcalls can be delivered
 * </pre>   
 */
public class Node2PC extends Node{
	// override the default failure rates 
	public static double getFailureRate(){ return 5/100.0; }
	public static double getRecoveryRate(){ return 5/100.0; }
	public static double getDropRate(){ return 5/100.0; }
	public static double getDelayRate(){ return 10/100.0; }
	
	//TODO: should we do something smarter for NUM_NODES?
	public static int NUM_NODES = 3;
	public static int TIMEOUT = 4;
	
	// 2PC state
	private Decision vote;
	private Decision decide;
	private int votesReceived;
	private State currentState;
	private HashMap<Integer, Decision> votes;
	
	// persistent storage
	PersistentStorageWriter log;
	
	enum State { REQWAIT, VOTEWAIT, DECISIONWAIT, FINISHED };
	enum Decision { COMMIT, ABORT, UNDECIDED };
	
	/**
	 * Create a new node and initialize everything
	 */
	public Node2PC() {
		Random rand = new Random();
		if(rand.nextDouble() <= .95){
			vote = Decision.COMMIT;
		}else{
			vote = Decision.ABORT;
		}
		//vote = rand.nextBoolean() ? Decision.COMMIT : Decision.ABORT;
		decide = Decision.UNDECIDED;
		votesReceived = 0;
		currentState = State.REQWAIT;
		votes = null;
	}

	/**
	 * Called by the manager to start this node up.
	 */
	public void start() {
		try {
			if(!Utility.fileExists(this, "log")) {
				// First start of node
				log = getWriter("log", false);
				logOutput("Started fresh and going to vote: " + vote);
				add2PCTimeout(0);
			}else {
				// We are a recovering node
				logOutput("Recovered. Checking logs...");
				recoverWithLog();
				
				log = getWriter("log", true);
			}
		} catch (IOException e) {
			e.printStackTrace();
			stop();
		}

	}

	/**
	 * Recover using a log saved in persistent storage. We will attempt to use
	 * the log to decide.
	 * 
	 * @throws IOException
	 */
	private void recoverWithLog() throws IOException{
		PersistentStorageReader logReader = getReader("log");

		if(addr == 0) {
			// We are the coordinator
			while(logReader.ready()) {
				String line = logReader.readLine();
				if(line.equals("COMMIT")){
					logOutput("Discovered that we previously committed...");
					decide = Decision.COMMIT;
					String message = Decision.COMMIT.toString();
					broadcast(Protocol.DECISION_PKT, Utility.stringToByteArray(message));
					stop();
				}else if(line.equals("ABORT")){
					logOutput("Discovered that we previously aborted...");
					decide = Decision.ABORT;
					String message = Decision.ABORT.toString();
					broadcast(Protocol.DECISION_PKT, Utility.stringToByteArray(message));
					stop();
				}
			}

			if (decide == Decision.UNDECIDED) {
				// We must have crashed before making a decision, which means it
				// is legal to abort
				logOutput("Did not decide yet.  Aborting...");
				decide = Decision.ABORT;
				String message = Decision.ABORT.toString();
				broadcast(Protocol.DECISION_PKT, Utility.stringToByteArray(message));
				stop();
			}
		} else {
			// We are simply a participant
			boolean votedYes = false;
			while(logReader.ready()) {
				String line = logReader.readLine();
				if(line.equals("COMMIT")) {
					logOutput("Discovered that we previously committed...");
					decide = Decision.COMMIT;
					stop();
				} else if(line.equals("ABORT")) {
					logOutput("Discovered that we previously aborted...");
					decide = Decision.ABORT;
					stop();
				} else if(line.equals("YES")) {
					votedYes = true;
				}
			}
			
			if(!votedYes) {
				// We haven't voted yes yet, which means it is legal to abort
				logOutput("Did not vote yes.  Aborting...");
				decide = Decision.ABORT;
				stop();
			} else {
				// We voted yes, but crashed before deciding.
				// That means we have no clue if the rest of the nodes aborted
				// or committed, so we need to ask for help with a termination
				// protocol
				terminationProtocol();
			}
		}
	}
	
	@Override
	public void stop() {
		logOutput("stopped with decision: " + decide);
		currentState = State.FINISHED;
		
		// Please call this at the end of stop
		super.stop();
	}

	/**
	 * Add a timeout that that triggers if a given node doesn't respond in time
	 * 
	 * @param node
	 *            The node that we are waiting for
	 */
	public void add2PCTimeout(int node) {
		try{
			Method onTimeoutMethod = Callback.getMethod("onTimeout", this, new String[]{ "java.lang.String", "java.lang.Integer" });
			addTimeout(new Callback(onTimeoutMethod, this, new Object[]{ currentState.toString(), node }), TIMEOUT);
		}catch(Exception e){
			e.printStackTrace(System.err);
		}
	}
	
	@Override
	public void onReceive(Integer from, int protocol, byte[] msg) {
		logOutput("received packet from " + from);
		
		if(currentState == State.FINISHED) {
			// If we are finished, our state won't change, but if we are the
			// coordinator, we may need to help out other nodes that are running
			// the termination protocol
			if (addr == 0 && protocol == Protocol.DECISIONREQ_PKT) {
				String message = decide.toString();
				broadcast(Protocol.DECISION_PKT, Utility.stringToByteArray(message));
			}else{
				logOutput("Ignoring the packet packet from " + from);
			}
			return;
		}

		receivePacket(from, protocol, msg);
	}
	
	/**
	 * This function is called when a timeout is triggered.
	 * 
	 * @param waitPhase
	 *            The state in which we were waiting
	 * @param waitNode
	 *            The node for which we were waiting
	 */
	public void onTimeout(String waitPhase, Integer waitNode){
		if(State.valueOf(waitPhase) != currentState){
			// If we already moved on to the next phase, we don't need this
			// message anymore
			return;
		}
		
		logOutput("Timed out in " + waitPhase + " waiting for a packet from: " + waitNode);
		
		if(addr == 0){
			// We are the coordinator
			if(!votes.containsKey(waitNode)){
				// We don't know the vote of the failed node. The failed node
				// may have wanted to abort, so we must abort.
				try{
					log.write("ABORT\n");
				}catch(IOException e) {
					logError("Failed logging 'ABORT'");
					stop();
				}
				
				decide = Decision.ABORT;
				String message = Decision.ABORT.toString();
				broadcast(Protocol.DECISION_PKT, Utility.stringToByteArray(message));
				stop();
			}
		}else{
			if(waitNode == 0){
				// The coordinator failed
				if(currentState == State.REQWAIT){
					// We are still waiting for the vote request, so we never
					// voted and can abort.
					try{
						log.write("ABORT\n");
					}catch(IOException e) {
						logError("Failed logging 'ABORT'");
						stop();
					}
					
					decide = Decision.ABORT;
					stop();
				}else if(currentState == State.DECISIONWAIT){
					// If we are in this state, then we voted yes, but never got
					// back a decision. We have to ask for help using a
					// termination protocol.
					terminationProtocol();
				}
			}
		}
	}

	@Override
	public void onCommand(String command) {
		if(matchInitVoteCommand(command)) {
			return;
		}
		
		if(matchStatusCommand(command)){
			return;
		}

		logError("Unrecognized command: " + command);
	}

	/**
	 * If the command is a status command, this method executes it, which simply
	 * prints out the status of the current node.
	 * 
	 * @param command
	 *            The command
	 * @return true if the command was a status command, false otherwise
	 */
	private boolean matchStatusCommand(String command) {
		if(!command.equals("status")){
			return false;
		}
		
		logOutput(toString());
		return true;
	}

	/**
	 * If the command is a initVote command, this method executes it, which
	 * simply starts a vote from the coordinator. This command should only be
	 * sent to node 0, and there should be at least 2 nodes participating.
	 * 
	 * @param command
	 * @return true if the command was an initVote command, false otherwise
	 */
	private boolean matchInitVoteCommand(String command) {
		if(!command.equals("initVote")){
			return false;
		}
		
		logOutput("initializing vote...");
		
		try {
			// send a vote request to all participants
			String message = "";
			broadcast(Protocol.VOTEREQ_PKT, Utility.stringToByteArray(message));
			
			// wait for all of the participants to respond
			currentState = State.VOTEWAIT;
			//This doesn't work if we're the only one alive
			for(int i = 1; i < NUM_NODES; ++i){
				add2PCTimeout(i);
			}
			votes = new HashMap<Integer, Decision>();
			return true;
		}catch(Exception e) {
			logError("Exception: " + e);
			e.printStackTrace();
		}

		return true;
	}

	/**
	 * Actually process a packet.
	 * 
	 * @param from
	 *            The address of the sender
	 * @param protocol
	 *            The protocol identifier of the message
	 * @param msg
	 *            The message object that was sent
	 */
	private void receivePacket(int from, int protocol, byte[] msg) {
		String message;
		
		switch(protocol) {
		// Packets we should receive as coordinator
		case Protocol.VOTE_PKT:
			Decision participantVote = Decision.valueOf( Utility.byteArrayToString(msg) );
			votes.put(from, participantVote);
			
			if (participantVote == Decision.ABORT) {
				// Someone voted abort
				logOutput("Deciding to abort...");
				try {
					log.write("ABORT\n");
				} catch (IOException e) {
					logError("Failed logging 'ABORT'");
					stop();
				}
				decide = Decision.ABORT;
				message = Decision.ABORT.toString();
				broadcast(Protocol.DECISION_PKT, Utility.stringToByteArray(message));
				stop();
			} else if (++votesReceived == NUM_NODES - 1) {
				// Everyone voted commit
				logOutput("Deciding to commit...");
				try {
					log.write("COMMIT\n");
				} catch (IOException e) {
					logError("Failed logging 'COMMIT'");
					stop();
				}
				decide = Decision.COMMIT;
				message = Decision.COMMIT.toString();
				broadcast(Protocol.DECISION_PKT, Utility.stringToByteArray(message));
				stop();
			}
			break;
			
		case Protocol.DECISIONREQ_PKT:
			if(decide == Decision.UNDECIDED) {
				// A participant must have timed out on our vote request.
				logOutput("Deciding to abort...");
				decide = Decision.ABORT;
				message = Decision.ABORT.toString();
				broadcast(Protocol.DECISION_PKT, Utility.stringToByteArray(message));
				stop();
			}
			// If we've already decided, we send help in onReceive
			break;
			
		// Packets we should receive as a participant
		case Protocol.VOTEREQ_PKT:
			message = vote.toString();
			send(from, Protocol.VOTE_PKT, Utility.stringToByteArray(message));
			
			if(vote == Decision.ABORT){
				logOutput("Deciding to abort...");
				try{
					log.write("ABORT\n");
				}catch(IOException e) {
					logError("Failed logging 'ABORT'");
					stop();
				}
				decide = Decision.ABORT;
				stop();
			}else{
				try{
					log.write("YES\n");
				}catch(IOException e) {
					logError("Failed logging 'YES'");
					stop();
				}
				currentState = State.DECISIONWAIT;
				add2PCTimeout(0);
			}
			break;
			
		case Protocol.DECISION_PKT:
			Decision coordinatorDecision = Decision.valueOf( Utility.byteArrayToString(msg) );
			logOutput("Deciding to " + coordinatorDecision.toString() + "...");
			try{
				log.write(coordinatorDecision.toString() + '\n');
			}catch(IOException e) {
				logError("Failed logging '" + coordinatorDecision.toString() + "'");
				stop();
			}
			
			decide = coordinatorDecision;
			
			stop();
			break;

		default:
			logError("Packet with unknown protocol received. Protocol: " + protocol);	    
		}
	}
	
	/**
	 * Runs a termination protocol that asks for help from other nodes. This
	 * specific implementation is very simple and continuously polls the
	 * coordinator for a decision.
	 */
	//TODO: even if i was manager, i may restart with a different address
	public void terminationProtocol() {
		String message = "";
		send(0, Protocol.DECISIONREQ_PKT, Utility.stringToByteArray(message));
		add2PCTimeout(0);
	}
	
	@Override
	public String toString(){
		String s;
		if(addr == 0){
			s = "(Coordinator) addr: " + addr + ", state: " + currentState + ", decision: " + decide;
		}else{
			s = "addr: " + addr + ", vote: " + vote + ", state: " + currentState + ", decision: " + decide;
		}
		return s;
	}
	
	public void logError(String output) {
		log(output, System.err);
	}

	public void logOutput(String output) {
		log(output, System.out);
	}

	public void log(String output, PrintStream stream) {
		stream.println("Node " + addr + ": " + output);
	}
}
