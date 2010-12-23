package edu.washington.cs.cse490h.lib;

import java.net.Socket;
import java.net.InetAddress;
import java.util.Collection;
import java.util.LinkedList;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * <pre>   
 * Keeps track of information about an emulated node
 * Emulated node uses a TCP socket to talk to the Router, but send and receive messages directly
 * to other emulated nodes using UDP. 
 * </pre>   
 */
public class EmulatedNode implements Runnable{
	private Router parent;
	private Socket socket;               // Socket used to talk to node
	private OutputStream out;             // Output stream to send data across socket
	private InputStream in;
	private int addr;                // Fish address assigned to node

	//This is the ip address and port that node uses to talk to other nodes via UDP
	private InetAddress ipAddress;       // The IP address of node. 
	private int port;                  // The port that node is on.
	
	private boolean cleanQuit;
	private boolean finished;

	/**
	 * Create a new EmulatedNode
	 * @param socket The socket to use to talk to the emulated node
	 * @param out An outputstream to use to send data across the socket
	 * @param fishAddr The fishnet address of the emulated node
	 * @param ipAddress The IP address of the machine that the node is on
	 * @param port The port that the emulated node will use to talk to other nodes
	 * @throws IOException 
	 */
	public EmulatedNode(Router parent, Socket socket, int addr, InetAddress ipAddress, int port) throws IOException {
		this.parent = parent;
		this.socket = socket;
		this.addr = addr;
		this.ipAddress = ipAddress;
		this.port = port;

		out = socket.getOutputStream();
		in = socket.getInputStream();

		cleanQuit = false;
		finished = false;

		Thread t = new Thread(this);
		t.start();
	}

	public void run() {
		try {
			while(isUp()) {
				Packet packet = Packet.unpack(in);

				if(packet == null) {
					// The other side closed the connection
					break;
				}

				if((packet.getFlags() & Packet.FIN) != 0) {
					LinkedList<Packet> queue = close();
					// if a send occurs here it's OK cause finished = true
					// we don't call nodeQuit inside close because it could cause deadlock
					parent.nodeQuit(addr, queue);
				} else if(packet.getDest() == Manager.BROADCAST_ADDRESS) {
					Collection<Integer> c = parent.emulatedNodes.keySet();
					System.out.println("Broadcasting: " + packet);

					synchronized(parent.emulatedNodes) {
						for(Integer dest: c) {
							if(dest != addr) {
								parent.emulatedNodes.get(dest).send(packet);
							}
						}
					}
				} else {
					parent.emulatedNodes.get(packet.getDest()).send(packet);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		if(!cleanQuit) {
			try {
				socket.close();
			} catch (IOException e) {
			}
			if(parent != null) {
				parent.nodeQuit(addr, null);
			}
		}
	}

	protected synchronized boolean send(Packet pkt){
		if(finished) {
			return false;
		}

		try {
			out.write(pkt.pack());
			out.flush();
		} catch (IOException e) {
			finished = true;
			e.printStackTrace();
		}
		return true;
	}

	/**
	 * Close the the connection to the node server
	 */
	private synchronized LinkedList<Packet> close() {
		Packet fin = new Packet(addr);
		send(fin);
		finished = true;

		try {
			socket.shutdownOutput();

			LinkedList<Packet> queue = new LinkedList<Packet>();

			while(true) {
				Packet packet = Packet.unpack(in);

				if(packet == null) {
					throw new IOException("Corrupted packet.  Cannot recover from misalignment.");
				}

				if((packet.getFlags() & Packet.FIN) != 0) {
					cleanQuit = true;
					socket.close();
					return queue;
				} else {
					queue.add(packet);
				}
			}
		} catch (IOException e) {
			System.err.println("Encountered IO Exception while trying to close socket in EmulatedNode: "
					+ addr + "Exception Stack Trace:");
			e.printStackTrace();
		}
		
		return null;
	}

	/**
	 * Get the virtual address of this node
	 * @return The virtual address of this node
	 */
	protected int getAddr() {
		return addr;
	}

	/**
	 * Get the IP address of the machine that this emulated node is on
	 * @return The IP address of the machine that this emulated node is on
	 */
	protected InetAddress getIPAddress() {
		return ipAddress;
	}

	/**
	 * Get the port that this emulated node is using to talk to its neighbors
	 * @return The port that this emulated node is using to talk to its neighbors
	 */
	protected int getPort() {
		return port;
	}

	/**
	 * Return a string containing details of this emulated node
	 * @return A string containing details of this emulated node
	 */
	public String toString() {
		return new String("<TCP: " + socket.getInetAddress() + ":" + socket.getPort() + " Fish: " + addr + ">");
	}

	/**
	 * Called by the router to tell the emulated node to stop and that the
	 * router has already removed the emulated node
	 */
	protected void finish() {
		parent = null;
		finished = true;
	}
	
	/**
	 * Check if the emulated node is still alive
	 * @return True if the node is still alive
	 */
	protected boolean isUp() {
		return (!socket.isClosed() &&
				!socket.isInputShutdown() &&
				!finished);
	}
}
