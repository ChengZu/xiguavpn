package com.easyvpn.main;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class TcpServer implements Runnable {
	private ServerSocket server = null;
	private ArrayList<TcpClient> clientList = new ArrayList<TcpClient>();
	private boolean isRunning = false;
	private Thread thread;

	public TcpServer() {
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
		for (TcpClient client : clientList) {
			client.close(true);
		}
	}

	@Override
	public void run() {
		isRunning = true;
		System.out.println("Tcp listen on: " + server.getLocalSocketAddress());
		while (isRunning) {
			try {
				Socket clientSocket = server.accept();
				//System.out.println("accept, total tcp socket: " + clientList.size());
				for (int i = 0; i < clientList.size(); i++) {
					if (clientList.get(i).isClose()) {
						clientList.remove(i);
						i--;
					}
				}
				if (clientList.size() < Config.MAX_CONNECT) {
					TcpClient client = new TcpClient(clientSocket);
					clientList.add(client);
				} else {
					clientSocket.close();
					System.out.println("TcpProxy connect max");
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
