package com.easyvpn.main;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class MyServerSocket implements Runnable {
	private ServerSocket server = null;
	private ArrayList<MyConnect> myConnects = new ArrayList<MyConnect>();
	private boolean isRunning = true;
	private Thread thread;

	public MyServerSocket() {
		try {
			server = new ServerSocket(Config.PORT);
			thread = new Thread(this);
			thread.start();
		} catch (IOException e) {
			e.printStackTrace();;
		}
	}

	public void close() throws IOException {
		isRunning = false;
		server.close();
		for (MyConnect connect : myConnects) {
			connect.close(true);
		}
	}

	@Override
	public void run() {
		isRunning = true;
		System.out.println("server listen on: " + server.getLocalSocketAddress());
		while (isRunning) {
			try {
				Socket clientSocket = server.accept();
				for (int i = 0; i < myConnects.size(); i++) {
					if (myConnects.get(i).isClose()) {
						myConnects.remove(i);
						i--;
					}
				}
				if (myConnects.size() < Config.MAX_CONNECT) {
					MyConnect connect = new MyConnect(clientSocket);
					myConnects.add(connect);
				} else {
					clientSocket.close();
					System.out.println("connect max");
				}
				//System.out.println("accept, total tcp socket: " + clientList.size());

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
