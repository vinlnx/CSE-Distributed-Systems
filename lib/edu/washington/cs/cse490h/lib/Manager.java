package edu.washington.cs.cse490h.lib;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * <pre>   
 * Abstract class defining generic routines for running network code under MessageLayer
 * </pre>   
 */
public abstract class Manager {
	protected static final int BROADCAST_ADDRESS = 255;
	protected static final int MAX_ADDRESS = 255;
	
	protected final Class<? extends Node> nodeImpl;
	protected final double failureRate;
	protected final double recoveryRate;
	protected final double dropRate;
	protected final double delayRate;
	
    protected long seed;
    
	private int pktsSent;
	protected ArrayList<Event> sortedEvents;
	protected ArrayList<Timeout> waitingTOs;
	protected ArrayList<Packet> inTransitMsgs;
	protected CommandsParser parser;   // parser for commands file
	
	protected SynopticLogger synLogger = new SynopticLogger();
	
	protected FailureLvl userControl;
	protected enum FailureLvl{
		NOTHING,		// Everything is handled by the random number generator
		CRASH,			// The user only controls node crashes and restarts
		DROP,			// The user also controls message dropping
		DELAY,			// The user also controls message delays
		EVERYTHING		// The user controls everything, including message ordering
	}
	protected InputType cmdInputType;
	protected enum InputType{ USER, FILE }
	
	protected class Timeout{
		protected Node node;
		protected long fireTime;
		protected Callback cb;
		
		protected Timeout(Node node, long fireTime, Callback cb) {
			this.node = node;
			this.fireTime = fireTime;
			this.cb = cb;
			
		}
		
		public String toString() {
			return node.addr + ": " + cb + " at " + fireTime;
		}
	}
	private long time;
	
	/**
	 * Initialize Manager. 
	 * @param time Starting time in microseconds
	 */
	protected Manager(Class<? extends Node> nodeImpl, Long seed, String replayOutputFilename, String replayInputFilename) throws IllegalArgumentException, IOException{
		pktsSent = 0;
		waitingTOs = new ArrayList<Timeout>();
		inTransitMsgs = new ArrayList<Packet>();
		parser = null;
		
		this.nodeImpl = nodeImpl;
		try{
			// this block is not actually needed when the failure generator is the user, but should work anyways
			failureRate = (Double)nodeImpl.getMethod("getFailureRate", (Class<?>[])null)
									.invoke(null, (Object[])null);
			recoveryRate = (Double)nodeImpl.getMethod("getRecoveryRate", (Class<?>[])null)
									.invoke(null, (Object[])null);
			dropRate = (Double)nodeImpl.getMethod("getDropRate", (Class<?>[])null)
									.invoke(null, (Object[])null);
			delayRate = (Double)nodeImpl.getMethod("getDelayRate", (Class<?>[])null)
									.invoke(null, (Object[])null);
		}catch(NoSuchMethodException e){
			throw new IllegalArgumentException("Error while finding get*rate functions: " + e);
		}catch(Exception e){
			throw new IllegalArgumentException("Error while executing get*rate functions: " + e); 
		}
		
		Replay.parent = this;

		if(!replayOutputFilename.equals("")) {
			File f = new File(replayOutputFilename);
			if (f.exists()) {
				throw new IllegalArgumentException("Replay output file already exists");
			}
			Replay.replayOut = new DataOutputStream(new FileOutputStream(replayOutputFilename));
		} else {
			Replay.replayOut = null;
		}

		if(!replayInputFilename.equals("")) {
			this.seed = Replay.init(new DataInputStream(new FileInputStream(replayInputFilename)));
		} else {
			Replay.init(null);
			if (seed == null) {
				this.seed = System.currentTimeMillis();
			} else {
				this.seed = seed;
			}
		}
	}

	/**
	 * Starts the Manager. This runs in an infinite loop until network is stopped
	 * Instantiates nodes and gets them running
	 */
	protected abstract void start();

	protected String stopString(){
		String s = "MessageLayer exiting.\nNumber of packets sent: " + String.valueOf(pktsSent);
		if(userControl != FailureLvl.EVERYTHING){
			s += "\nRandom Seed: " + seed;
		}
		return s;
	}
	
	/**
	 * Stops MessageLayer. Normally this method should not return
	 */
	protected void stop() {
		System.out.println(stopString());
		System.exit(0);
	}

	/**
	 * Send the pkt to the specified node
	 * @param from	The node that is sending the packet
	 * @param to	Integer specifying the destination node
	 * @param pkt	The payload to be sent
	 * @return True	if the packet was sent, false otherwise
	 * @throws IllegalArgumentException	If the arguments are invalid
	 */
	protected void sendPkt(Node fromNode, int to, int protocol, byte[] payload) throws IllegalArgumentException {
		int from = fromNode.addr;
		if ( (payload.length > Packet.MAX_PAYLOAD_SIZE)
			|| !Packet.validAddress(to)
			|| !Packet.validAddress(from)) {

			throw new IllegalArgumentException("Either pkt is not valid, address is not valid, or TTL is not valid");
		}
		pktsSent++;
	}

	protected void setParser(CommandsParser parser) {
		this.parser = parser;
	}
	
	protected void addTimeout(Node node, long timeout, Callback cb) {
		waitingTOs.add(new Timeout(node, now() + timeout, cb));
	}
	
	public long now(){
		return time;
	}
	
	protected abstract void checkWriteCrash(Node n, String description);
	
	protected void setTime(long time){
		this.time = time;
	}
}
