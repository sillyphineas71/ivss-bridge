package vn.attendance.bridge.sdk;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import vn.attendance.lib.NetSDKLib;

/**
 * Holds the live NetSDK handles. Adapted from
 * sep490_ams_be: vn.attendance.service.server.ServerInstance
 * (stripped of camera real-play handles we don't need).
 */
@Getter
@Setter
@Component
public class ServerInstance {

    private final NetSDKLib netSdk = NetSDKLib.NETSDK_INSTANCE;
    private final NetSDKLib configSdk = NetSDKLib.CONFIG_INSTANCE;

    private NetSDKLib.LLong loginHandle = new NetSDKLib.LLong(0);
    private NetSDKLib.NET_DEVICEINFO_Ex deviceInfo = new NetSDKLib.NET_DEVICEINFO_Ex();
    private NetSDKLib.LLong realLoadHandle = new NetSDKLib.LLong(0);
    private boolean bConnect = false;

    public boolean isConnect() {
        return loginHandle.longValue() != 0 && bConnect;
    }
}
