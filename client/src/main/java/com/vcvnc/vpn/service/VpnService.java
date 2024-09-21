package com.vcvnc.vpn.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import com.vcvnc.vpn.utils.ProxyConfig;
import com.vcvnc.vpn.tunnel.RemoteTunnel;
import com.vcvnc.vpn.tcpip.IPHeader;
import com.vcvnc.vpn.tcpip.TCPHeader;
import com.vcvnc.vpn.tcpip.UDPHeader;
import com.vcvnc.vpn.utils.AppDebug;
import com.vcvnc.vpn.utils.DebugLog;
import com.vcvnc.vpn.utils.VpnServiceHelper;
import com.vcvnc.xiguavpn.R;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class VpnService extends android.net.VpnService implements Runnable {

    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything
    private static int ID;
    private boolean IsRunning = false;
    private Thread mVPNThread;
    private ParcelFileDescriptor mVPNInterface;
    private FileOutputStream mVPNOutputStream;

    private byte[] mPacket;
    private FileInputStream in;
    RemoteTunnel remoteTunnel = null;
    private SharedPreferences sp;
    private String VPN_SP_NAME = "vpn_sp_name";
    private String DEFAULT_PACKAGE_ID = "default_package_id";

    public VpnService() {
        ID++;
        DebugLog.i("New VPNService(%d)\n", ID);
    }

    //启动Vpn工作线程
    @Override
    public void onCreate() {
        DebugLog.i("VPNService(%s) created.\n", ID);
        mPacket = new byte[ProxyConfig.MUTE];
        sp = getSharedPreferences(VPN_SP_NAME, Context.MODE_PRIVATE);
        VpnServiceHelper.onVpnServiceCreated(this);
        mVPNThread = new Thread(this, "VPNServiceThread");
        mVPNThread.start();
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    //停止Vpn工作线程
    @Override
    public void onDestroy() {
        DebugLog.i("VPNService(%s) destroyed.\n", ID);
        if (mVPNThread != null) {
            mVPNThread.interrupt();
        }
        VpnServiceHelper.onVpnServiceDestroy();
        super.onDestroy();
    }


    private void runVPN() throws Exception {
        mVPNInterface = establishVPN();
        setVpnRunningStatus(true);
        ProxyConfig.Instance.onVpnStart(this);
        startStream();
    }

    private void startStream() throws Exception {
        int size = 0;
        mVPNOutputStream = new FileOutputStream(mVPNInterface.getFileDescriptor());
        in = new FileInputStream(mVPNInterface.getFileDescriptor());
        while (size != -1 && IsRunning) {
            size = in.read(mPacket);
            if (size > 0) {
                onIPPacketReceived(mPacket, size);
            }
        }
        in.close();
        mVPNOutputStream.close();
        mVPNOutputStream = null;
    }

    public void write(byte[] data, int offset, int size) {
        boolean badPacket = false;
        IPHeader ipHeader = new IPHeader(data, offset);
        int oldCrc = ipHeader.getCrc();
        ipHeader.ComputeIPChecksum();
        int newCrc = ipHeader.getCrc();
        if(oldCrc != newCrc){
            badPacket = true;
        }
        if(!badPacket) {
            switch (ipHeader.getProtocol()) {
                case IPHeader.TCP:
                    TCPHeader tcpHeader = new TCPHeader(data, offset + IPHeader.IP4_HEADER_SIZE);
                    oldCrc = tcpHeader.getCrc();
                    tcpHeader.ComputeTCPChecksum(ipHeader);
                    newCrc = tcpHeader.getCrc();
                    if(oldCrc != newCrc){
                        badPacket = true;
                    }
                    break;
                case IPHeader.UDP:
                    UDPHeader udpHeader = new UDPHeader(data, offset + IPHeader.IP4_HEADER_SIZE);
                    oldCrc = udpHeader.getCrc();
                    udpHeader.ComputeUDPChecksum(ipHeader);
                    newCrc = udpHeader.getCrc();
                    if(oldCrc != newCrc){
                        badPacket = true;
                    }
                    break;
                default:
                    DebugLog.i("未知IP包 %s.\n", ipHeader);
                    badPacket = true;
                    break;
            }
        }
        if(badPacket){
            DebugLog.i("VpnService recived bad packet");
            setVpnRunningStatus(false);
            ProxyConfig.errorMsg = getString(R.string.rev_bad_packet);
            return;
        }

        try {
            if(mVPNOutputStream != null)
                mVPNOutputStream.write(data, offset, size);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onIPPacketReceived(byte[] bytes, int size) throws IOException {
        IPHeader ipHeader = new IPHeader(bytes, 0);
        switch (ipHeader.getProtocol()) {
            case IPHeader.TCP:
            case IPHeader.UDP:
                remoteTunnel.processPacket(bytes, size);
                break;
            default:
                DebugLog.i("未知IP包 %s.\n", ipHeader);
                break;
        }
    }

    private void waitUntilPrepared() {
        while (prepare(this) != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                if (AppDebug.IS_DEBUG) {
                    e.printStackTrace();
                }
                DebugLog.e("waitUntilPrepared catch an exception %s\n", e);
            }
        }
    }

    private ParcelFileDescriptor establishVPN() throws Exception {
        Builder builder = new Builder();
        builder.setMtu(ProxyConfig.MUTE);
        String selectPackage = sp.getString(DEFAULT_PACKAGE_ID, null);
        DebugLog.i("setMtu: %d\n", ProxyConfig.MUTE);

        ProxyConfig.IPAddress ipAddress = ProxyConfig.Instance.getDefaultLocalIP();
        builder.addAddress(ipAddress.Address, ipAddress.PrefixLength);
        DebugLog.i("addAddress: %s/%d\n", ipAddress.Address, ipAddress.PrefixLength);

        builder.addRoute(VPN_ROUTE, 0);

        builder.addDnsServer(ProxyConfig.DNS_FIRST);
        builder.addDnsServer(ProxyConfig.DNS_SECOND);
        try {
            if (selectPackage != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder.addAllowedApplication(selectPackage);
                    builder.addAllowedApplication(getPackageName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        builder.setSession(getString(R.string.app_name));
        ParcelFileDescriptor pfdDescriptor = builder.establish();
        return pfdDescriptor;
    }

    @Override
    public void run() {
        try {
            DebugLog.i("VPNService(%s) work thread is Running...\n", ID);
            waitUntilPrepared();
            //启动TCP代理服务
            remoteTunnel = new RemoteTunnel(this);
            remoteTunnel.start();
            if(!remoteTunnel.isClose()){
                runVPN();
            }
        } catch (InterruptedException e) {
            if (AppDebug.IS_DEBUG) {
                e.printStackTrace();
            }
            DebugLog.e("VpnService run catch an exception %s.\n", e);
        } catch (Exception e) {
            if (AppDebug.IS_DEBUG) {
                e.printStackTrace();
            }
            DebugLog.e("VpnService run catch an exception %s.\n", e);
        } finally {
            DebugLog.i("VpnService terminated");
            dispose();
            setVpnRunningStatus(false);
            ProxyConfig.Instance.onVpnEnd(this);
        }
    }

    public synchronized void dispose() {
        try {
            mVPNInterface.close();
            remoteTunnel.close(ProxyConfig.errorMsg);
            stopSelf();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean vpnRunningStatus() {
        return IsRunning;
    }

    public void setVpnRunningStatus(boolean isRunning) {
        IsRunning = isRunning;
    }
}
