package vn.attendance.bridge.sdk;

import com.sun.jna.Pointer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.attendance.lib.NetSDKLib;

/** Trimmed (no AMS deps). Fires when the NetSDK auto-reconnect succeeds. */
@Component
public class HaveReconnectCallback implements NetSDKLib.fHaveReConnect {

    @Autowired private ServerInstance server;
    @Autowired private EventListener eventListener;

    @Override
    public void invoke(NetSDKLib.LLong lLoginID, String pchDVRIP, int nDVRPort, Pointer dwUser) {
        server.setBConnect(true);
        System.out.printf("[IVSS] reconnected %s:%d — re-attaching event stream%n", pchDVRIP, nDVRPort);
        // re-subscribe events after a reconnect
        eventListener.attach();
    }
}
