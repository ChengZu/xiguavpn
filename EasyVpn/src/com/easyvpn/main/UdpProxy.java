package com.easyvpn.main;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;


public class UdpProxy implements Runnable {
	private DatagramSocket datagramSocket;
	private Packet packet;
	private boolean isClose = false;
	public InetAddress ip;
	public int port;
	public InetAddress srcIp;
	public int srcPort;
	public InetAddress destIp;
	public int destPort;
	public static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE;
	public UdpTunnel udpTunnel;
	public long lastRefreshTime;

	public UdpProxy(UdpTunnel udpTunnel, Packet packet, InetAddress srcIp, InetAddress destIp, int srcPort,
			int destPort) {

		try {

			datagramSocket = new DatagramSocket();
			packet.swapSourceAndDestination();
			this.udpTunnel = udpTunnel;
			this.packet = packet;
			this.srcIp = srcIp;
			this.destIp = destIp;
			this.srcPort = srcPort;
			this.destPort = destPort;

			Thread thread = new Thread(this);
			thread.start();

		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
	

	public void sendToServer(DatagramPacket sendPacket) {
		this.lastRefreshTime = System.currentTimeMillis();
		try {
			datagramSocket.send(sendPacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			close();
			e.printStackTrace();
		}
	}

	public void close() {
		isClose = true;
		if (datagramSocket != null) {
			datagramSocket.close();
		}
	}

	public boolean isClose() {
		long time = System.currentTimeMillis() - this.lastRefreshTime;
		if (time > 10000) {
			close();
			return true;
		}
		return datagramSocket.isClosed() || isClose;
	}
	

	@Override
	public void run() {
		// TODO Auto-generated method stub
		while (!isClose()) {
			byte[] revBuf = new byte[Config.MUTE - HEADER_SIZE];
			DatagramPacket revPacket = new DatagramPacket(revBuf, revBuf.length);
			try {
				datagramSocket.receive(revPacket);
				
				int len = revPacket.getLength();
				if (len > 0) {
					byte[] dataCopy = new byte[HEADER_SIZE + len];
					
					System.arraycopy(revPacket.getData(), 0, dataCopy, HEADER_SIZE, len);

					ByteBuffer buf = ByteBuffer.wrap(dataCopy);

					Packet newPacket = packet.duplicated();
					//note udp checksum = 0;
					newPacket.updateUDPBuffer(buf, len);
					
					//System.out.println("send to client data: "+newPacket);

					udpTunnel.sendToClient(dataCopy, 0, dataCopy.length);
					
				}
				lastRefreshTime = System.currentTimeMillis();
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				close();
				// e.printStackTrace();
			}
		}
	}

}
