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
public class NodeServer implements Runnable {
	private Socket socket;
	private ArrayList<Packet> packetsReceived;
	private InputStream in;
	private OutputStream out;
	private int address;
	private Emulator parent;
	
	private boolean gotFIN;
	private boolean finished;

	protected int getAddress() {
		return address;
	}

	/**
	 * Creates a new NodeServer
	 * @param socket The UDP socket to listen on
	 */
	public NodeServer(String name, int port, Emulator parent) throws IOException{
		socket = new Socket(name, port);
		packetsReceived = new ArrayList<Packet>();
		in = socket.getInputStream();
		out = socket.getOutputStream();
		gotFIN = false;
		this.parent = parent;
		finished = false;
		
		address = in.read();
		
		if(address != Manager.BROADCAST_ADDRESS) {
			Thread t = new Thread(this);
			t.start();
		}
	}

	/**
	 * This starts the server
	 */
	public void run() {
		try {
			while(!finished && !socket.isClosed()) {
				Packet packet = Packet.unpack(in);

				if(packet == null) {
					// The other side closed the connection
					break;
				}
				//System.out.println("Received packet: " + packet);

				if((packet.getFlags() & Packet.FIN) != 0) {
					gotFIN = true;
					return;
				}

				// store it
				storePacket(packet);
			}
		}catch(IOException e) {
			System.err.println("Encountered IOException when trying to receive packet.");
			e.printStackTrace();
		}

		try {
			socket.close();
		} catch (IOException e) {
		}
		synchronized (parent) {
			if (parent != null) {
				parent.IOFinish();
			}
		}
	}

	/**
	 * Gets the first packet stored
	 * @return The first packet stored
	 */
	protected Packet getPacket() {
		synchronized (packetsReceived) {
			if (packetsReceived.isEmpty()) {
				return null;
			}
			return packetsReceived.remove(0);
		}
	}

	protected void send(byte[] pkt) {
		try {
			out.write(pkt);
			out.flush();
		} catch (IOException e) {
			finished = true;
			e.printStackTrace();
		}
	}
	
	private void storePacket(Packet packet) {
		synchronized (packetsReceived) {
			packetsReceived.add(packet);
		}
	}
	
	protected void close() {
		try {
			Packet fin = new Packet(address);
			send(fin.pack());

			while (!gotFIN) {
				if (socket.isClosed()) {
					if (!gotFIN) {
						throw new IOException("Socket closed before we got a FIN back!");
					}
				}
				Thread.yield();
			}

			synchronized (packetsReceived) {
				for (Packet pkt : packetsReceived) {
					send(pkt.pack());
				}
			}
			for (Packet pkt : parent.inTransitMsgs) {
				send(pkt.pack());
			}
			send(fin.pack());
		} catch (IOException e) {
			System.err.println("Error while sending back packets.");
			e.printStackTrace();
		}

		packetsReceived.clear();
		synchronized (parent) {
			parent = null;
		}
		finished = true;

		try {
			socket.close();
		} catch (IOException e) {
			System.err.println("Error while closing socket!");
			e.printStackTrace();
		}
	}
}
