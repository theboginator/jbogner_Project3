package Client;

import java.io.*;
import java.net.*;

import Common.MyLogger;
import Common.Packet;
import Common.Settings;

import javax.sound.sampled.Port;


public class Receiver {
	// Create a logger for this class.
	private MyLogger logger = new MyLogger("Client.Receiver");

	public void getFile(String fn) {
		Packet packet;

		// The Common library has a Packet class, create a new Packet of
		// type: REQ, starting at sequence number: 0
		// the length should be the length of the filename (fn)
		// and the data is the byte array from the filename (getBytes)
		// hint:   packet = ...
		packet = new Packet(1,0,fn.length(),fn.getBytes());

		try {
			// call receiveFile and pass to it the packet just created
			receiveFile(packet);
		} catch (Exception e) {
			// Catch any errors generated and print out a stack trace
			e.printStackTrace();
		}

	}

	private void sendAck(int sequenceNumber, InetAddress IPAddress, DatagramSocket clientSocket) throws IOException {
		// Create a new packet (similar to the one in getFile). This is the acknowledgement packet.
		// type: ACK,
		// sequence number: whatever was passed in
		// length: 0 (It is an empty packet)
		// data: null
		// hint:   Packet packet = new ...
		Packet packet = new Packet(0,sequenceNumber,0,null);

		// Now create a DatagramPacket, call it sendPacket.
		// The buf is the packet just created, (example   packet.getBytes())
		// The length is whatever length the getBytes is
		// The IP address was passed in and the port to use can be found in the Settings file.
		// hint:  DO NOT hard-code the port!  Use it from the settings
		// DatagramPacket sendPacket = ...
		byte[] tempbytes = packet.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(packet.getBytes(),tempbytes.length,IPAddress, Settings.PORT);

		// Send the packet using the clientSocket.send method

		// Then log it.  (hint: leave this line here. If you named your variables as stated
		// above it will work)
		logger.sent(packet.getBytes());
	}

	/*
	 * This method will receive the packets from the server and process them.
	 */
	private void receiveFile(Packet packet) throws Exception {
		// Get the byte array from the packet that was passed in. Amongst other things, this
		// will contain the name of the file we're looking for.
		//byte[] packetBytes = ..
		byte [] packetBytes = packet.getBytes();

		// Create a byte array for the received data.  Make the byte array be 1000 characters long
		// Perhaps this should be in the settings file
		//byte[] receiveData = ...
		byte [] receiveData = new byte[1000];

		// Create a DatagramSocket for the clientSocket and create a datagramPacket called
		// receivePacket.
		DatagramSocket clientSocket = new DatagramSocket();
		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

		// Create a string to hold the file being transferred.  Initialize it to an empty string
		// (not null)
		// ...
		String transferFile = "";


		// Use the following to keep track of what's happening
		int packetsReceived = 0;   // The number of packets received
		int nextSeqno = 0;         // The next sequence number
		int lastSeqno = 0;         // The last sequence number received
		int totalData = 0;         // The total data
		int totalSize = 0;

		// Set the client socket to timeout in 1 second
		clientSocket.setSoTimeout(ONE_SECOND_IN_MS);

		// Get the InetAddress (IPAddress)
		// ...
		InetAddress localhost = InetAddress.getLocalHost();
		// Create a DatagramPacket called sendPacket.
		//  buf: the packet bytes (filename)
		//  length: the length of the filename
		// the IP Address and Port
		// hint: DatagramPacket sendPacket = new ...
		DatagramPacket sendPacket = new DatagramPacket(receiveData, receivePacket.getLength());

		// Send it using the clientSocket
		//...
		clientSocket.send(sendPacket);


		// log the data
		logger.sent(packetBytes);

		// Using a boolean to signal that we have the end of transmission (EOT).  While we don't have the
		// complete file, keep looping
		boolean haveEOT = false;
		while (!haveEOT) {
			try {
				// Create a Packet for the incoming data
				// hint:  Packet ...
				Packet received;

				// Call the clientSocket.receive to get receivePacket
				// ...
				clientSocket.receive(receivePacket);

				// Log what just happened
				logger.received(receiveData);

				// Convert the receiveData into a Packet object and store it as incoming data
				// ...
				received = new Packet(receiveData);

				// If the packet type of incoming data is an error (ERR) log it and exit this method
				// if (... ...ERR) {
				//	logger.info("Error: " ...);
				//	return;
				//}
				if(received.getType() == 3){
					logger.info("Error");
				}

				// If the incoming data's sequence number is the next sequence number, then it is a
				// good packet!!!
				// if (...) {
				// Append the data from the incoming packet to the file being transferred
				// ...
				if(received.getSeqNo() == (nextSeqno+1)) {
					transferFile = transferFile +(received.getData());


					// Track the total size
					// ...

					totalSize = totalSize + received.getSize();


					// If the incoming packet type is EOT, then set the haveEOT to true
					// ...
					if(received.getType() == 4) {
						haveEOT = true;
					}

					// Track the last sequence number
					// ...
					lastSeqno = received.getSeqNo();
					// Send an ACK that this sequence was received
					// hint: sendAck(..., ..., clientSocket);
					sendAck(received.getSeqNo(), localhost, clientSocket);

					// Set the next expected sequence number.  Remember that we have a window size that
					// will wrap back to 0 at some point.  The current window size is in the Settings
					// class.  Use it from there !!!  Do not hardcode it!
					// hint: set nextSeqno
					if(nextSeqno == Settings.WINDOW_SIZE){
						nextSeqno=0;
					}else{
						nextSeqno++;
					}



					// Track the total number of packets received
					// hint: increment packetsReceived
					packetsReceived++;

					// If we got a packet, but not what we're expecting
				} else if (packetsReceived == 0) {
					// do nothing


					// Otherwise, we still got a packet, but the sequence number is wrong, log about it and
					// send an ack for the last packet.  The server should figure out what to do from here
				} else {
					logger.info("Received a packet, but not the one we're expecting.");

					// Send the ack
					// hint: sendAck(lastSeqno, ...);
					sendAck(lastSeqno,localhost,clientSocket);
				}
			} catch (SocketTimeoutException e) {
				logger.info("Receive timeout");
			}
		}

		// close the clientSocket and print the file that was received!  Done!  Yippee!!!
		clientSocket.close();
		// hint: close the clientSocket
		logger.info("Received the file containing: " + totalData + "(bytes) of data");
		// Print out the file
		System.out.println(transferFile);
		//System.out.println(...);
	}

	private static int ONE_SECOND_IN_MS = 1000;
	private static String LOCALHOST = "localhost";
}
