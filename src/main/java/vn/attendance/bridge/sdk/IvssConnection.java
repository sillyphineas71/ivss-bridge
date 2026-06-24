package vn.attendance.bridge.sdk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import vn.attendance.lib.NetSDKLib;
import vn.attendance.lib.ToolKits;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Owns the NetSDK lifecycle: init -> login -> attach event listener.
 * Adapted (thin) from sep490_ams_be ServerServiceImpl.loginServer / InitTest / logout.
 *
 * Key facts carried over from the proven project:
 *   - IVSS uses CLIENT_LoginWithHighLevelSecurity on port 37777 (NOT http).
 *   - Auto-reconnect via HaveReconnectCallback keeps the session alive.
 *
 * Uses bridge-local trimmed DisconnectCallback / HaveReconnectCallback (no AMS deps).
 */
@Service
public class IvssConnection {

    @Value("${device.ip}")        private String deviceIp;
    @Value("${device.port}")      private int devicePort;
    @Value("${device.username}")  private String deviceUsername;
    @Value("${device.password}")  private String devicePassword;

    @Autowired private ServerInstance server;
    @Autowired private EventListener eventListener;
    @Autowired private DisconnectCallback disconnectCallback;
    @Autowired private HaveReconnectCallback haveReconnectCallback;

    @PostConstruct
    public void start() {
        initSdk();
        boolean ok = login(deviceIp, devicePort, deviceUsername, devicePassword);
        if (ok) {
            // Begin streaming face-detect / face-recognition events from all channels.
            eventListener.attach();
        } else {
            System.err.println("[IvssConnection] initial login failed; REST /login can retry once the device is reachable.");
        }
    }

    private void initSdk() {
        server.getNetSdk().CLIENT_Init(disconnectCallback, null);
        server.getNetSdk().CLIENT_SetAutoReconnect(haveReconnectCallback, null);
    }

    /** Login (or re-login). Returns true on success. */
    public synchronized boolean login(String ip, int port, String user, String pass) {
        logout();

        NetSDKLib.NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY in =
                new NetSDKLib.NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY();
        in.szIP = ip.getBytes();
        in.nPort = port;
        in.szUserName = user.getBytes();
        in.szPassword = pass.getBytes();

        NetSDKLib.NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY out =
                new NetSDKLib.NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY();

        NetSDKLib.LLong handle =
                server.getNetSdk().CLIENT_LoginWithHighLevelSecurity(in, out);

        if (handle.longValue() != 0) {
            server.setLoginHandle(handle);
            server.setDeviceInfo(out.stuDeviceInfo);
            server.setBConnect(true);
            System.out.println("[IvssConnection] login success -> " + ip + ":" + port);
            return true;
        }
        server.setBConnect(false);
        System.err.printf("[IvssConnection] login FAILED, lastError=%s%n", ToolKits.getErrorCode());
        return false;
    }

    public boolean isConnected() {
        return server.isConnect();
    }

    @PreDestroy
    public void logout() {
        if (server.isConnect()) {
            eventListener.detach();
            server.getNetSdk().CLIENT_Logout(server.getLoginHandle());
            server.setLoginHandle(new NetSDKLib.LLong(0));
            server.setBConnect(false);
        }
    }
}
