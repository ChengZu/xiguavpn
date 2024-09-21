package com.vcvnc.xiguavpn;

import java.util.ArrayList;

public class TcpTunnel {
	//客户端
	private Client client = null;
	//保存tcp代理数组
	private ArrayList<TcpProxy> tcpProxys = new ArrayList<TcpProxy>();

	public TcpTunnel(Client client) {
		this.client = client;
	}
	
	public void processPacket(byte[] packet, int size)  {
		clearCloseProxy();
		if (tcpProxys.size() > Config.CLIENT_MAX_TCPPROXY) {
			int clearNum =  clearExpireProxy();
			if(clearNum > 0) System.out.printf("Client(%d) tcpProxys size: %d max, clean proxy num: %d.\n", client.id, tcpProxys.size(), clearNum);
		}
		TCPHeader tcpHeader = new TCPHeader(packet, IPHeader.IP4_HEADER_SIZE);
		TcpProxy tcpProxy = null;
		//检查该代理是否创建
		for (int i = 0; i < tcpProxys.size(); i++) {
			if (tcpProxys.get(i).equal(packet)) {
				tcpProxy = tcpProxys.get(i);
				break;
			}
		}
		
		int flags = tcpHeader.getFlag();
		if(tcpProxy == null && (flags & TCPHeader.SYN) != TCPHeader.SYN) {
			if((flags & TCPHeader.RST) != TCPHeader.RST){
				sendRSTPacket(packet);
			}
			return;
		}
		
		if((flags & TCPHeader.ACK) == TCPHeader.ACK){
			tcpProxy.processACKPacket(packet);
		}
		
		if((flags & TCPHeader.SYN) == TCPHeader.SYN){
			handleSYN(packet, tcpProxy);
		}
		
		if((flags & TCPHeader.RST) == TCPHeader.RST){
			tcpProxy.processRSTPacket(packet);
		}
		
		if((flags & TCPHeader.PSH) == TCPHeader.PSH){
			tcpProxy.processPSHPacket(packet);
		}
		
		if((flags & TCPHeader.URG) == TCPHeader.URG){
			tcpProxy.processURGPacket(packet);
		}
		
		if((flags & TCPHeader.FIN) == TCPHeader.FIN){
			tcpProxy.processFINPacket(packet);
		}

	}

	public void handleSYN(byte[] packet, TcpProxy tcpProxy) {
		if (tcpProxy == null) {
			tcpProxy = new TcpProxy(client, packet);
			tcpProxys.add(tcpProxy);
			tcpProxy.processSYNPacket(packet);
		} else {
			tcpProxy.close();
			tcpProxys.remove(tcpProxy);
			tcpProxy = new TcpProxy(client, packet);
			tcpProxys.add(tcpProxy);
			tcpProxy.processSYNPacket(packet);
		}
		//System.out.println(tcpProxy.id + "SYN flag");
	}
	
	void sendRSTPacket(byte[] packet){
		IPHeader ipHeader = new IPHeader(packet, 0);
		TCPHeader tcpHeader = new TCPHeader(packet, IPHeader.IP4_HEADER_SIZE);
		int srcIp = ipHeader.getSourceIP();
		short srcPort = tcpHeader.getSourcePort();
		int destIp = ipHeader.getDestinationIP();
		short destPort = tcpHeader.getDestinationPort();
		int seq = tcpHeader.getAckID() - 1;
		int ack = tcpHeader.getSeqID() + 1;
		
		ipHeader.setSourceIP(destIp);
		tcpHeader.setSourcePort(destPort);
		ipHeader.setDestinationIP(srcIp);
		tcpHeader.setDestinationPort(srcPort);
		ipHeader.setFlagsAndOffset((short) 0);
		tcpHeader.setWindow((short) 65535);
		tcpHeader.setUrp((short) 0);
		tcpHeader.setHeaderLength((byte) 20);
		
		tcpHeader.setFlag((byte) TCPHeader.RST);
		tcpHeader.setSeqID(seq);
		tcpHeader.setAckID(ack);
		ipHeader.setTotalLength((short) TcpProxy.HEADER_SIZE);
		tcpHeader.ComputeTCPChecksum(ipHeader);
		client.sendToClient(packet, 0, (IPHeader.IP4_HEADER_SIZE + TCPHeader.TCP_HEADER_SIZE));
	}

	/*
	 * 清除已关闭代理
	 */
	public int clearCloseProxy() {
		int ret = 0;
		for (int i = 0; i < tcpProxys.size(); i++) {
			if (tcpProxys.get(i).isClose()) {
				tcpProxys.remove(i);
				i--;
				ret++;
			}
		}
		return ret;
	}
	
	/*
	 * 清除不活动代理
	 */
	public int clearExpireProxy() {
		int ret = 0;
		for (int i = 0; i < tcpProxys.size(); i++) {
			if (tcpProxys.get(i).isExpire()) {
				tcpProxys.get(i).close();
				tcpProxys.remove(i);
				i--;
				ret++;

			}
		}
		return ret;
	}
	
	/*
	 * 清除所有代理
	 */
	public void clearAllProxy() {
		for (int i = 0; i < tcpProxys.size(); i++) {
			tcpProxys.get(i).close();
		}
		tcpProxys.clear();
	}

}
