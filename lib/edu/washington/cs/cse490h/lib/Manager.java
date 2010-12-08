package edu.washington.cs.cse490h.lib;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Random;

/**
 * <pre>   
 * Abstract class defining generic routines for running network code under MessageLayer
 * </pre>   
 */
public abstract class Manager {
	final Class<? extends Node> nodeImpl;
    final double failureRate;
    final double recoveryRate;
    final double dropRate;
    final double delayRate;
	
    protected Random randNumGen;
    protected long seed;
    
	private int pktsSent;
	protected ArrayList<Event> sortedEvents;
	protected CommandsParser parser;   // parser for commands file

	protected final BufferedReader keyboard;
	
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
	
	class Timeout{
		int addr;
		long fireTime;
		Callback cb;
		
		Timeout(int addr, long fireTime, Callback cb) {
			this.addr = addr;
			this.fireTime = fireTime;
			this.cb = cb;
			
		}
		
		public String toString() {
			return addr + ": " + cb + " at " + fireTime;
		}
	}
	private long time;
	ArrayList<Timeout> waitingTOs;
	
	/**
	 * Initialize Manager. 
	 * @param time Starting time in microseconds
	 */
	protected Manager(Class<? extends Node> nodeImpl) throws IllegalArgumentException{
		this.pktsSent = 0;
		this.sortedEvents = new ArrayList<Event>();
		this.parser = null;
		
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
		
		keyboard = new BufferedReader(new InputStreamReader(System.in));
	}

	/**
	 * Starts the Manager. This runs in an infinite loop until network is stopped
	 * Instantiates nodes and gets them running
	 */
	public abstract void start();

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
	public void stop() {
		System.out.println(stopString());
		System.exit(0);
	}

	/**
	 * Send the pkt to the specified node
	 * @param from	The node that is sending the packet
	 * @param to	Integer specifying the destination node
	 * @param pkt	The packet to be sent, serialized to a byte array
	 * @return True	if the packet was sent, false otherwise
	 * @throws IllegalArgumentException	If the arguments are invalid
	 */
	public void sendPkt(int from, int to, byte[] pkt) throws IllegalArgumentException {
		if ( (pkt.length > Packet.MAX_PACKET_SIZE)
			|| !Packet.validAddress(to)
			|| !Packet.validAddress(from)
			|| !Packet.unpack(pkt).isValid()) {

			throw new IllegalArgumentException("Either pkt is not valid, address is not valid, or TTL is not valid");
		}
		pktsSent++;
	}

	protected void setParser(CommandsParser parser) {
		this.parser = parser;
	}
	
	protected void addTimeout(int addr, long timeout, Callback cb) {
		waitingTOs.add(new Timeout(addr, now() + timeout, cb));
	}
	
	public long now(){
		return time;
	}
	
	protected abstract void checkWriteCrash(Node n, String description);
	
	protected void setTime(long time){
		this.time = time;
	}
}
