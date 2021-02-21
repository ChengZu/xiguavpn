package com.easyvpn.main;

public class EasyVpn {
	public static void main(String args[]) {
		if (args.length > 0) {
			Config.PORT = Integer.parseInt(args[0]);
		}
		new TcpServer();
		new UdpServer();

	}

}
