package com.vcvnc.vpn.tunnel;


import com.vcvnc.vpn.utils.ProxyConfig;
import com.vcvnc.vpn.service.VpnService;
import com.vcvnc.vpn.tcpip.IPHeader;
import com.vcvnc.vpn.utils.DebugLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import com.vcvnc.xiguavpn.R;


public class RemoteTunnel implements Runnable {
    private static final String TAG = RemoteTunnel.class.getSimpleName();
    private final VpnService vpnService;
    private Socket socket;
    private InputStream is;
    private OutputStream os;
    private boolean isClose = false;
    private byte[] cacheBytes;
    private boolean haveCacheBytes = false;
    String ipAndPort;

    public RemoteTunnel(VpnService vpnService) {
        this.vpnService = vpnService;
    }

    public void start() {
        connectServer();
        Thread thread = new Thread(this, TAG);
        thread.start();
    }

    public boolean connectServer() {
        InetAddress destinationAddress = null;
        try {
            destinationAddress = InetAddress.getByName(ProxyConfig.serverIp);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        int destinationPort = ProxyConfig.serverPort;

        ipAndPort = destinationAddress.toString()+":"+ProxyConfig.serverPort;
        DebugLog.iWithTag(TAG, "RemoteTunnel connecting server:%s", ipAndPort);

        try {
            socket =  new Socket(ProxyConfig.serverIp, destinationPort);
            is = socket.getInputStream();
            os = socket.getOutputStream();
            vpnService.protect(socket);
            isClose = false;

            byte[] header = new byte[IPHeader.IP4_HEADER_SIZE];
            IPHeader ipheader = new IPHeader(header, 0);
            ipheader.setHeaderLength(IPHeader.IP4_HEADER_SIZE);
            ipheader.setSourceIP(ProxyConfig.userName);
            ipheader.setDestinationIP(ProxyConfig.userPwd);
            ipheader.setProtocol((byte) IPHeader.TCP);
            //发送头个用户验证包
            os.write(header, 0, IPHeader.IP4_HEADER_SIZE);

            DebugLog.iWithTag(TAG, "RemoteTunnel connect succeed.");

        } catch (IOException e) {
            DebugLog.dWithTag(TAG, "RemoteTunnel init false.");
            close(vpnService.getString(R.string.can_not_connect_server));

            return false;
        }
        return true;
    }

    //发送给服务器
    public void processPacket(byte[] bytes, int size) {
        try {
            os.write(bytes, 0, size);
        } catch (IOException e) {
            DebugLog.wWithTag(TAG, "Network write error: %s %s", ipAndPort, e);
            close(vpnService.getString(R.string.send_packet_error));
        }
    }

    public void close(String errMsg) {
        if(isClose) return;
        isClose = true;
        ProxyConfig.errorMsg = errMsg;
        vpnService.setVpnRunningStatus(false);
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

    synchronized public void processRecvPacket(byte[] bytes, int size) {
        if(this.haveCacheBytes) {
            byte[] data = new byte[this.cacheBytes.length + size];
            System.arraycopy(this.cacheBytes, 0, data, 0, this.cacheBytes.length);
            System.arraycopy(bytes, 0, data, this.cacheBytes.length, size);
            bytes = data;
            size = this.cacheBytes.length + size;
            this.haveCacheBytes = false;

        }
        if (size < IPHeader.IP4_HEADER_SIZE) {
            byte[] data = new byte[size];
            System.arraycopy(bytes, 0, data, 0, size);
            this.cacheBytes = data;
            this.haveCacheBytes = true;
            return;
        }

        IPHeader IpHeader = new IPHeader(bytes, 0);
        int totalLength = IpHeader.getTotalLength() & 0xFFFFFFFF;
        if(totalLength > ProxyConfig.MUTE){
            close(vpnService.getString(R.string.rev_bad_packet));
        }
        if(size > totalLength){
            vpnService.write(bytes, 0, totalLength);
            int nextDataSize = size - totalLength;
            byte[] data = new byte[nextDataSize];
            System.arraycopy(bytes, totalLength, data, 0, nextDataSize);
            processRecvPacket(data, nextDataSize);
        }else if(size == totalLength){
            vpnService.write(bytes, 0, size);
        }else if(size < totalLength){
            byte[] data = new byte[size];
            System.arraycopy(bytes, 0, data, 0, size);

            this.cacheBytes = data;
            this.haveCacheBytes = true;
        }
    }

    @Override
    public void run() {
        try {
            int size = 0;
            while (size != -1 && !isClose()) {
                byte[] bytes = new byte[ProxyConfig.MUTE];
                size = is.read(bytes);
                if (size > 0) {
                    processRecvPacket(bytes, size);
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            //e.printStackTrace();
        }
        close(vpnService.getString(R.string.connect_abort));
    }

}
