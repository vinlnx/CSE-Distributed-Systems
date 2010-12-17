package edu.washington.cs.cse490h.lib;

import java.net.Socket;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
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
	
	private boolean error;

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

		error = false;

		Thread t = new Thread(this);
		t.start();
	}

	//FIXME: shouldn't assume that the recipient stays up
	public void run() {
		try {
			while(isUp() && !error) {
				Packet packet = Packet.unpack(in);

				if(packet == null) {
					error = true; // this doesn't matter right now but we set it anyway
					throw new IOException("Corrupted packet.  Cannot recover from misalignment.");
				}

				if((packet.getFlags() & Packet.FIN) != 0) {
					close();
				} else if(packet.getDest() == Packet.BROADCAST_ADDRESS) {
					//TODO: possibly queue to dead nodes, this same problem arises within the simulator framework as well
					Collection<EmulatedNode> c = parent.emulatedNodes.values();
					System.out.println("Broadcasting: " + packet);
					synchronized(parent.emulatedNodes) {
						for(EmulatedNode dest: c) {
							if(dest.addr != this.addr) {
								System.out.println("To: " + dest.addr);
								dest.send(packet);
							}
						}
					}
				} else {
					EmulatedNode dest = parent.getEmulatedNode(packet.getDest());
					if (dest != null) {
						System.out.println("Sending: " + packet);
						dest.send(packet);
					} else {
						// The node failed, but let's send anyways to whatever node takes this address in the future
						List<Packet> downQueue = parent.destDownQueue.get(packet.getDest());
						if(downQueue == null) {
							downQueue = Collections.synchronizedList( new LinkedList<Packet>() );
							parent.destDownQueue.put(packet.getDest(), downQueue);
						}

						System.out.println("Queueing to failed node: " + packet);
						downQueue.add(packet);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public synchronized void send(Packet pkt){
		try {
			out.write(pkt.pack());
			out.flush();
		} catch (IOException e) {
			error = true;
		}
	}

	/**
	 * Close the the connection to the emulated node
	 */
	public synchronized void close() {
		try {
			Packet fin = new Packet(addr);
			send(fin);
			socket.shutdownOutput();

			List<Packet> downQueue = parent.destDownQueue.get(addr);
			if(downQueue == null) {
				downQueue = Collections.synchronizedList( new LinkedList<Packet>() );
				parent.destDownQueue.put(addr, downQueue);
			}
			parent.nodeQuit(addr);

			while(true) {
				Packet packet = Packet.unpack(in);

				if(packet == null) {
					throw new IOException("Corrupted packet.  Cannot recover from misalignment.");
				}

				if((packet.getFlags() & Packet.FIN) != 0) {
					socket.close();
					return;
				} else {
					downQueue.add(packet);
				}
			}
		} catch (IOException e) {
			System.err.println("Encountered IO Exception while trying to close socket in EmulatedNode: "
								+ addr + "Exception Stack Trace:");
			e.printStackTrace();
			error = true;
		}
	}

	/**
	 * Get the virtual address of this node
	 * @return The virtual address of this node
	 */
	public int getAddr() {
		return addr;
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
		return new String("<TCP: " + socket.getInetAddress() + ":" + socket.getPort() + " Fish: " + addr + ">");
	}

	/**
	 * Check if the emulated node is still alive
	 * @return True if the node is still alive
	 */
	public boolean isUp() {
		return (!socket.isClosed() &&
				!socket.isInputShutdown());
	}
}
