package com.easyvpn.main;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.easyvpn.main.Packet.IP4Header;

public class UdpServer implements Runnable {
	private DatagramSocket datagramSocket;
	private ArrayList<TcpProxy2> tcpProxys = new ArrayList<>();
	private ArrayList<UdpProxy> udpProxys = new ArrayList<>();

	private boolean isClose = false;

	public UdpServer() {
		try {
			datagramSocket = new DatagramSocket(Config.PORT);
			Thread thread = new Thread(this);
			thread.start();

		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	public void close() {
		isClose = true;
		datagramSocket.close();
		clearAllProxy();
	}

	public boolean isClose() {
		return datagramSocket.isClosed() || isClose;
	}

	public void write(DatagramPacket sendPacket) {
		try {
			datagramSocket.send(sendPacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void processPacket(DatagramPacket recePacket) throws UnknownHostException {

		ByteBuffer byteBuffer = ByteBuffer.wrap(recePacket.getData(), 0, recePacket.getLength());
		byteBuffer.limit(recePacket.getLength());
		Packet packet = new Packet(byteBuffer);

		if (packet.isTCP()) {
			processTcpPacket(recePacket, packet); //处理udp发过来的tcp包
		} else if (packet.isUDP()) {
			processUdpPacket(recePacket, packet);
		}

	}

	public void processTcpPacket(DatagramPacket recePacket, Packet packet) {
		if (tcpProxys.size() > Config.MAX_CONNECT) {
			System.out.println("TcpProxy connect max");
			return;
		}
		IP4Header Ip4Header = packet.getIp4Header();
		com.easyvpn.main.Packet.TCPHeader TCPHeader = packet.getTcpHeader();
		if (Ip4Header == null || TCPHeader == null) {
			System.out.println("process packet error: bad packet");
			return;
		}
		InetAddress destIp = Ip4Header.destinationAddress;
		int destPort = TCPHeader.destinationPort;
		InetAddress srcIp = Ip4Header.sourceAddress;
		int srcPort = TCPHeader.sourcePort;

		// byte[] data = new byte[packet.getPlayLoadSize()];
		// System.arraycopy(packet.backingBuffer.array(), offset, data, 0, dataSize);

		int index = -1;
		for (int i = 0; i < tcpProxys.size(); i++) {
			TcpProxy2 proxy = tcpProxys.get(i);
			if (proxy.ip.equals(recePacket.getAddress()) && proxy.port == recePacket.getPort()
					&& proxy.srcIp.equals(srcIp) && proxy.srcPort == srcPort && proxy.destIp.equals(destIp)
					&& proxy.destPort == destPort) {
				if (index == 0)
					System.out.println("TcpProxy exist");
				proxy.write(packet);

				index = i;
				break;
			}
		}

		if (index == -1) {
			TcpProxy2 proxy = new TcpProxy2(this, packet, recePacket.getAddress(), recePacket.getPort(), srcIp, srcPort,
					destIp, destPort);
			tcpProxys.add(proxy);
			proxy.write(packet);

			// System.out.println("accept, total udp socket: " + udpProxys.size());
		} else {
			
		}
	}

	public void processUdpPacket(DatagramPacket recePacket, Packet packet) {
		clearExpireProxy();
		if (udpProxys.size() > Config.MAX_CONNECT) {
			System.out.println("UdpProxy connect max");
			return;
		}
		IP4Header Ip4Header = packet.getIp4Header();
		com.easyvpn.main.Packet.UDPHeader UDPHeader = packet.getUdpHeader();
		if (Ip4Header == null || UDPHeader == null) {
			System.out.println("process packet error: bad packet");
			return;
		}
		InetAddress destIp = Ip4Header.destinationAddress;
		int destPort = UDPHeader.destinationPort;
		InetAddress srcIp = Ip4Header.sourceAddress;
		int srcPort = UDPHeader.sourcePort;

		int dataSize = packet.getPlayLoadSize();
		int offset = packet.backingBuffer.limit() - dataSize;
		byte[] data = new byte[packet.getPlayLoadSize()];
		System.arraycopy(packet.backingBuffer.array(), offset, data, 0, dataSize);
		DatagramPacket sendPacket = new DatagramPacket(data, dataSize, destIp, destPort);
		// System.out.println(packet);

		int index = -1;
		for (int i = 0; i < udpProxys.size(); i++) {
			UdpProxy proxy = udpProxys.get(i);
			if (proxy.ip.equals(recePacket.getAddress()) && proxy.port == recePacket.getPort()
					&& proxy.srcIp.equals(srcIp) && proxy.srcPort == srcPort && proxy.destIp.equals(destIp)
					&& proxy.destPort == destPort) {
				proxy.write(sendPacket);
				index = i;
				break;
			}
		}

		if (index == -1) {
			UdpProxy proxy = new UdpProxy(this, packet, recePacket.getAddress(), recePacket.getPort(), srcIp, srcPort,
					destIp, destPort);
			udpProxys.add(proxy);
			proxy.write(sendPacket);
			// System.out.println("accept, total udp socket: " + udpProxys.size());
		} else {
			// System.out.println("UdpProxy exist");
		}
	}

	public void clearExpireProxy() {
		synchronized (udpProxys) {
			for (int i = 0; i < udpProxys.size(); i++) {
				if (udpProxys.get(i).isClose()) {
					udpProxys.remove(i);
					i--;
				}
			}
		}
	}

	public void clearAllProxy() {
		synchronized (udpProxys) {
			for (int i = 0; i < udpProxys.size(); i++) {
				udpProxys.get(i).close();
			}
			udpProxys.clear();
		}
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		System.out.println("Udp listen on: " + datagramSocket.getLocalAddress() + ":" + datagramSocket.getLocalPort());
		while (!isClose()) {
			byte[] receBuf = new byte[Config.MUTE];
			DatagramPacket recePacket = new DatagramPacket(receBuf, receBuf.length);
			try {
				datagramSocket.receive(recePacket);
				processPacket(recePacket);
				// System.out.println(recePacket.getAddress()+""+recePacket.getPort());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}
}
