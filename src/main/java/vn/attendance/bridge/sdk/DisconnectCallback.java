package vn.attendance.bridge.sdk;

import com.sun.jna.Pointer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.attendance.lib.NetSDKLib;

/** Trimmed (no AMS deps). Fires when the NetSDK session drops. */
@Component
public class DisconnectCallback implements NetSDKLib.fDisConnect {

    @Autowired private ServerInstance server;

    @Override
    public void invoke(NetSDKLib.LLong lLoginID, String pchDVRIP, int nDVRPort, Pointer dwUser) {
        server.setBConnect(false);
        System.err.printf("[IVSS] disconnected %s:%d%n", pchDVRIP, nDVRPort);
    }
}
