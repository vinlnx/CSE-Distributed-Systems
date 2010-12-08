package edu.washington.cs.cse490h.lib;

import java.net.Socket;
import java.net.InetAddress;
import java.io.PrintWriter;
import java.io.IOException;

/**
 * <pre>   
 * Keeps track of information about an emulated node
 * Emulated node uses a TCP socket to talk to the CentralHandler, but send and receive messages directly
 * to other emulated nodes using UDP. 
 * </pre>   
 */
public class EmulatedNode {

	private Socket socket;               // Socket used to talk to node
	private PrintWriter out;             // Output stream to send data across socket
	private int fishAddr;                // Fish address assigned to node

	//This is the ip address and port that node uses to talk to other nodes via UDP
	private InetAddress ipAddress;       // The IP address of node. 
	private int port;                  // The port that node is on.

	/**
	 * Create a new EmulatedNode
	 * @param socket The socket to use to talk to the emulated node
	 * @param out An outputstream to use to send data across the socket
	 * @param fishAddr The fishnet address of the emulated node
	 * @param ipAddress The IP address of the machine that the node is on
	 * @param port The port that the emulated node will use to talk to other nodes
	 */
	public EmulatedNode(Socket socket, PrintWriter out, int fishAddr, InetAddress ipAddress, int port) {
		this.socket = socket;
		this.fishAddr = fishAddr;
		this.ipAddress = ipAddress;
		this.port = port;

		this.out = out;
	}

	/**
	 * Close the the connection to the emulated node
	 */
	public void close() {
		try {
			out.close();
			socket.close();
		} catch (IOException e) {
			System.err.println("Encountered IO Exception while trying to close socket in EmulatedNode: "
								+ fishAddr + "Exception Stack Trace:");
			e.printStackTrace();
		}
	}

	/**
	 * Get the fishnet address of this node
	 * @return The fishnet address of this node
	 */
	public int getFishAddr() {
		return fishAddr;
	}

	/**
	 * Get the IP address of the machine that this emulated node is on
	 * @return The IP address of the machine that this emulated node is on
	 */
	public InetAddress getIPAddress() {
		return ipAddress;
	}

	/**
	 * Get the port that this emulated node is using to talk to its neighbors
	 * @return The port that this emulated node is using to talk to its neighbors
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Return a string containing details of this emulated node
	 * @return A string containing details of this emulated node
	 */
	public String toString() {
		return new String("<TCP: " + socket.getInetAddress() + ":" + socket.getPort() + " Fish: " + fishAddr +
				" UDP: " + ipAddress + ":" + port + ">");
	}

	/**
	 * Check if the emulated node is still alive
	 * @return True if the node is still alive
	 */
	public boolean isAlive() {
		return (!socket.isClosed() &&
				!out.checkError()  &&
				!socket.isOutputShutdown());
	}
}
