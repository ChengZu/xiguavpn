package com.easyvpn.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

public class TcpProxy implements Runnable {
	private MyConnect connect;
	private int ip;
	private int port;
	private Socket socket;
	private InputStream is;
	private OutputStream os;

	private boolean isClose = false;
	private Thread thread;

	public TcpProxy(MyConnect connect, int ip, int port) {
		this.connect = connect;
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
			System.out.println("bad address:" + CommonMethods.ipIntToString(ip) + ":" + port);
			// e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Connect error: " + CommonMethods.ipIntToString(this.ip) + ":" + this.port);
			// e.printStackTrace();
		}
		if (init) {
			thread = new Thread(this);
			thread.start();
		} else {
			close(true);
		}

	}

	public void writeToServer(byte[] packet, int offset, int size) {
		try {
			os.write(packet, offset, size);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			close(true);
			e.printStackTrace();
		}
	}

	public void close(boolean closeBrother) {
		isClose = true;
		try {
			if (closeBrother) {
				connect.close(false);
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

	@Override
	public void run() {	
		try {
			int size = 0;
			byte[] packet = new byte[Config.MUTE];
			while (size != -1 && !isClose()) {
				size = is.read(packet);
				if (size > 0) {
					connect.sendToClient(packet, 0, size);
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
