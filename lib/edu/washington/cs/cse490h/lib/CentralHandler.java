package edu.washington.cs.cse490h.lib;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.NumberFormatException;
import java.lang.Integer;

/**
 * <pre>   
 * Manages the emulated nodes. 
 * It works by listening for new TCP connections. Each new connecting node sends the UDP port
 * it is using for listening to peers. Also multiple emulated nodes might be running on the same machine
 * so this lets us disambiguate them.
 * The CentralHandler replies with the fishnet address that the emulated node should use, as well as the current 
 * neighbor list for that node as <fishnetAddress ipAddress udpPort> pairs.
 * The centralHandler updates this list as it changes.
 *
 * Usage: java CentralHandler <port to listen on> [topo file]
 *       
 *        Topo file is the topology file. It is an optional argument. By default all nodes will be neighbors.
 * </pre>   
 */
public class CentralHandler {

	private static CentralHandler centralHandler = null;
	private ServerSocket socket;
	private HashMap<Integer, EmulatedNode> emulatedNodes;

	/**
	 * Static method to get an instance of centralHandler. 
	 * It will return null if the centralHandler has not been initialized with the port and topofile first
	 * @return An instance of this CentralHandler class
	 */
	public static CentralHandler GetInstance() {
		return centralHandler;
	}

	/**
	 * A node was removed from the topology.  We just remove the node's edges;
	 * the user has to kill the emulated node directly.
	 * NOTE: this must be called *BEFORE* the change to the topology!
	 * @param fishAddr Address of the node to be failed
	 */
	public void failNode(int fishAddr) {
		EmulatedNode node = getEmulatedNode(fishAddr);
		if(node != null) {
//			node.reset();
		}
	}

	/**
	 * A node rejoined the topology, so we add back in all of its edges.
	 * @param fishAddr Address of node that is restarting
	 */
	public void restartNode(int fishAddr) {
//		updateNeighbors(fishAddr);
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
	 * @throws Exception Any other exception that might occur while the CentralHandler is running
	 */
	public void start() throws IOException{
		System.out.println("CentralHandler awaiting fish...");
		Socket nodeSocket;
		PrintWriter out;
		while((nodeSocket = socket.accept()) != null) {
			try {
				checkNodesQuit();

				BufferedReader in = new BufferedReader( new InputStreamReader( nodeSocket.getInputStream() ));
				out = new PrintWriter(nodeSocket.getOutputStream(), true);

				InetAddress ipAddress = nodeSocket.getInetAddress();
				int port = Integer.parseInt(in.readLine());

				if(port < 1024 || portConflict(ipAddress, port)) {
					System.err.println("CentralHandler: Illegal port: " + port);
					out.println(Packet.BROADCAST_ADDRESS);
					out.close();
					nodeSocket.close();
				}else {
					// find a fishnet address to assign to the new node
					int fishAddr = this.freeFishAddr();

					if(fishAddr == -1) {
						System.err.println("CentralHandler: out of addresses");
						out.println(Packet.BROADCAST_ADDRESS);
						out.close();
						nodeSocket.close();		    
					}else {
						//Disable Nagle
						nodeSocket.setTcpNoDelay(true);

						System.out.println("Got port " + port + ": assigning addr: " + fishAddr);
						out.println(fishAddr);
						this.emulatedNodes.put(new Integer(fishAddr), 
								new EmulatedNode(nodeSocket, out, fishAddr, ipAddress, port));
//						updateNeighbors(fishAddr);
					}
				}
			}catch(NumberFormatException e) {
				System.err.println("Msg received from node is not a port number. Socket: " + nodeSocket);
			}catch(IOException e) {
				System.err.println("IOException occured while trying to creade new node. Exception: " + e);
			}catch(Exception e) {
				System.err.println("Exception occured while trying to creade new node. Exception Stack Trace: ");
				e.printStackTrace();		
			}
		}	    
	}


	/**
	 * An emulated node has quit so notify its neighbors
	 * @param dyingNode The node that has quit
	 */
	public void remove(EmulatedNode dyingNode) {
		System.err.println("Removing node " + dyingNode.getFishAddr());
		try {
			Iterator<EmulatedNode> iter = emulatedNodes.values().iterator();
			while(iter.hasNext()) {
				EmulatedNode node = (EmulatedNode)iter.next();
				if(node.getFishAddr() == dyingNode.getFishAddr()) {
					iter.remove();
				}else { 
//					removeNeighbors(dyingNode, node);
				}
			}
		}catch(Exception e) {
			System.err.println("Exception occured while to remove emulated node: " + dyingNode.getFishAddr() + 
					" Exception: " + e);
		}
	}

	/**
	 * Stop the CentralHandler
	 */
	public void exit() {
		System.out.println("CentralHandler exiting...");
		System.exit(0);
	}

	private void checkNodesQuit() {
		try {
			Iterator<EmulatedNode> iter = emulatedNodes.values().iterator();

			for(EmulatedNode node = null; iter.hasNext(); node = iter.next()) {
				if(!node.isAlive()) {
					node.close();
					iter.remove();
				}
			}
		}catch(Exception e) {
			System.err.println("Exception occured while to remove emulated node. Exception: " + e);
		}
	} 


	// returns -1 if no fish address is available
	private int freeFishAddr() {
		for(int i = 0; i < Packet.BROADCAST_ADDRESS; i++) {
			if(!this.emulatedNodes.containsKey(new Integer(i))) {
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

	// return null if addr not in hash
	private EmulatedNode getEmulatedNode(int fishAddr) {
		Integer addr = new Integer(fishAddr);
		EmulatedNode node = null;
		if(this.emulatedNodes.containsKey(addr)) {
			node = (EmulatedNode)this.emulatedNodes.get(addr);
		}
		return node;
	}

	private CentralHandler(int port) throws IOException {
		socket = new ServerSocket(port);
		emulatedNodes = new HashMap<Integer, EmulatedNode>();
	}

	/**
	 * Entry point to start CentralHandler
	 */
	public static void main(String[] args) {
		if(args.length < 1) {
			System.err.println("Missing arguments");
			usage();
			return;
		}

		try {
			int port = Integer.parseInt(args[0]);

			centralHandler = new CentralHandler(port);
			CentralHandler.GetInstance().start();
		}catch(IOException e) {
			System.err.println("Invalid port given to CentralHandler. Exception: " + e);	
		}catch(NumberFormatException e) {
			System.err.println("First argument must be the port number, an integer. Exception: " + e);
		}catch(Exception e) {
			System.err.println("Exception occured in CentralHandler!! Exception: " + e);
		}
	}

	private static void usage() {
		System.out.println("Usage: java CentralHandler <port to listen on>");
	}
}
