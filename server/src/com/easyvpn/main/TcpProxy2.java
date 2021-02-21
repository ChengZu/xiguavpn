package com.easyvpn.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TcpProxy2 implements Runnable {
	public static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE;
	
	public static final int SYN_ACK = 1;
	public static final int FIN_ACK = 2;
	public UdpServer udpServer;
	public long lastRefreshTime;

	private Packet packet;
	public InetAddress ip;
	public int port;
	public InetAddress srcIp;
	public int srcPort;
	public InetAddress destIp;
	public int destPort;
	private byte[] recvBuffer = new byte[65536];
	private byte[] writeBuffer = new byte[65536];
	private long recvBufferSize = 0;
	private long writeBufferSize = 0;
	private long recvBufferPos = 0;
	private long writeBufferPos = 0;
	private int buffermax = 0xffff;
	private byte flags;
	private long sequenceNum;
	private long readSequenceNum;
	private long readAckNum;
	private int bufferSize;
	private Socket socket;
	private InputStream is;
	private OutputStream os;

	private boolean isClose = true;
	private boolean isConnect = false;
	private boolean onConnect = false;
	private boolean waitAck = false;
	private boolean recvAck = false;
	private int waitAckType = 0;
	private Thread thread;

	public static int id1 = 0;
	public int id = 0;
	public static int log = 1;

	private ArrayList<DoubleLong> packetSeq = new ArrayList<>();

	public TcpProxy2(UdpServer udpServer, Packet packet, InetAddress ip, int port, InetAddress srcIp, int srcPort,
			InetAddress destIp, int destPort) {
		packet.swapSourceAndDestination();
		this.udpServer = udpServer;
		this.packet = packet;
		this.ip = ip;
		this.port = port;
		this.srcIp = srcIp;
		this.srcPort = srcPort;
		this.destIp = destIp;
		this.destPort = destPort;
		id1++;
		id = id1;

	}

	public void connect() {
		boolean init = false;
		try {
			socket = new Socket(destIp, destPort);
			is = socket.getInputStream();
			os = socket.getOutputStream();
			init = true;
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			if (id == log)
				System.out.println("Connect error: " + destIp + ":" + destPort);
			// e.printStackTrace();
		}
		if (init) {
			thread = new Thread(this);
			thread.start();
			isClose = false;
			isConnect = true;
			// TODO
		} else {
			close();
		}
	}
	
	
	private void doGetSynAck(Packet packet) {
		if (isClose && !isConnect) {
			recvBufferSize = packet.getTcpHeader().sequenceNumber;
			recvBufferPos = packet.getTcpHeader().sequenceNumber;
			new Thread(new Runnable() {
				@Override
				public void run() {
					connect();
				}

			}).start();
		}
	}

	synchronized public void write(Packet packet) {
		// printfPacket(packet, offset, size);
		lastRefreshTime = System.currentTimeMillis();
		if (this.id == this.log)
			System.out.println("----recv packet----" + packet + packet.playLoadSize);
		Packet newPacket = packet.duplicated();
		newPacket.swapSourceAndDestination();

		com.easyvpn.main.Packet.TCPHeader tcpHeader = packet.getTcpHeader();
		byte flags = TCPHeader.FIN;
		DatagramPacket sendPacket;
		long ackNum;

		if (tcpHeader.isACK()) {
			

			addRecvPacket(packet);
			
			
			

			switch (waitAckType) {
			case TcpProxy2.SYN_ACK:
				recvAck = true;
				doGetSynAck(packet);
				break;
			case TcpProxy2.FIN_ACK:
				recvAck = true;
				close();
				break;
			default:
				break;
			}



		}
		
		
		if (waitAck && !recvAck) {
			if (id == log)
				System.out.println("----wait ack----" + packet);
			// return;
		} else if (waitAck && recvAck) {
			waitAck = false;
			recvAck = false;
			waitAckType = -1;
		}



		if (tcpHeader.isRST()) {

		}

		if (tcpHeader.isSYN()) {
			// onConnect = true;
			waitAck = true;
			recvAck = false;
			waitAckType = TcpProxy2.SYN_ACK;

			flags = TCPHeader.SYN | TCPHeader.ACK;
			ackNum = newPacket.getTcpHeader().sequenceNumber + 1;
			packet.backingBuffer.position(0);
			newPacket.updateTCPBuffer(packet.backingBuffer, flags, sequenceNum, ackNum, 0);
			sendPacket = new DatagramPacket(packet.backingBuffer.array(), packet.backingBuffer.limit(), ip, port);
			udpServer.write(sendPacket);

			if (id == log)
				System.out.println("----write syn----" + newPacket);
		}

		if (tcpHeader.isPSH()) {

			// sendRecvBuffer(packet);
		}

		if (tcpHeader.isURG()) {

		}

		if (tcpHeader.isFIN()) {
			waitAck = true;
			recvAck = false;
			waitAckType = TcpProxy2.FIN_ACK;
			if (id == log)
				System.out.println("----fin close----");
			flags = TCPHeader.ACK;
			ackNum = newPacket.getTcpHeader().sequenceNumber + 1;
			newPacket.updateTCPBuffer(packet.backingBuffer, flags, sequenceNum, ackNum, 0);
			sendPacket = new DatagramPacket(packet.backingBuffer.array(), packet.backingBuffer.limit(), ip, port);
			udpServer.write(sendPacket);
			if (id == log)
				System.out.println("----write fin ack----" + newPacket);

			flags = TCPHeader.FIN | TCPHeader.ACK;
			ackNum = newPacket.getTcpHeader().sequenceNumber + 1;
			newPacket.updateTCPBuffer(packet.backingBuffer, flags, sequenceNum, ackNum, 0);
			sendPacket = new DatagramPacket(packet.backingBuffer.array(), packet.backingBuffer.limit(), ip, port);
			udpServer.write(sendPacket);
			if (id == log)
				System.out.println("----write fin----" + newPacket);
			
		}

		if (isConnect) {
			sendRecvBuffer(packet);
			sendWriteBuffer(packet);
		}

	}

	public void addRecvPacket(Packet packet) {
		if (packet.playLoadSize > 0) {
			packetSeq.add(new DoubleLong(packet.getTcpHeader().sequenceNumber,
					packet.getTcpHeader().sequenceNumber + packet.playLoadSize));

			for (int i = 0; i < packetSeq.size(); i++) {
				for (int j = 0; j < packetSeq.size() - i - 1; j++) {
					if (packetSeq.get(j).start > packetSeq.get(j + 1).start) {
						DoubleLong l = packetSeq.get(j + 1);
						packetSeq.set(j + 1, packetSeq.get(j));
						packetSeq.set(j, l);
					}
				}
			}

			for (int i = 0; i < packetSeq.size() - 1; i++) {
				DoubleLong l1 = packetSeq.get(i);
				DoubleLong l2 = packetSeq.get(i + 1);
				if (l1.end <= l2.start) {
					packetSeq.remove(i);
					l2.start = l1.start;
					i--;
				}
			}

			int offset = (int) (packetSeq.get(0).end & buffermax);

			System.arraycopy(packet.backingBuffer.array(), packet.backingBuffer.limit() - packet.playLoadSize,
					recvBuffer, offset, packet.playLoadSize);

			if (id == log) {
				System.out.println("----add packet--" + offset + "--" + packet.playLoadSize + " start: "
						+ packetSeq.get(0).start + "end: " + packetSeq.get(0).end);
				printfPacket(packet.backingBuffer.array(), packet.backingBuffer.limit() - packet.playLoadSize,
						packet.playLoadSize);
			}
		}

	}

	synchronized public void sendRecvBuffer(Packet packet) {

		if (packetSeq.size() == 0)
			return;
		recvBufferSize = packetSeq.get(0).end;
		if (recvBufferPos < recvBufferSize) {

			if (id == log) {
				System.out.println(
						"----send recv ---- recvBufferPos: " + recvBufferPos + "recvBufferSize: " + recvBufferSize);
			}
			long maxSize = recvBufferSize - recvBufferPos;
			send(recvBuffer, (int) (recvBufferPos & buffermax), (int) maxSize);
			recvBufferPos += maxSize;

		}

	}

	synchronized public void sendWriteBuffer(Packet packet) {
		long seq = packet.getTcpHeader().acknowledgementNumber - 1;
		long max = seq + packet.getTcpHeader().getWindow();

		if (seq < writeBufferSize) {
			if (max > writeBufferSize) {
				max = writeBufferSize & buffermax;
			} else {
				max &= buffermax;
			}
			int size = (int) max;
			byte[] dataCopy = new byte[HEADER_SIZE + size];
			System.arraycopy(writeBuffer, (int) (seq & buffermax), dataCopy, HEADER_SIZE, size);

			ByteBuffer buf = ByteBuffer.wrap(dataCopy);

			Packet newPacket = packet.duplicated();
			byte flags = TCPHeader.ACK | TCPHeader.PSH;
			DoubleLong l1 = packetSeq.get(0);

			newPacket.updateTCPBuffer(buf, flags, writeBufferPos, l1.end + 1, size);
			// TODO
			if (seq < writeBufferPos)
				writeBufferPos += size;
			if (id == log)
				System.out.println("----write send----" + newPacket);
			DatagramPacket sendPacket = new DatagramPacket(dataCopy, dataCopy.length, ip, port);
			udpServer.write(sendPacket);

		}
	}

	public boolean send(byte[] packet, int offset, int size) {

		if (id == log) {
			System.out.println("----send recv ----" + offset + "size: " + size);
			printfPacket(packet, offset, size);
		}
		boolean res = false;
		try {
			os.write(packet, offset, size);
			res = true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return res;
	}

	public void close() {
		if (id == log)
			System.out.println("----close 2----");
		isClose = true;
		try {
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
		// System.out.println(destIp + ":" + destPort + " size:" + size + "\r\n");
		for (int i = offset; i < size + offset; i++) {
			System.out.printf("0x%s ", Integer.toHexString(packet[i]));
		}
		System.out.println();
	}

	@Override
	public void run() {
		try {
			int size = 0;
			byte[] recvbuf = new byte[Config.MUTE];
			while (size != -1 && !isClose()) {
				size = is.read(recvbuf);
				if (size > 0) {
					// printfPacket(packet, 0, size);
					int offset = (int) (writeBufferSize & buffermax);
					System.arraycopy(recvbuf, 0, writeBuffer, offset, size);
					writeBufferSize += size;

				}
				lastRefreshTime = System.currentTimeMillis();

				while ((writeBufferSize - buffermax) > writeBufferPos) {
					Thread.sleep(10);
				}
			}

		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
		} finally {
			close();
		}

	}

	public class DoubleLong {
		public long start;
		public long end;

		public DoubleLong(long start, long end) {
			this.start = start;
			this.end = end;
		}
	}

}
