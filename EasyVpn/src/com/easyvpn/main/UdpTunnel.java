package com.easyvpn.main;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class UdpTunnel {
	private MyConnect connect = null;
	private boolean isClose = false;
	private ArrayList<UdpProxy> udpProxys = new ArrayList<>();
	private byte[] cacheBytes = null;
	private boolean haveCacheBytes = false;
	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_RED = "\u001B[31m";

	public UdpTunnel(MyConnect connect) {
		this.connect = connect;
	}

	public void close() {
		isClose = true;
		for (UdpProxy udpProxy : udpProxys) {
			udpProxy.close();
		}
	}

	public boolean isClose() {
		return isClose;
	}

	public void sendToClient(byte[] packet, int offset, int size) {
		connect.sendToClient(packet, offset, size);
	}

	synchronized public void processRecvPacket(byte[] bytes, int size) throws UnknownHostException {

		if (this.haveCacheBytes) {
			byte[] data = new byte[this.cacheBytes.length + size];
			System.arraycopy(this.cacheBytes, 0, data, 0, this.cacheBytes.length);
			System.arraycopy(bytes, 0, data, this.cacheBytes.length, size);
			bytes = data;

			// System.out.println("#####recv size: " + size + " cache size:
			// "+this.cacheBytes.length);

			size = this.cacheBytes.length + size;
			this.cacheBytes = null;
			this.haveCacheBytes = false;

		}
		if (size < UdpProxy.HEADER_SIZE) {
			byte[] data = new byte[size];
			System.arraycopy(bytes, 0, data, 0, size);
			this.cacheBytes = data;
			this.haveCacheBytes = true;
			// System.out.println("bad packet size: "+ size +", CacheBytes");
			return;
		}

		ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, 0, size);
		byteBuffer.limit(size);
		Packet packet = new Packet(byteBuffer);

		Packet.IP4Header Ip4Header = packet.getIp4Header();
		Packet.UDPHeader UDPHeader = packet.getUdpHeader();
		if (Ip4Header == null || UDPHeader == null) {
			System.out.println(ANSI_RED + "#####process packet error: bad packet" + ANSI_RESET);
			close();
			return;
		}

		// System.out.println("client send size: "+size+"---------packet: "+packet);

		if (size > packet.getIp4Header().totalLength) {
			packetGiveToProxy(bytes, packet.getIp4Header().totalLength, packet);
			int nextDataSize = size - packet.getIp4Header().totalLength;
			byte[] data = new byte[nextDataSize];
			System.arraycopy(bytes, packet.getIp4Header().totalLength, data, 0, nextDataSize);
			processRecvPacket(data, nextDataSize);
		} else if (size == packet.getIp4Header().totalLength) {
			packetGiveToProxy(bytes, size, packet);
		} else if (size < packet.getIp4Header().totalLength) {
			byte[] data = new byte[size];
			System.arraycopy(bytes, 0, data, 0, size);
			this.cacheBytes = data;
			this.haveCacheBytes = true;
		}

	}

	public void packetGiveToProxy(byte[] bytes, int size, Packet packet) {
		clearExpireProxy();
		if (udpProxys.size() > Config.MAX_CONNECT) {
			System.out.println("UdpProxy connect max");
			return;
		}

		InetAddress srcIp = packet.getIp4Header().sourceAddress;
		InetAddress destIp = packet.getIp4Header().destinationAddress;
		int srcPort = packet.getUdpHeader().sourcePort;
		int destPort = packet.getUdpHeader().destinationPort;

		int dataSize = size - UdpProxy.HEADER_SIZE;
		int offset = UdpProxy.HEADER_SIZE;
		byte[] data = new byte[dataSize];
		System.arraycopy(bytes, offset, data, 0, dataSize);
		DatagramPacket clientPacket = new DatagramPacket(data, dataSize, destIp, destPort);

		int index = -1;
		UdpProxy proxy = null;
		for (int i = 0; i < udpProxys.size(); i++) {
			proxy = udpProxys.get(i);
			if (proxy.srcIp.equals(srcIp) && proxy.destIp.equals(destIp) && proxy.srcPort == srcPort
					&& proxy.destPort == destPort) {

				index = i;
				break;
			}
		}

		if (index == -1) {
			proxy = new UdpProxy(this, packet, srcIp, destIp, srcPort, destPort);
			udpProxys.add(proxy);
		}
		proxy.sendToServer(clientPacket);

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
			for (UdpProxy udpProxy : udpProxys) {
				udpProxy.close();
			}
			udpProxys.clear();
		}
	}
}
