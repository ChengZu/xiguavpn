package com.easyvpn.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class TcpClient implements Runnable {
	private Socket socket;
	private int ip;
	private int port;
	private InputStream is;
	private OutputStream os;
	private TcpProxy tcpProxy;
	private boolean isClose = true;
	private Thread thread;

	public TcpClient(Socket socket) {
		this.socket = socket;
		try {
			is = socket.getInputStream();
			os = socket.getOutputStream();
			isClose = false;
			thread = new Thread(this);
			thread.start();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void close(boolean closeChild) {
		isClose = true;
		try {
			if (tcpProxy != null && closeChild) {
				tcpProxy.close(false);
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

	public void write(byte[] packet, int offset, int size) {
		try {
			os.write(packet, offset, size);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		try {
			int size = 0;
			byte[] packet = new byte[Config.MUTE];
			size = is.read(packet);
			if (size > 0) {
				IPHeader header = new IPHeader(packet, 0);
				ip = header.getSourceIP();
				port = header.getDestinationIP();
				tcpProxy = new TcpProxy(this, ip, port);
				if(tcpProxy.isClose()) {
					close(true);
					return;
				}
			}

			while (size != -1 && !isClose()) {
				size = is.read(packet);
				if (size > 0) {
					tcpProxy.write(packet, 0, size);
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		} finally {
			close(true);
		}
	}

}
