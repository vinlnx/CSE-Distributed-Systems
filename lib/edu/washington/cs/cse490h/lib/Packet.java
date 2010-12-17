package edu.washington.cs.cse490h.lib;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * <pre>   
 * Packet defines the MessageLayer packet headers and some constants.
 * </pre>   
 */
public class Packet {

	public static final int BROADCAST_ADDRESS = 255;
	public static final int MAX_ADDRESS = 255;
	public static final int HEADER_SIZE = 4;
	public static final int MAX_PAYLOAD_SIZE = (java.lang.Short.MAX_VALUE+1)*2 - 1;  // bytes
	public static final int MAX_PACKET_SIZE = MAX_PAYLOAD_SIZE + HEADER_SIZE;  // bytes

	private int dest;
	private int src;
	private int protocol;
	private int flags;
	
	public static final byte FIN = 1;
	
	private byte[] payload;

	/**
	 * Constructing a new packet.
	 * @param dest The destination fishnet address.
	 * @param src The source fishnet address.
	 * @param ttl The time-to-live value for this packet.
	 * @param protocol What type of packet this is.
	 * @param seq The sequence number of the packet.
	 * @param payload The payload of the packet.
	 * @throws IllegalArgumentException If the given arguments are invalid
	 */
	public Packet(int dest, int src, int protocol, byte[] payload) throws IllegalArgumentException {

		if(!isValid(dest, src, payload.length + Packet.HEADER_SIZE)) {
			throw new IllegalArgumentException("Arguments passed to constructor of Packet are invalid");
		}

		this.dest = dest;
		this.src = src;
		this.protocol = protocol;
		this.flags = 0;
		this.payload = payload;
	}
	
	public Packet(int node) {
		dest = node;
		src = node;
		protocol = 0;
		flags = FIN;
		payload = new byte[0];
	}

	public Packet(int dest, int src, int protocol, int flags, byte[] payload) throws IllegalArgumentException {

		if(!isValid(dest, src, payload.length + Packet.HEADER_SIZE)) {
			throw new IllegalArgumentException("Arguments passed to constructor of Packet are invalid");
		}

		this.dest = dest;
		this.src = src;
		this.protocol = protocol;
		this.flags = flags;
		this.payload = payload;
	}
	
	/**
	 * Provides a string representation of the packet.
	 * @return A string representation of the packet.
	 */
	@Override
	public String toString() {
		if((flags & FIN) != 0) {
			return new String("Packet: " + src + ": FIN");
		}
		return new String("Packet: " + src + "->" + dest + " protocol: " + protocol + 
				" contents: " + Utility.byteArrayToString(payload));
	}

	/**
	 * @return The address of the destination node
	 */
	public int getDest() {
		return dest;
	}

	/**
	 * @return The address of the src node
	 */
	public int getSrc() {
		return src;
	}

	/**
	 * @return The protocol used for this packet
	 */
	public int getProtocol() {
		return protocol;
	}

	/**
	 * @return The flags for this packet
	 */
	public int getFlags() {
		return flags;
	}
	
	/**
	 * @return The payload of this packet
	 */
	public byte[] getPayload() {
		return payload;
	}

	/**
	 * Convert the Packet object into a byte array for sending over the wire.
	 * Format:
	 *        destination address: 1 byte
	 *        source address: 1 byte
	 *        protocol: 1 byte
	 *        packet length: 1 byte
	 *        payload: <= MAX_PAYLOAD_SIZE bytes
	 * @return A byte[] for transporting over the wire. Null if failed to pack for some reason
	 */
	public byte[] pack() {	

		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		byteStream.write(dest);
		byteStream.write(src);
		byteStream.write(protocol);
		byteStream.write(flags);
		byteStream.write(payload.length);

		byteStream.write(payload, 0, payload.length);

		return byteStream.toByteArray();
	}

	/**
	 * Unpacks a byte array to create a Packet object
	 * Assumes the array has been formatted using pack method in Packet
	 * @param packedPacket String representation of the packet
	 * @return Packet object created or null if the byte[] representation was corrupted
	 */
	public static Packet unpack(byte[] packedPacket){
		ByteArrayInputStream byteStream = new ByteArrayInputStream(packedPacket);

		int dest = byteStream.read();
		int src = byteStream.read();
		int protocol = byteStream.read();
		int flags = byteStream.read();
		int packetLength = byteStream.read();

		byte[] payload = new byte[byteStream.available()];
		byteStream.read(payload, 0, payload.length);

		if(payload.length != packetLength) {
			return null;
		}	

		try {
			return new Packet(dest, src, protocol, flags, payload);
		}catch(IllegalArgumentException e) {
			// will return null
		}
		return null;
	}
	
	/**
	 * Unpacks a byte array to create a Packet object
	 * Assumes the array has been formatted using pack method in Packet
	 * @param packedPacket String representation of the packet
	 * @return Packet object created or null if the byte[] representation was corrupted
	 */
	public static Packet unpack(InputStream stream) throws IOException{
		int dest = stream.read();
		while(dest == -1){
			Thread.yield();
			dest = stream.read();
		}
		int src = stream.read();
		while(src == -1){
			Thread.yield();
			src = stream.read();
		}
		int protocol = stream.read();
		while(protocol == -1){
			Thread.yield();
			protocol = stream.read();
		}
		int flags = stream.read();
		while(flags == -1){
			Thread.yield();
			flags = stream.read();
		}
		int payloadLength = stream.read();
		while(payloadLength == -1){
			Thread.yield();
			payloadLength = stream.read();
		}

		byte[] payload = new byte[payloadLength];
		int off = 0;
		while(off < payloadLength) {
			Thread.yield();
			int read = stream.read(payload, off, payload.length - off);
			if(read != -1) {
				off += read;
			}
		}

		try {
			return new Packet(dest, src, protocol, flags, payload);
		}catch(IllegalArgumentException e) {
			// will return null
		}
		return null;
	}
	
	/**
	 * Tests if the address is a valid one
	 * @param addr Address to check
	 * @return True is address is valid, else false
	 */
	public static boolean validAddress(int addr) {
		return (addr <= MAX_ADDRESS && addr >= 0);
	}

	/**
	 * Tests if this Packet is valid or not
	 * @return True if packet is valid, else false
	 */
	public boolean isValid() {
		return isValid(dest, src, payload.length + HEADER_SIZE);
	}

	private boolean isValid(int dest, int src, int size) {
		return (dest <= MAX_ADDRESS && dest >= 0   &&
				Packet.validAddress(src)           &&
				size <= MAX_PACKET_SIZE);

	}

	public String toSynopticString() {
		return new String("src:" + src + " dest:" + dest + " proto:" + protocol + 
				" contents:" + Utility.byteArrayToString(payload));
	}
}
