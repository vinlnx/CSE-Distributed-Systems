package edu.washington.cs.cse490h.lib;

import java.lang.Thread;
import java.util.ArrayList;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <pre>   
 * In a separate thread, this class listens to incoming messages from neighbors and stores the data received
 * </pre>   
 */
public class NodeServer extends Thread {
	private Socket socket;
	private ArrayList<Packet> packetsReceived;
	private InputStream in;
	private OutputStream out;
	private int address;
	private Emulator parent;
	
	private boolean gotFIN;

	public int getAddress() {
		return address;
	}

	/**
	 * Creates a new EmulatedNodeServer
	 * @param socket The UDP socket to listen on
	 */
	public NodeServer(String name, int port, Emulator parent) throws IOException{
		socket = new Socket(name, port);
		packetsReceived = new ArrayList<Packet>();
		in = socket.getInputStream();
		out = socket.getOutputStream();
		gotFIN = false;
		this.parent = parent;
		
		address = in.read();
		while(address == -1){
			Thread.yield();
			address = in.read();
		}
	}

	/**
	 * This starts the server
	 */
	public void run() {
		while(true) {
			try {
				Packet packet = Packet.unpack(in);
				
				//System.out.println("Received packet: " + packet);
				
				if((packet.getFlags() & Packet.FIN) != 0) {
					gotFIN = true;
					socket.shutdownInput();
					return;
				}
				
				// store it
				storePacket(packet);
			}catch(IOException e) {
				System.err.println("Encountered IOException when trying to receive packet. User should kill explicitly. \nStack Trace:");
				e.printStackTrace();
				continue;
			}
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
	public synchronized Packet getPacket() {
		if(packetsReceived.isEmpty()) {
			return null;
		}
		return packetsReceived.remove(0);
	}

	protected void send(byte[] pkt) throws IOException{
		out.write(pkt);
		out.flush();
	}
	
	private synchronized void storePacket(Packet packet) {
		packetsReceived.add(packet);
	}
	
	void close() {
		try{
			Packet fin = new Packet(address);
			send(fin.pack());
			
			while(!gotFIN){
				Thread.yield();
			}
			
			for(Packet pkt: packetsReceived) {
				send(pkt.pack());
			}
			for(Packet pkt: parent.inTransitMsgs) {
				send(pkt.pack());
			}
			send(fin.pack());
			
			socket.close();
		} catch (IOException e) {
			System.err.println("Error while closing socket!");
			e.printStackTrace();
		}
	}
}
