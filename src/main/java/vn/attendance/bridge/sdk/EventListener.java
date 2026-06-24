package vn.attendance.bridge.sdk;

import com.sun.jna.Pointer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.attendance.bridge.dto.FaceEventDto;
import vn.attendance.bridge.forward.NestForwarder;
import vn.attendance.lib.NetSDKLib;
import vn.attendance.lib.ToolKits;
import vn.attendance.lib.enumeration.EM_EVENT_IVS_TYPE;

import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Subscribes to IVSS intelligent events and forwards FACE events to NestJS.
 *
 * Mechanism (proven in sep490_ams_be LogAccessServiceImpl):
 *   CLIENT_RealLoadPictureEx(loginHandle, -1 (all channels), EVENT_IVS_ALL, ... callback)
 *   -> fAnalyzerDataCallBack.invoke(...) fires per event.
 *
 * We handle:
 *   EVENT_IVS_FACERECOGNITION -> identity match (the one that drives per-person presence)
 *   EVENT_IVS_FACEDETECT      -> bare detection (no identity) — forwarded as well, optional.
 *
 * ⚠ LIVE-VERIFY (cannot be proven offline / without IVSS + HDD):
 *   The exact identity fields on the recognition struct. The struct carries:
 *     - nChannelID, UTC, image  (VERIFIED from prior project)
 *     - szUID  = unique id of the matched person in the Sample Database
 *               -> this is the primary key we map back to a SMRMPTS user
 *     - nCandidateNum + stuCandidates (name + similarity)  [best-effort]
 *     - bEventAction (0=pulse, 1=continuous-start, 2=continuous-end -> enter/leave)
 *   Confirm field population against vn.attendance.lib.structure.DEV_EVENT_FACERECOGNITION_INFO(_V1)
 *   and the candidate struct (NET_CANDIDATE_INFOEX2 / CANDIDATE_INFO_CEX) on the device.
 */
@Service
public class EventListener {

    @Autowired private ServerInstance server;
    @Autowired private NestForwarder forwarder;

    /** Attach the realtime picture/alarm stream for all channels. */
    public boolean attach() {
        NetSDKLib.LLong handle = server.getNetSdk().CLIENT_RealLoadPictureEx(
                server.getLoginHandle(),
                -1,                                              // all channels
                EM_EVENT_IVS_TYPE.EVENT_IVS_ALL.getType(),
                1,                                               // bNeedPicFile
                AnalyzerCB.get(forwarder),
                null,
                null);
        server.setRealLoadHandle(handle);
        boolean ok = handle.longValue() != 0;
        System.out.println("[EventListener] attach " + (ok ? "OK" : "FAIL"));
        return ok;
    }

    public boolean detach() {
        boolean ok = server.getNetSdk().CLIENT_StopLoadPic(server.getRealLoadHandle());
        if (ok) server.setRealLoadHandle(new NetSDKLib.LLong(0));
        return ok;
    }

    /** Singleton JNA callback (must outlive the call, hence static instance). */
    public static class AnalyzerCB implements NetSDKLib.fAnalyzerDataCallBack {
        private static AnalyzerCB instance;
        private final NestForwarder forwarder;

        private AnalyzerCB(NestForwarder forwarder) { this.forwarder = forwarder; }

        public static synchronized AnalyzerCB get(NestForwarder forwarder) {
            if (instance == null) instance = new AnalyzerCB(forwarder);
            return instance;
        }

        @Override
        public int invoke(NetSDKLib.LLong lAnalyzerHandle, int dwAlarmType, Pointer pAlarmInfo,
                          Pointer pBuffer, int dwBufSize, Pointer dwUser, int nSequence, Pointer reserved) {
            if (lAnalyzerHandle == null || lAnalyzerHandle.longValue() == 0) return -1;

            EM_EVENT_IVS_TYPE type = EM_EVENT_IVS_TYPE.getEventType(dwAlarmType);
            if (type == null) return 0;

            switch (type) {
                case EVENT_IVS_FACERECOGNITION -> handleRecognition(pAlarmInfo, pBuffer);
                case EVENT_IVS_FACEDETECT      -> handleDetect(pAlarmInfo, pBuffer);
                default -> { /* ignore other intelligent events */ }
            }
            return 0;
        }

        private void handleRecognition(Pointer pAlarmInfo, Pointer pBuffer) {
            NetSDKLib.DEV_EVENT_FACERECOGNITION_INFO info = new NetSDKLib.DEV_EVENT_FACERECOGNITION_INFO();
            ToolKits.GetPointerData(pAlarmInfo, info);

            // Stranger / không khớp ai trong FaceDB → nCandidateNum = 0 → KHÔNG forward.
            // (info.szUID top-level là UID ảnh-snap, RỖNG khi match → đừng dùng nó.)
            if (info.nCandidateNum <= 0) {
                return;
            }

            // Identity thật = UID server-sinh của candidate đã khớp (= "621"), khớp device_person_id bên BE.
            NetSDKLib.CANDIDATE_INFO cand = info.stuCandidates[0];
            String personUid = trim(cand.stPersonInfo.szUID);
            if (personUid == null || personUid.isEmpty()) {
                return; // candidate có nhưng không có UID → bỏ, KHÔNG gửi rỗng (tránh 400).
            }

            FaceEventDto e = new FaceEventDto();
            e.setType("face_recognition");
            e.setChannelId(info.nChannelID);
            e.setUtc(toIso(info.UTC));
            e.setPersonUid(personUid);
            e.setImageBase64(extractImage(info, pBuffer));

            forwarder.forwardAsync(e);
        }

        private void handleDetect(Pointer pAlarmInfo, Pointer pBuffer) {
            NetSDKLib.DEV_EVENT_FACEDETECT_INFO info = new NetSDKLib.DEV_EVENT_FACEDETECT_INFO();
            ToolKits.GetPointerData(pAlarmInfo, info);

            FaceEventDto e = new FaceEventDto();
            e.setType("face_detect");
            e.setChannelId(info.nChannelID);
            e.setUtc(toIso(info.UTC));
            // detection has no identity
            forwarder.forwardAsync(e);
        }

        /** Image bytes -> base64 data URL. Pattern verified in prior LogAccessServiceImpl. */
        private String extractImage(NetSDKLib.DEV_EVENT_FACERECOGNITION_INFO info, Pointer pBuffer) {
            try {
                NetSDKLib.NET_PIC_INFO pic = info.stuObject.stPicInfo;
                if (pic == null || pic.dwFileLenth <= 0 || pBuffer == null) return null;
                byte[] img = pBuffer.getByteArray(pic.dwOffSet, pic.dwFileLenth);
                if (img.length <= 0) return null;
                return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(img);
            } catch (Exception ex) {
                return null;
            }
        }

        private static String toIso(NetSDKLib.NET_TIME_EX t) {
            if (t == null) return null;
            try {
                return LocalDateTime.of(t.dwYear, t.dwMonth, t.dwDay, t.dwHour, t.dwMinute, t.dwSecond).toString();
            } catch (Exception ex) { return null; }
        }

        private static String trim(byte[] b) {
            return b == null ? null : new String(b).trim().replace("\u0000", "");
        }
    }
}
