package edu.washington.cs.cse490h.lib;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import edu.washington.cs.cse490h.lib.Manager;

/**
 * Node -- Class defining the interface and basic functionality of a node. The
 * student should extend this class to provide additional functionality. The
 * visibility of each of the methods/variables are on purpose.
 * 
 * This code must be written as a state machine -- each upcall must do its work
 * and return so that other upcalls can be delivered
 */
public abstract class Node {
	/**
	 * Failure rate functions that designate probability of an event happening.
	 * The student may hide these by implementing static methods with the same
	 * signature in their own node class. 0<=p<=1.
	 */
	public static double getFailureRate() {	return 5/100.0; }
	public static double getRecoveryRate(){ return 10/100.0; }
	public static double getDropRate(){ return 10/100.0; }
	public static double getDelayRate(){ return 25/100.0; }

	/**
	 * Special error that is thrown when stop() is called. It is an unchecked
	 * exception, which allows us to interrupt execution of the node opaquely.
	 * This extends Error rather than RuntimeException in case some student
	 * tries to catch Exception.
	 * 
	 * The student should NOT try to catch this or Error, Throwable, etc.
	 */
	class NodeCrashException extends Error {
		private static final long serialVersionUID = 1418673528976798283L;
	}
	
	// this node's local vector time
	// note: ArrayList data structure is not synchronized!
	private static ArrayList<Integer> localVectorTime = null;
	
	// In case the student's implementation needs to know this.
	// TODO: remove numNodes
	private static int numNodes;
	public final static int getNumNodes(){
		return numNodes;
	}
	static final void setNumNodes(int num){
		numNodes = num;
	}
	
	// Set the timeout of all timer interrupts.
	private long timeout;
	public final long getTimeout() {
		return timeout;
	}
	public final void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	private Manager manager;
	public int addr;

	/**
	 * Called by the manager to initialize certain variables. Students should
	 * not worry about this method. We do this here, rather than in the
	 * constructor in order to avoid passing a manager to the student's
	 * implementation
	 * 
	 * @param manager
	 *            The manager for the current simulation/emulation
	 * @param addr
	 *            Address of the new node
	 */
	final void init(Manager manager, int addr){
		this.manager = manager;
		this.addr = addr;
		setTimeout(4);
		localVectorTime = new ArrayList<Integer>();
		// create an empty localVectorTime
		for (int i = 0; i < numNodes; i++)
			localVectorTime.add(0);
	}

	/**
	 * Called by the manager to start this node up.
	 */
	public abstract void start();

	/**
	 * Stop the node and don't return. Please make sure to call this at the end
	 * of any overriding stop.
	 * 
	 * This method should only be used for logging purposes, since you are not
	 * guaranteed that it will be called for each crash (especially in an
	 * emulation)
	 */
	public void stop() {
		throw new NodeCrashException();
	}

	/**
	 * Called by the manager when a packet has arrived for this node
	 * 
	 * @param from
	 *            The address of the node that has sent this message
	 * @param protocol
	 *            The protocol identifier of the message
	 * @param msg
	 *            The serialized version of the message
	 */
	public abstract void onReceive(Integer from, int protocol, byte[] msg);

	/**
	 * Called by the manager when there is a command for this node from the
	 * user or a file.
	 * 
	 * @param command
	 *            The command for this node
	 */
	public abstract void onCommand(String command);

	/**
	 * This method should be the one called to send a message.
	 * 
	 * @param destAddr
	 *            The address of the destination
	 * @param pkt
	 *            The serialized form of the packet
	 */
	public void send(int destAddr, int protocol, byte[] payload) {
		Packet newPkt = new Packet(destAddr, addr, protocol, payload);
		try {
			manager.sendPkt(this, destAddr, newPkt.pack());
		}catch(IllegalArgumentException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Adds a timer interrupt for the current node. If timeout is 0, it just
	 * invokes the method
	 * 
	 * @param cb
	 *            The callback object that should be invoked when the interrupt
	 *            fires
	 */
	public void addTimeout(Callback cb){
		if(getTimeout() <= 0) {
			// if the timeout is less than or equal to 0, just invoke the callback
			// TODO: synoptic logging here
			try{
				cb.invoke();
			}catch(InvocationTargetException e) {
				Throwable t = e.getCause();
				if(t == null) {
					e.printStackTrace(System.err);
				} else {
					t.printStackTrace(System.err);
				}
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		} else {
			manager.addTimeout(this, getTimeout(), cb);
		}
	}
	
	/**
	 * Gets a PersistentStorageReader object for the filename specified.
	 * 
	 * @param filename
	 *            The file to open for reading
	 * @return A PersistentStorageReader that can read the filename
	 * @throws FileNotFoundException
	 *             If the file does not exist or can't be opened for reading
	 */
	public PersistentStorageReader getReader(String filename) throws FileNotFoundException{
		return new PersistentStorageReader(this, filename);
	}
	
	/**
	 * Gets a persistentStorageWriter object for the filename specified
	 * 
	 * @param filename
	 *            The file to open for writing
	 * @param append
	 *            Whether to append to the end of the file, or start at the
	 *            beginning
	 * @return A PersistentStorageWriter that can write to the file
	 * @throws IOException
	 *             If the file cannot be opened for writing
	 */
	public PersistentStorageWriter getWriter(String filename, boolean append) throws IOException{
		if(!Utility.fileExists(this, filename)){
			checkWriteCrash("creation of " + filename);
		}
		Utility.mkdirs(addr);
		File f = new File(Utility.realFilename(addr, filename));
		return new PersistentStorageWriter(this, f, append);
	}
	
	
	/**
	 * Called before any modification of persistent storage. Tells the manager
	 * to check whether we should crash or not.
	 * 
	 * @param description
	 *            Helpful description of the operation that is being attempted.
	 *            This is mostly to aid in debugging and user-specified crashes.
	 */
	void checkWriteCrash(String description) {
		manager.checkWriteCrash(this, description);
	}
	
	@Override
	public String toString(){
		return "addr: " + addr;
	}
	
	/**
	 * Returns a unique string identifier for this node that can be used
	 * in synoptic log files.
	 * 
	 * @return unique string identifying this node
	 */
	final public String toSynopticString() {
		return "" + addr;
	}
}
