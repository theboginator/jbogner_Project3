package Server;

import java.net.SocketTimeoutException;

import Common.MyLogger;
import Common.Packet;
import Common.PacketType;
import Common.Settings;

class NoPacketsException extends Exception {
	private static final long serialVersionUID = 1L;

	public NoPacketsException(String msg) {
		super(msg);
	}
}

class WrongPacketTypeException extends Exception {
	private static final long serialVersionUID = 1L;

	public WrongPacketTypeException(String msg) {
		super(msg);
	}
}

public class PacketManager {
	private MyLogger logger = new MyLogger("Server.PacketManager");
	
	public PacketManager(UDP udpSocket) {		
		// Store the udpSocket as a class variable.
		// hint:  this.udpSocket...
		
		// Call the reset to make sure the packet manager is all set
		// ...
	}
	
	private void reset() {
		seqno = 0;
		windowStart = 0;
		nextSlot = 0;
		
		// Set all packets from 0 through MAX to null
		// ...
	}

	public boolean okToSend() {
		// It is ok to send if the packet in the next slot is null
		// hint packets[nextSlot]
	}

	private int allocateSlot() throws NoPacketsException {
		if (okToSend()) {
			int slotToReturn = nextSlot++;
			nextSlot = nextSlot % MAX_PACKETS;
			return slotToReturn;
		}

		throw new NoPacketsException("No packets available.");
	}
	
	public void processAck() throws WrongPacketTypeException {
		try {
			Packet packet = new Packet(udpSocket.getPacket(1000));
			if (packet.getType() != PacketType.ACK)
				throw new WrongPacketTypeException("Received: " + packet.getType() + " but was expecting ACK");

			acksReceived++;

			if (packets[windowStart].getSeqNo() == packet.getSeqNo()) {
				packets[windowStart++] = null;
				windowStart = windowStart % MAX_PACKETS;
			} else {
				logger.warning("Received an unexpected ACK. Expected: " + packets[windowStart].getSeqNo()
						+ " but received an ACK for: " + packet.getSeqNo());
				badAcksReceived++;
			}
		} catch (SocketTimeoutException e) {
			logger.warning("Missing ACK for packet " + packets[windowStart].getSeqNo() + " resending cached window.");
			resend();
		} catch (WrongPacketTypeException e) {
			// don't do anything
		}
	}
	
	public boolean done() {
		// it is done when all packets from 0 through MAX are null
		// hint: check all packets to make sure they're null, if one is, this should return false
		
		return true;
	}

	public void resend() {
		for (int i = 0; i < MAX_PACKETS; i++) {
			Packet p = packets[(i + windowStart) % MAX_PACKETS];
			if (p != null) {
				packetCounts[p.getType()]++;
				
				logger.info("Resending " + PacketType.toString(p.getType()) + " from window slot: " + (i + windowStart) % MAX_PACKETS);

				udpSocket.sendPacket(p.getSize(), p.getBytes());
				packetsSent++;
				packetsResent++;
			}
		}
	}

	public void send(int packetType, String data) throws WrongPacketTypeException {
		boolean sending = true;
		
		if (data == null)
			data = "";
		
		int dataSize = (data.length() > 0) ? data.length() : 0;
		
		packetCounts[packetType]++;

		while (sending) {
			try {
				if (sending) {
					int slot = allocateSlot();
					
					packets[slot] = new Packet(packetType, seqno++, dataSize, data.getBytes());
					
					if (Settings.SKIP_FIRST_PACKET && packets[slot].getSeqNo() == 0) {
						logger.info("==========> Skipping packet to simulate data loss of first packet");
					} else if (Settings.SKIP_ALL_PACKETS) {
						logger.info("==========> Skipping packet to simulate data loss");
					} else {
						// Send the packet from the slot because we aren't skipping this one.
						// hint use udpSocket.sendPacket
					}
					
					expectedPacketsSent++;
					packetsSent++;

					seqno = seqno % Settings.WINDOW_SIZE;
					sending = false;
				}
			} catch (NoPacketsException np) {
				processAck();
			}
		}
	}
	
	public void stats() {
		logger.info(String.format("Packet Manager:"));
		logger.info(String.format("    Expected Number of Sent Packets:     %4d", expectedPacketsSent));
		logger.info(String.format("    Number of packets Resent:            %4d", packetsResent));
		logger.info(String.format("    Breakdown:"));
		for (int i = 0; i < 5; i++)
			logger.info(String.format("        Number of %3s Packets:           %4d", PacketType.toString(i), packetCounts[i]));
		logger.info(String.format("    Total Number of Packets Sent:        %4d", packetsSent));
		logger.info(String.format(""));
		logger.info(String.format("    Number of ACKs received:             %4d", acksReceived));
		logger.info(String.format("    Number of unexpected ACKs received:  %4d", badAcksReceived));
		logger.info(String.format(""));
		udpSocket.stats();
	}

	private final int MAX_PACKETS = Settings.WINDOW_SIZE - 1;

	private int seqno = 0;
	private int windowStart = 0;
	private int nextSlot = 0;
	private Packet packets[] = new Packet[MAX_PACKETS];
	private static int packetsSent = 0;
	private static int packetsResent = 0;
	private static int expectedPacketsSent = 0;
	private static int acksReceived = 0;
	private static int badAcksReceived = 0;
	
	private static int[] packetCounts = {0,0,0,0,0};
	
	private UDP udpSocket;
}
