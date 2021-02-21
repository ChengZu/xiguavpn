package com.easyvpn.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

public class TcpProxy implements Runnable {
	private TcpClient tcpClient;
	private int ip;
	private int port;
	private Socket socket;
	private InputStream is;
	private OutputStream os;

	private boolean isClose = true;
	private Thread thread;

	public TcpProxy(TcpClient vpnClient, int ip, int port) {
		this.tcpClient = vpnClient;
		this.ip = ip;
		this.port = port;
		boolean init = false;
		try {
			socket = new Socket(CommonMethods.ipIntToString(ip), port);
			is = socket.getInputStream();
			os = socket.getOutputStream();
			init = true;
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Connect error: " + CommonMethods.ipIntToString(this.ip) + ":" + this.port);
			// e.printStackTrace();
		}
		if (init) {
			thread = new Thread(this);
			thread.start();
			isClose = false;
		} else {
			close(true);
		}

	}

	public void write(byte[] packet, int offset, int size) {
		// printfPacket(packet, offset, size);
		try {
			os.write(packet, offset, size);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
	}

	public void close(boolean closeChild) {
		isClose = true;
		try {
			if (closeChild) {
				tcpClient.close(false);
			}
			if (socket != null) {
				is.close();
				os.close();
				socket.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public boolean isClose() {
		if (socket == null)
			return true;
		return socket.isClosed() || isClose;
	}

	public void printfPacket(byte[] packet, int offset, int size) {
		System.out.println(CommonMethods.ipIntToString(ip) + ":" + port + " size:" + size + "\r\n");
		for (int i = offset; i < size - offset; i++) {
			System.out.printf("0x%s ", Integer.toHexString(packet[i]));
		}
		System.out.println();
	}

	@Override
	public void run() {
		try {
			int size = 0;
			byte[] packet = new byte[Config.MUTE];
			while (size != -1 && !isClose()) {
				size = is.read(packet);
				if (size > 0) {
					// printfPacket(packet, 0, size);
					tcpClient.write(packet, 0, size);
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
