package edu.washington.cs.cse490h.lib;

import java.lang.Thread;
import java.util.ArrayList;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.IOException;

/**
 * <pre>   
 * In a separate thread, this class listens to incoming messages from neighbors and stores the data received
 * </pre>   
 */
public class EmulatedNodeServer extends Thread {
	private DatagramSocket socket;
	private ArrayList<DatagramPacket> packetsReceived;
	private static int MAX_PACKET_LENGTH = EmulatorPacket.MAX_PACKET_SIZE;

	/**
	 * Creates a new EmulatedNodeServer
	 * @param socket The UDP socket to listen on
	 */
	public EmulatedNodeServer(DatagramSocket socket) {
		this.socket = socket;
		packetsReceived = new ArrayList<DatagramPacket>();
	}

	/**
	 * This starts the server
	 */
	public void run() {
		while(true) {
			byte[] buf = new byte[MAX_PACKET_LENGTH];

			// receive request
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			try {
				socket.receive(packet);
			}catch(IOException e) {
				System.err.println("Encountered IOException when to recceive packet. User should kill explicitly. \nStack Trace:");
				e.printStackTrace();
				continue;
			}
			
			// store it
			storePacket(packet);
		}
	}

	/**
	 * Tests if there are more packets stored
	 * @return True if there are more packets stored in memory
	 */
	public synchronized boolean hasPackets() {
		return !packetsReceived.isEmpty();
	}

	/**
	 * Gets the first packet stored
	 * @return The first packet stored
	 */
	public synchronized DatagramPacket getPacket() {
		if(packetsReceived.isEmpty()) {
			return null;
		}
		return packetsReceived.remove(0);
	}

	private synchronized void storePacket(DatagramPacket packet) {
		packetsReceived.add(packet);
	}
}
