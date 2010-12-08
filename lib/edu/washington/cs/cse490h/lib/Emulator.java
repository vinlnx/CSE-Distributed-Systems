package edu.washington.cs.cse490h.lib;

import java.net.Socket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.NumberFormatException;
import java.lang.Integer;
import java.util.Random;

import edu.washington.cs.cse490h.lib.Node.NodeCrashException;

/**
 * Manages an emulated node
 */
public class Emulator extends Manager {
	private Socket trawler;
	private PrintWriter trawlerWriter;
	private BufferedReader trawlerReader;
	private DatagramSocket udpSocket;
	private int fishAddress;    
	private Node node;
	private EmulatedNodeServer server;

	public Emulator(Class<? extends Node> nodeImpl, String trawlerName, int trawlerPort, int localUDPPort, Long seed) throws IOException, IllegalArgumentException{
		super(nodeImpl);
		setParser(new EmulationCommandsParser());
		
		trawler = new Socket(trawlerName, trawlerPort);
		trawlerWriter = new PrintWriter(trawler.getOutputStream(), true);
		trawlerReader = new BufferedReader(new InputStreamReader(trawler.getInputStream()));
		udpSocket = new DatagramSocket(localUDPPort);
		try {
			fishAddress = getFishAddress();
		}catch(NumberFormatException e) {
			System.err.println("Msg received from trawler is not an int, thus is not a fish address!!");
			System.exit(1);
		}
		if(fishAddress == Packet.BROADCAST_ADDRESS) {
			// CentralHandler returns broadcast address to signal there's already someone using the local port
			System.err.println("Port " + localUDPPort + " is already in use. Pick another");
			throw new IllegalArgumentException("Illegal local port " + localUDPPort);
		}
		
		if(userControl != FailureLvl.EVERYTHING){
			if(seed == null){
				this.seed = System.currentTimeMillis();
			}else{
				this.seed = seed;
			}
			System.out.println("Starting simulation with seed: " + this.seed);
			randNumGen = new Random(this.seed);
		}
		
		try{
			node = nodeImpl.newInstance();
		}catch(Exception e){
			throw new IllegalArgumentException("Error while constructing node: " + e);
		}
		node.init(this, fishAddress);
	}
	
	/**
	 * Create a new emulator
	 * @param trawlerName Name of the machine that the CentralHandler is on
	 * @param trawlerPort The port that the CentralHandler is listening on
	 * @param localUDPPort The UDP port that this node should use to talk to its neighbors
	 * @throws UnknownHostException If the trawlerName cannot be resolved
	 * @throws SocketException If there is an error in creating a TCP socket
	 * @throws IOException If there is an error in writing to the TCP socket
	 * @throws IllegalArgumentException If the local port given is already in use
	 */
	public Emulator(Class<? extends Node> nodeImpl, String trawlerName, int trawlerPort, int localUDPPort, FailureLvl failureGen, Long seed) throws UnknownHostException, SocketException, 
	IOException, IllegalArgumentException {
		this(nodeImpl, trawlerName, trawlerPort, localUDPPort, seed);
		
		cmdInputType = InputType.USER;
		userControl = failureGen;
		
		server = new EmulatedNodeServer(udpSocket);
		server.start();
	}
	
	public Emulator(Class<? extends Node> nodeImpl, String trawlerName, int trawlerPort, int localUDPPort, FailureLvl failureGen, String commandFile, Long seed) throws UnknownHostException, SocketException, 
	IOException, IllegalArgumentException {
		this(nodeImpl, trawlerName, trawlerPort, localUDPPort, seed);
		
		cmdInputType = InputType.FILE;
		userControl = failureGen;
		
		server = new EmulatedNodeServer(udpSocket);
		server.start();
		
		EmulationCommandsParser commandFileParser = new EmulationCommandsParser();
		sortedEvents = commandFileParser.parseFile(commandFile);
	}

	/**
	 * <pre>   
	 * Starts the emulated node
	 * do:
	 *   Read commands from the fishnet file if there is one
	 *   Process any defered events
	 *   Process 1 pending incoming message. Timeout when next event is supposed to occur
	 * loop
	 * </pre>   
	 */
	/*public void start() {
		node.start();

		Event nextEvent = null;
		while(true) {
			try {
					if(channelID == EmulatedNodeServer.ID) {
						processPacket(server.getPacket());
					}else if(channelID == IOThreadEmulator.ID) {
						parser.parseLine(keyboard.readLine());
					}
			}catch(Exception e) {
				System.err.println("Exception occured in Emulator. Stack trace: ");
				e.printStackTrace();
			}
		}
	}*/
	
	
	/**
	 * Starts the emulated node
	 */
	public void start() {
		try{
			node.start();
		}catch(NodeCrashException e) { }
		
		if(cmdInputType == InputType.FILE) {
			while(!sortedEvents.isEmpty()) {
				checkCrash();
				
				processPacket(server.getPacket());
				
				handleEvent(sortedEvents.remove(0));
			}
		}else if(cmdInputType == InputType.USER) {
			while(true){
				checkCrash();

				// just in case an exception is thrown or input is null
				Event ev = null;
				
				try{
					// A command will be converted into an Event and passed to the node later in the loop
					// A quit command will be matched later in this try block
					// Empty/whitespace will be treated as a skipped line, which will return null and cause a continue
					System.out.println("Please input a command, or just press enter:");
					String input = keyboard.readLine();
					// Process user input if there is any
					if(input != null) {
						ev = parser.parseLine(input);
					}
				}catch(IOException e){
					System.err.println("Error on user input: " + e);
				}
				
				if(ev == null){
					continue;
				}
				
				handleEvent(ev);
			}
		}
	}
	
	private void checkCrash(){
		// See if we should crash any nodes.
		// Failures and restarts specified in the file are deprecated
		if(userControl.compareTo(FailureLvl.CRASH) < 0){
			int rand = randNumGen.nextInt(100);
			if(rand < failureRate){
				failNode();
			}
		}else{
			try{
				System.out.println("Crash? (y/n)");
				String input = keyboard.readLine();
				if(input.charAt(0) == 'y'){
					failNode();
				}
			}catch(IOException e){
				e.printStackTrace(System.err);
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
		case COMMAND:
			sendNodeCmd(ev.command);
			break;
		case ECHO:
			parser.printStrArray(ev.msg, System.out);
			break;
		case EXIT:
			stop();
		default:
			System.err.println("Shouldn't happen. DELIVERY here?");
		}
	}

	/**
	 * Send the pkt to the specified node
	 * @param from The node that is sending the packet
	 * @param to Int spefying the destination node
	 * @param pkt The packet to be sent, serialized to a byte array
	 * @return True if the packet was sent, false otherwise
	 * @throws IllegalArgumentException If the arguments are invalid
	 */
	public void sendPkt(int from, int to, byte[] pkt) throws IllegalArgumentException {
		super.sendPkt(from, to, pkt);  // check arguments
		EmulatorPacket emulatorPacket = new EmulatorPacket(to, from, pkt);
		byte[] payload = emulatorPacket.pack();
		if(payload == null) {
			return;
		}
		DatagramPacket physicalPacket = new DatagramPacket(payload, payload.length);
		try {
			if(to == Packet.BROADCAST_ADDRESS) {
				broadcastPacket(physicalPacket);	    
			}else {
				sendToTrawler(physicalPacket, to);
			}
		}catch(IOException e) {
			System.err.println("IOException occured while trying to send to node: " + to + ". Exception: " + e);
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

	private int getFishAddress() throws NumberFormatException, IOException {
		trawlerWriter.println(udpSocket.getLocalPort());
		return Integer.parseInt(trawlerReader.readLine());
	}

	// Send a packet but defer sending it if we have sent something else recently
	private void sendToTrawler(DatagramPacket packet, int destAddr) throws IOException {
//		packet.setAddress(arpData.getIPAddress());
//		packet.setPort(arpData.getPort());
		this.udpSocket.send(packet);
	}

	private void broadcastPacket(DatagramPacket packet) throws IOException {
//		Iterator<Integer> iter = arp.keySet().iterator();
//		while(iter.hasNext()) {
//			Integer neighborAddr = iter.next();
//			this.physicalSend(packet, neighborAddr.intValue());
//		}
	}

	private void processPacket(DatagramPacket packet) {
		InetAddress ipAddress = packet.getAddress();
		int port = packet.getPort();
		EmulatorPacket emulatorPacket = EmulatorPacket.unpack(packet.getData());
		if(emulatorPacket == null) {
			// Corrupt data.
			System.err.println("Was unable to extract packet received from " + ipAddress + ":" + port);
			return;
		}
		Integer srcAddr = new Integer(emulatorPacket.getSrc());
		int destAddr = emulatorPacket.getDest();
		if(destAddr == this.fishAddress || destAddr == Packet.BROADCAST_ADDRESS) {
			try{
//				node.onReceive(srcAddr, emulatorPacket.getPayload());
			}catch(NodeCrashException e) { }
		}
		// drop if not for me. This can happen if we took a port that was recently occupied by another node
	}
	
	private void failNode(){
		stop();
	}

	@Override
	protected void checkWriteCrash(Node n, String description) {
		// TODO Auto-generated method stub
		
	}
}
