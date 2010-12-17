package edu.washington.cs.cse490h.lib;

import java.net.UnknownHostException;
import java.io.IOException;
import java.lang.Integer;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

import edu.washington.cs.cse490h.lib.Node.NodeCrashException;

//TODO: deterministic replay! maybe wait for and use synoptic logging with message dumps

/**
 * Manager that runs under a single emulated node on the client machine.
 */
public class Emulator extends Manager {
	private Node node;
	private NodeServer server;
	private int address;

	public Emulator(Class<? extends Node> nodeImpl, String trawlerName, int trawlerPort, Long seed) throws IOException, IllegalArgumentException{
		super(nodeImpl);
		
		setParser(new EmulationCommandsParser());
		
		server = new NodeServer(trawlerName, trawlerPort, this);
		address = server.getAddress();

		if(address == Packet.BROADCAST_ADDRESS) {
			// Router returns broadcast address to signal there's already someone using the local port
			System.err.println("Address/Port is already in use. Pick another");
			throw new IllegalArgumentException("Illegal local port");
		}
		
		if(seed == null){
			this.seed = System.currentTimeMillis();
		}else{
			this.seed = seed;
		}
		System.out.println("Starting simulation with seed: " + this.seed);
		randNumGen = new Random(this.seed);
		
		try{
			node = nodeImpl.newInstance();
		}catch(Exception e){
			throw new IllegalArgumentException("Error while constructing node: " + e);
		}
		node.init(this, address);
		Node.setNumNodes(3); //FIXME: reprogram 2PC for this!!!
		
		server.start();
		
		setTime(System.currentTimeMillis());
	}

	/**
	 * Create a new emulator that takes commands through user input
	 * 
	 * @param nodeImpl
	 *            The Class object for the student's node implementation
	 * @param routerName
	 *            Name of the machine that the Router is on
	 * @param routerPort
	 *            The port that the Router is listening on
	 * @param failureGen
	 *            How failures should be generated
	 * @param seed
	 *            Seed for the RNG. This can be null if the failure generator is
	 *            not a RNG
	 * 
	 * @throws UnknownHostException
	 *             If the router's name cannot be resolved
	 * @throws IOException
	 *             If there is an error in writing to the TCP socket
	 * @throws IllegalArgumentException
	 *             If the local port given is already in use
	 */
	public Emulator(Class<? extends Node> nodeImpl, String routerName,
			int routerPort, FailureLvl failureGen, Long seed)
			throws UnknownHostException, IOException, IllegalArgumentException {
		this(nodeImpl, routerName, routerPort, seed);

		cmdInputType = InputType.USER;
		userControl = failureGen;
	}
	
	/**
	 * Create a new emulator that takes commands through a file
	 * 
	 * @param nodeImpl
	 *            The Class object for the student's node implementation
	 * @param routerName
	 *            Name of the machine that the Router is on
	 * @param routerPort
	 *            The port that the Router is listening on
	 * @param failureGen
	 *            How failures should be generated
	 * @param commandFile
	 *            File containing the list of commands
	 * @param seed
	 *            Seed for the RNG. This can be null if the failure generator is
	 *            not a RNG
	 * 
	 * @throws UnknownHostException
	 *             If the router's name cannot be resolved
	 * @throws IOException
	 *             If there is an error in writing to the TCP socket
	 * @throws IllegalArgumentException
	 *             If the local port given is already in use
	 */
	public Emulator(Class<? extends Node> nodeImpl, String routerName,
			int routerPort, FailureLvl failureGen, String commandFile,
			Long seed) throws UnknownHostException, IOException,
			IllegalArgumentException {
		this(nodeImpl, routerName, routerPort, seed);

		cmdInputType = InputType.FILE;
		userControl = failureGen;

		EmulationCommandsParser commandFileParser = new EmulationCommandsParser();
		sortedEvents = commandFileParser.parseFile(commandFile);
	}
	
	/**
	 * Starts the emulated node
	 */
	@Override
	public void start() {
		try{
			node.start();
		}catch(NodeCrashException e) { }
		
		if(cmdInputType == InputType.FILE) {
			while(!inTransitMsgs.isEmpty() || !sortedEvents.isEmpty() || !waitingTOs.isEmpty()) {
				System.out.println("\nTime: " + now());
				
				ArrayList<Event> currentRoundEvents = new ArrayList<Event>();
				
				// If a message was delivered successfully and therefore changed the state,
				// in transit messages should be checked again.
				//		Ex: In order to deliver a sequence of messages in reverse order, we need to
				//		recursively delay everything except the last message.
				checkInTransit(currentRoundEvents);
				
				boolean advance = false;
				do{
					if(sortedEvents.isEmpty()){
						advance = true;
					}else{
						Event ev = sortedEvents.remove(0);
						if(ev.t == Event.EventType.TIME) {
							advance = true;
						} else {
							currentRoundEvents.add(ev);
						}
					}
				}while(!advance);
				
				checkCrash(currentRoundEvents);
				
				checkTimeouts(currentRoundEvents);
				
				executeEvents(currentRoundEvents);
				
				// do a sleep and then currentTimeMillis is what we want
				setTime(now()+1);
			}
		}else if(cmdInputType == InputType.USER) {
			while(true){
				System.out.println("\nTime: " + now());
				
				ArrayList<Event> currentRoundEvents = new ArrayList<Event>();
				
				checkInTransit(currentRoundEvents);

				// just in case an exception is thrown or input is null
				Event ev;
				boolean advance = false;
				System.out.println("Please input a sequence of commands terminated by a blank line or the TIME command:");

				//TODO: think about what order to do this in. Especially since it blocks on user input
				
				do{
					// just in case an exception is thrown or input is null
					ev = null;

					try{
						// A command will be converted into an Event and passed to the node later in the loop
						// A quit command will be matched later in this try block
						// Empty/whitespace will be treated as a skipped line, which will return null and cause a continue
						String input = keyboard.readLine();
						// Process user input if there is any
						if(input != null) {
							ev = parser.parseLine(input);
						}
					}catch(IOException e){
						System.err.println("Error on user input: " + e);
					}

					if(ev == null){
						advance = true;
					}else{
						if(ev.t == Event.EventType.TIME) {
							advance = true;
						} else {
							currentRoundEvents.add(ev);
						}
					}
				}while(!advance);

				checkCrash(currentRoundEvents);

				checkTimeouts(currentRoundEvents);

				executeEvents(currentRoundEvents);

				setTime(now()+1);
			}
		}
	}
	
	private void checkInTransit(ArrayList<Event> currentRoundEvents) {
		// Load in all the newly received messages
		Packet pkt = server.getPacket();
		while(pkt != null) {
			inTransitMsgs.add(pkt);
			pkt = server.getPacket();
		}
		
		if(inTransitMsgs.isEmpty()){
			return;
		}
		
		// See what we should do with all the in-transit messages
		ArrayList<Packet> currentPackets = inTransitMsgs;
		inTransitMsgs = new ArrayList<Packet>();
		
		if(userControl.compareTo(FailureLvl.DROP) < 0){		// userControl < DROP
			// Figure out if we need to drop the packet.
			Iterator<Packet> iter = currentPackets.iterator();
			while(iter.hasNext()) {
				Packet p = iter.next();
				double rand = randNumGen.nextDouble();
				if(rand < dropRate){
					System.out.println("Randomly dropping: " + p.toString());
					iter.remove();
				}
			}
		}else{
			System.out.println("The following messages are in transit: ");
			for(int i = 0; i < currentPackets.size(); ++i){
				System.out.println(i + ": " + currentPackets.get(i).toString());
			}

			try{
				System.out.println("Which should be dropped? (space delimited list or just press enter to drop none)");
				String input = keyboard.readLine().trim();
				// hash set so we don't have to deal with duplicates
				HashSet<Packet> toBeRemoved = new HashSet<Packet>();
				
				if(!input.equals("")){
					String[] dropList = input.split("\\s+");
					for(String s: dropList){
						toBeRemoved.add( currentPackets.get(Integer.parseInt(s)) );
					}
				}
				
				if(toBeRemoved.size() == currentPackets.size()){
					return;
				}
				
				// If user drops and delays the same packet, result is undefined
				//   In current implementation, delay takes precedence
				if(userControl.compareTo(FailureLvl.DELAY) >= 0){		// userControl >= DELAY
					System.out.println("Which should be delayed? (space delimited list or just press enter to delay none)");
					input = keyboard.readLine().trim();
					
					if(!input.equals("")){
						String[] delayList = input.split("\\s+");
						for(String s: delayList){
							Packet p = currentPackets.get(Integer.parseInt(s));
							inTransitMsgs.add(p);
							toBeRemoved.add(p);
						}
					}
					
					if(toBeRemoved.size() == currentPackets.size()){
						return;
					}
				}
				
				currentPackets.removeAll(toBeRemoved);
			}catch(IOException e){
				e.printStackTrace();
			}
		}
		
		if(userControl.compareTo(FailureLvl.DELAY) < 0){		// userControl < DELAY
			Iterator<Packet> iter = currentPackets.iterator();
			while(iter.hasNext()) {
				Packet p = iter.next();
				double rand = randNumGen.nextDouble();
				// adjust the probability since these are not independent events
				//   Ex: 50% drop rate and 50% delay rate should mean that nothing gets through
				double adjustedDelay = delayRate / (1 - dropRate);
				if(rand < adjustedDelay){
					System.out.println("Randomly Delaying: " + p.toString());
					iter.remove();
					inTransitMsgs.add(p);
				}
			}
		}
		
		for(Packet p: currentPackets) {
			currentRoundEvents.add(new Event(p));
		}
	}
	
	private void checkCrash(ArrayList<Event> currentRoundEvents){
		// See if we should crash.
		// TODO: maybe add recoveries???
		// Failures and restarts specified in the file are deprecated
		if(userControl.compareTo(FailureLvl.CRASH) < 0){
			int rand = randNumGen.nextInt(100);
			if(rand < failureRate){
				currentRoundEvents.add(new Event(address, Event.EventType.FAILURE));
			}
		}else{
			try{
				System.out.println("Crash? (y/n)");
				String input = keyboard.readLine();
				if(input.charAt(0) == 'y'){
					currentRoundEvents.add(new Event(address, Event.EventType.FAILURE));
				}
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Check to see if any timeouts are supposed to fire during the current
	 * time step
	 */
	private void checkTimeouts(ArrayList<Event> currentRoundEvents) {
		ArrayList<Timeout> currentTOs = waitingTOs;
		waitingTOs = new ArrayList<Timeout>();
		
		Iterator<Timeout> iter = currentTOs.iterator();
		while(iter.hasNext()) {
			Timeout to = iter.next();
			if(now() >= to.fireTime) {
				iter.remove();
				currentRoundEvents.add(new Event(to));
			}
		}
		
		waitingTOs.addAll(currentTOs);
	}
	
	/**
	 * Reorders and executes all the events for the current round.
	 * 
	 * Note that commands can be executed in a different order than they appear
	 * in the command file!
	 * 
	 * @param currentRoundEvents
	 */
	private void executeEvents(ArrayList<Event> currentRoundEvents) {
		if(userControl == FailureLvl.EVERYTHING){
			boolean doAgain = false;
			do{
				try{
					for(int i = 0; i < currentRoundEvents.size(); ++i){
						System.out.println(i + ": " + currentRoundEvents.get(i).toString());
					}
					System.out.println("In what order should the events happen? (enter for in-order)");
					String input = keyboard.readLine().trim();

					if(input.equals("")){
						for(Event ev: currentRoundEvents){
							handleEvent(ev);
						}
					}else{
						String[] order = input.split("\\s+");

						HashSet<Event> dupeMissCheck = new HashSet<Event>();
						for(String s: order){
							dupeMissCheck.add(currentRoundEvents.get(Integer.parseInt(s)));
						}
						
						if(dupeMissCheck.size() != currentRoundEvents.size()) {
							System.out.println("Not all of the events were specified!");
							doAgain = true;
							continue;
						}
						
						for(String s: order){
							Event ev = currentRoundEvents.get(Integer.parseInt(s));
							handleEvent(ev);
						}
					}
				}catch(IOException e){
					e.printStackTrace();
					doAgain = true;
				}
			}while(doAgain);
		}else{
			Collections.shuffle(currentRoundEvents, randNumGen);
			System.out.println("Executing with order: ");
			for(Event ev: currentRoundEvents) {
				System.out.println(ev.toString());
				handleEvent(ev);
			}
		}
	}
	
	private void handleEvent(Event ev){
		switch(ev.t){
		case FAILURE:
			failNode();
			break;
		case START:
			//FIXME: NO! unless you would like to hot-restart??
			break;
		case EXIT:
			failNode();
			break;
		case COMMAND:
			sendNodeCmd(ev.command);
			break;
		case ECHO:
			parser.printStrArray(ev.msg, System.out);
			break;
		case DELIVERY:
			deliverPkt(ev.p);
			break;
		case TIMEOUT:
			logEvent(ev.to.node, "TIMEOUT " + ev.to.cb.toString());
						
			try{
				ev.to.cb.invoke();
			}catch(InvocationTargetException e) {
				Throwable t = e.getCause();
				if(t == null) {
					e.printStackTrace();
				} else if(t instanceof NodeCrashException) {
					// let it slide
				} else {
					t.printStackTrace();
				}
			}catch(IllegalAccessException e) {
				e.printStackTrace();
			}
			break;
		default:
			System.err.println("Shouldn't happen. TIME here?");
		}
	}

	/**
	 * Send the pkt to the specified node
	 * @param from The node that is sending the packet
	 * @param to Int specifying the destination node
	 * @param pkt The packet to be sent, serialized to a byte array
	 * @return True if the packet was sent, false otherwise
	 * @throws IllegalArgumentException If the arguments are invalid
	 */
	public void sendPkt(Node fromNode, int to, byte[] pkt) throws IllegalArgumentException {
		super.sendPkt(fromNode, to, pkt);  // check arguments
		
		try {
			sendToRouter(to, pkt);
		}catch(IOException e) {
			System.err.println("IOException occured while trying to send to node: " + to);
			e.printStackTrace();
			return;
		}
		return;
	}

	/**
	 * Sends the msg to the the specified node 
	 * @param nodeAddr Address of the node to whom the message should be sent
	 * @param msg The msg to send to the node
	 * @return True if msg sent, false if address is not valid
	 */
	public boolean sendNodeCmd(String msg) {
		node.onCommand(msg);
		return true;
	}

	private void sendToRouter(int destAddr, byte[] pkt) throws IOException {
		server.send(pkt);
	}

	private void deliverPkt(Packet pkt) {
		if(pkt.getDest() == address || pkt.getDest() == Packet.BROADCAST_ADDRESS) {
			try{
				node.onReceive(pkt.getSrc(), pkt.getProtocol(), pkt.getPayload());
			}catch(NodeCrashException e) { }
		}
		// drop if not for me. This can happen if we took a port that was recently occupied by another node
	}

	private void failNode(){
		try{
			node.stop();	// FIXME: don't run anything more after this
		} catch (NodeCrashException e) {
			stop();
		}

		System.err.println("Did you forget to call super.stop() in your node implementation?");
	}

	public void stop() {
		System.out.println(stopString());
		System.out.println(node.addr + ": " + node.toString());
		logEvent(node, "STOPPED");
		
		server.close();
		
		// stop the synoptic logger
		this.synLogger.stop();
		System.exit(0);
	}

	@Override
	protected void checkWriteCrash(Node n, String description) {
		if(userControl.compareTo(FailureLvl.CRASH) < 0){
			if(randNumGen.nextDouble() < failureRate) {
				System.out.println("Randomly failing before write");
				failNode();
			}
		}else{
			try{
				System.out.println("Crash before " + description + "? (y/n)");
				String input = keyboard.readLine().trim();
				if(input.length() != 0 && input.charAt(0) == 'y'){
					failNode();
				}
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}
	
	private void logEvent(Node node, String eventStr) {
		// TODO
	}
}
