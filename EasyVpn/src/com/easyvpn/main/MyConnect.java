package com.easyvpn.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class MyConnect implements Runnable {
	private Socket socket;
	private int ip;
	private int port;
	private InputStream is;
	private OutputStream os;
	private TcpProxy tcpProxy = null;
	private UdpTunnel udpTunnel = null;
	private boolean isClose = false;
	private byte protocol = 0;
	private Thread thread;

	public MyConnect(Socket socket) {
		this.socket = socket;
		try {
			is = socket.getInputStream();
			os = socket.getOutputStream();
			thread = new Thread(this);
			thread.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void close(boolean closeBrother) {
		isClose = true;
		try {
			if (tcpProxy != null && closeBrother) {
				tcpProxy.close(false);
			}
			if (udpTunnel != null && closeBrother) {
				udpTunnel.close();
			}
			is.close();
			os.close();
			socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public boolean isClose() {
		return socket.isClosed() || isClose;
	}

	public void sendToClient(byte[] packet, int offset, int size) {
		try {
			os.write(packet, offset, size);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			close(true);
			//e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		try {
			int readSize = 0;
			byte[] readBytes = new byte[Packet.IP4_HEADER_SIZE];
			while (readSize < Packet.IP4_HEADER_SIZE) {
				int size = is.read(readBytes, readSize, Packet.IP4_HEADER_SIZE - readSize);
				if(size == -1) return;
				readSize += size;
			}

			IPHeader header = new IPHeader(readBytes, 0);
			protocol = header.getProtocol();
			if (protocol == IPHeader.TCP) {
				ip = header.getSourceIP();
				port = header.getDestinationIP();
				tcpProxy = new TcpProxy(this, ip, port);
			} else if (protocol == IPHeader.UDP) {
				udpTunnel = new UdpTunnel(this);
			} else {
				System.out.println("fist packet bad header value");
				return;
			}

			int size = 0;
			byte[] packet = new byte[Config.MUTE];
			while (size != -1 && !isClose()) {
				size = is.read(packet);
				if (size > 0) {
					if (protocol == IPHeader.TCP) {
						tcpProxy.writeToServer(packet, 0, size);
					} else if (protocol == IPHeader.UDP) {
						udpTunnel.processRecvPacket(packet, size);
					}	
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
		} finally {
			close(true);
		}
	}

}
