package vn.attendance.bridge.sdk;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.attendance.lib.NetSDKLib.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SnapshotService {

    @Autowired private ServerInstance server;

    private static final Map<Integer, CompletableFuture<byte[]>> pending = new ConcurrentHashMap<>();
    private static final AtomicInteger cmdSerialCounter = new AtomicInteger(0);

    // QUAN TRỌNG: giữ tham chiếu static để JNA không GC callback này
    // (native code vẫn giữ con trỏ tới nó sau khi registerCallback() return)
    private static fSnapRev snapRevCallback;

    public void registerCallback() {
        snapRevCallback = (lLoginID, pBuf, revLen, encodeType, cmdSerial, dwUser) -> {
            try {
                CompletableFuture<byte[]> f = pending.remove(cmdSerial);
                if (f == null) {
                    System.out.println("[SnapshotService] SnapRev: CmdSerial=" + cmdSerial + " không khớp future nào (đã timeout hoặc lạ) — bỏ qua.");
                    return;
                }
                if (encodeType != 10) {
                    System.out.println("[SnapshotService] SnapRev: EncodeType=" + encodeType + " không phải JPEG(10) — bỏ qua.");
                    f.complete(null);
                    return;
                }
                byte[] data = pBuf.getByteArray(0, revLen);
                System.out.println("[SnapshotService] SnapRev: CmdSerial=" + cmdSerial + " nhận " + revLen + " bytes JPEG.");
                f.complete(data);
            } catch (Exception ex) {
                System.err.println("[SnapshotService] SnapRev callback lỗi: " + ex.getMessage());
            }
        };
        server.getNetSdk().CLIENT_SetSnapRevCallBack(snapRevCallback, null);
        System.out.println("[SnapshotService] CLIENT_SetSnapRevCallBack registered.");
    }

    public String snapPicture(int channelId) {
        int cmdSerial = cmdSerialCounter.updateAndGet(v -> (v + 1) % 65536);

        SNAP_PARAMS params = new SNAP_PARAMS();
        params.Channel = channelId;
        params.Quality = 3;
        params.ImageSize = 2;
        params.mode = 0;
        params.InterSnap = 0;
        params.CmdSerial = cmdSerial;

        CompletableFuture<byte[]> future = new CompletableFuture<>();
        pending.put(cmdSerial, future);

        IntByReference reserved = new IntByReference(0);
        boolean ok = server.getNetSdk().CLIENT_SnapPictureEx(server.getLoginHandle(), params, reserved);

        if (!ok) {
            pending.remove(cmdSerial);
            System.out.println("[SnapshotService] CLIENT_SnapPictureEx fail-fast ch=" + channelId + " cmdSerial=" + cmdSerial
                + " errCode=" + vn.attendance.lib.ToolKits.getErrorCode());
            return null;
        }

        try {
            byte[] data = future.get(5, TimeUnit.SECONDS);
            if (data == null) return null;
            return "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(data);
        } catch (Exception ex) {
            pending.remove(cmdSerial);
            System.out.println("[SnapshotService] Timeout/lỗi chờ SnapRev ch=" + channelId + " cmdSerial=" + cmdSerial + ": " + ex.getMessage());
            return null;
        }
    }
}