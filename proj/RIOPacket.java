import java.math.BigInteger;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

import edu.washington.cs.cse490h.lib.Packet;

/**
 * This conveys the header for reliable, in-order message transfer. This is
 * carried in the payload of a Packet, and in turn the data being transferred is
 * carried in the payload of the RIOPacket packet.
 */
public class RIOPacket {

	public static final int MAX_PACKET_SIZE = Packet.MAX_PAYLOAD_SIZE;
	public static final int HEADER_SIZE = 6;
	public static final int MAX_PAYLOAD_SIZE = MAX_PACKET_SIZE - HEADER_SIZE;

	private int protocol;
	private int seqNum;
	private byte[] payload;

	/**
	 * Constructing a new RIO packet.
	 * @param type The type of packet. Either SYN, ACK, FIN, or DATA
	 * @param seqNum The sequence number of the packet
	 * @param payload The payload of the packet.
	 */
	public RIOPacket(int protocol, int seqNum, byte[] payload) throws IllegalArgumentException {
		if (!Protocol.isRIOProtocolValid(protocol) || payload.length > MAX_PAYLOAD_SIZE) {
			throw new IllegalArgumentException("Illegal arguments given to RIOPacket");
		}

		this.protocol = protocol;
		this.seqNum = seqNum;
		this.payload = payload;
	}

	/**
	 * @return The protocol number
	 */
	public int getProtocol() {
		return this.protocol;
	}
	
	/**
	 * @return The sequence number
	 */
	public int getSeqNum() {
		return this.seqNum;
	}

	/**
	 * @return The payload
	 */
	public byte[] getPayload() {
		return this.payload;
	}

	/**
	 * Convert the RIOPacket packet object into a byte array for sending over the wire.
	 * Format:
	 *        protocol = 1 byte
	 *        sequence number = 4 bytes
	 *        packet length = 1 byte
	 *        payload <= MAX_PAYLOAD_SIZE bytes
	 * @return A byte[] for transporting over the wire. Null if failed to pack for some reason
	 */
	public byte[] pack() {

		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		byteStream.write(protocol);
		
		// write 4 bytes for sequence number
		byte[] seqByteArray = (BigInteger.valueOf(seqNum)).toByteArray();
		int paddingLength = 4 - seqByteArray.length;
		for(int i = 0; i < paddingLength; i++) {
			byteStream.write(0);
		}
		byteStream.write(seqByteArray, 0, Math.min(seqByteArray.length, 4));

		byteStream.write(HEADER_SIZE + payload.length);	
		byteStream.write(payload, 0, payload.length);

		return byteStream.toByteArray();
	}

	/**
	 * Unpacks a byte array to create a RIOPacket object
	 * Assumes the array has been formatted using pack method in RIOPacket
	 * @param packet String representation of the transport packet
	 * @return RIOPacket object created or null if the byte[] representation was corrupted
	 */
	public static RIOPacket unpack(byte[] packet) {
		ByteArrayInputStream byteStream = new ByteArrayInputStream(packet);

		int protocol = byteStream.read();

		byte[] seqByteArray = new byte[4];
		if(byteStream.read(seqByteArray, 0, 4) != 4) {
			return null;
		}
		int seqNum = (new BigInteger(seqByteArray)).intValue();

		int packetLength = byteStream.read();

		byte[] payload = new byte[packetLength - HEADER_SIZE];
		int bytesRead = Math.max(0, byteStream.read(payload, 0, payload.length));

		if((HEADER_SIZE + bytesRead) != packetLength) {
			return null;
		}

		try {
			return new RIOPacket(protocol, seqNum, payload);
		}catch(IllegalArgumentException e) {
			// will return null
		}
		return null;
	}
}
