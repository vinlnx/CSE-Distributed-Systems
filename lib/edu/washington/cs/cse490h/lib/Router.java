package edu.washington.cs.cse490h.lib;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.lang.Integer;

import plume.Option;
import plume.OptionGroup;
import plume.Options;

/**
 * <pre>   
 * Manages the emulated nodes. 
 * It works by listening for new TCP connections. Each new connecting node sends the UDP port
 * it is using for listening to peers. Also multiple emulated nodes might be running on the same machine
 * so this lets us disambiguate them.
 * The Router replies with the fishnet address that the emulated node should use, as well as the current 
 * neighbor list for that node as <fishnetAddress ipAddress udpPort> pairs.
 * The router updates this list as it changes.
 *
 * Usage: java Router <port to listen on> [topo file]
 *       
 *        Topo file is the topology file. It is an optional argument. By default all nodes will be neighbors.
 * </pre>   
 */
public class Router {
	private static Router router = null;
	private ServerSocket socket;
	Map<Integer, EmulatedNode> emulatedNodes;
	HashMap<Integer, List<Packet> > destDownQueue;

	private Router(int port) throws IOException {
		socket = new ServerSocket(port);
		emulatedNodes = Collections.synchronizedMap( new HashMap<Integer, EmulatedNode>() );
		destDownQueue = new HashMap<Integer, List<Packet>>();
	}
	
	/**
	 * <pre>          
	 * Wait for a node to connect, then:
	 *	find out if any nodes have left while we were waiting
	 *		(and if so, update their neighbors so they stop sending them packets)
	 *	find a free fishnet Address to assign to the new node, and send it to them
	 *		(by writing to the node's TCP socket)
	 *	find out which UDP port they are listening to
	 *		(by reading from the node's TCP socket)
	 *	tell the node about all its neighbors' IP addresses and port #'s
	 *	tell all their neighbors with their IP address and port #
	 *	loop
	 * </pre>
	 * @throws Exception Any other exception that might occur while the Router is running
	 */
	public void start() throws IOException{
		System.out.println("Router awaiting nodes...");
		
		while(true) {
			try {
				Socket nodeSocket = socket.accept();

				InetAddress ipAddress = nodeSocket.getInetAddress();
				int port = nodeSocket.getPort();

				if(port < 1024 || portConflict(ipAddress, port)) {
					System.err.println("Router: Illegal port: " + port);
					nodeSocket.getOutputStream().write(Packet.BROADCAST_ADDRESS);
					nodeSocket.getOutputStream().flush();
					nodeSocket.close();
				}else {
					// find a virtual address to assign to the new node
					int address = freeAddr();

					if(address == -1) {
						System.err.println("Router: out of addresses");
						nodeSocket.getOutputStream().write(Packet.BROADCAST_ADDRESS);
						nodeSocket.getOutputStream().flush();
						nodeSocket.close();		    
					} else {
						//Disable Nagle
						nodeSocket.setTcpNoDelay(true);

						System.out.println("Connecting to " + ipAddress + ":" + port + " Assigning addr: " + address);
						nodeSocket.getOutputStream().write(address);
						nodeSocket.getOutputStream().flush();

						EmulatedNode newNode = new EmulatedNode(this, nodeSocket, address, ipAddress, port);
						emulatedNodes.put(address, newNode);

						sendQueued(newNode, destDownQueue.remove(address));
					}
				}
			}catch(IOException e) {
				System.err.println("IOException occured while trying to creade new node. Exception: " + e);
			}catch(Exception e) {
				System.err.println("Exception occured while trying to creade new node. Exception Stack Trace: ");
				e.printStackTrace();		
			}
		}	    
	}

	/**
	 * Stop the Router
	 */
	public void exit() {
		System.out.println("Router exiting...");
		System.exit(0);
	}

	// return null if addr not in hash
	protected EmulatedNode getEmulatedNode(int address) {
		return emulatedNodes.get(address);
	}

	protected void nodeQuit(int address) {
		emulatedNodes.remove(address);
	}

	private void sendQueued(EmulatedNode newNode, List<Packet> queue) {
		if(queue == null) {
			return;
		}
		for(Packet pkt: queue) {
			newNode.send(pkt);
		}
	}

	// returns -1 if no more addresses are available
	private int freeAddr() {
		for(int i = 0; i < Packet.BROADCAST_ADDRESS; i++) {
			if(!emulatedNodes.containsKey(i)) {
				return i;
			}
		}
		return -1;
	}

	private boolean portConflict(InetAddress ipAddress, int port) {
		for(EmulatedNode node: emulatedNodes.values()) {
			if(ipAddress.equals(node.getIPAddress()) && port == node.getPort()) {
				return true;
			}
		}
		return false;
	}

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
	 * Local port
	 */
	@OptionGroup("Execution Options")
	@Option(value="-p Local UDP port", aliases={"-local-port"})
	// TODO: specify a sane default
	public static int localUDPPort = -1;
	// end option group "Execution Options"


	/** One line synopsis of usage */
	private static String usage_string
	= "java Router [options]";


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
	 * Entry point to start Router
	 */
	public static void main(String[] args) {
		// this directly sets the static member options of the Main class
		Options options = new Options (usage_string, Router.class);
		
		@SuppressWarnings("unused")
		String[] cmdLineArgs = options.parse_or_usage(args);

		if (help) {
			options.print_usage();
			return;
		}

		if (version) {
			System.out.println(Router.versionString);
			return;
		}

		if (localUDPPort == -1) {
			System.out.println("you must specify a port with -p.");
			return;
		}
		
		try {
			router = new Router(localUDPPort);
			router.start();
		}catch(IOException e) {
			System.err.println("Invalid port given to Router. Exception: " + e);	
		}catch(Exception e) {
			System.err.println("Exception occured in Router!! Exception: " + e);
		}
	}
}
