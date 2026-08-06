package vn.attendance.bridge.sdk;

import com.sun.jna.Pointer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.attendance.bridge.dto.FaceEventDto;
import vn.attendance.bridge.forward.NestForwarder;
import vn.attendance.lib.NetSDKLib;
import vn.attendance.lib.ToolKits;
import vn.attendance.lib.enumeration.EM_EVENT_IVS_TYPE;
import vn.attendance.bridge.dto.VehicleEventDto;
import vn.attendance.lib.structure.DEV_EVENT_CAR_DRIVING_IN_INFO;
import vn.attendance.lib.structure.DEV_EVENT_CAR_DRIVING_OUT_INFO;
import vn.attendance.lib.structure.EVENT_PLATE_INFO;
import vn.attendance.lib.structure.DEV_EVENT_NUMBERSTAT_INFO;
import vn.attendance.bridge.dto.OccupancyEventDto;
import vn.attendance.lib.structure.NET_TIME_EX;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Subscribes to IVSS intelligent events and forwards FACE events to NestJS.
 *
 * Mechanism (proven in sep490_ams_be LogAccessServiceImpl):
 * CLIENT_RealLoadPictureEx(loginHandle, -1 (all channels), EVENT_IVS_ALL, ...
 * callback) -> fAnalyzerDataCallBack.invoke(...) fires per event.
 *
 * We handle: EVENT_IVS_FACERECOGNITION -> identity match (the one that drives
 * per-person presence) EVENT_IVS_FACEDETECT -> bare detection (no identity) —
 * forwarded as well, optional.
 *
 * ⚠ LIVE-VERIFY (cannot be proven offline / without IVSS + HDD): The exact
 * identity fields on the recognition struct. The struct carries: - nChannelID,
 * UTC, image (VERIFIED from prior project) - szUID = unique id of the matched
 * person in the Sample Database -> this is the primary key we map back to a
 * SMRMPTS user - nCandidateNum + stuCandidates (name + similarity)
 * [best-effort] - bEventAction (0=pulse, 1=continuous-start, 2=continuous-end
 * -> enter/leave) Confirm field population against
 * vn.attendance.lib.structure.DEV_EVENT_FACERECOGNITION_INFO(_V1) and the
 * candidate struct (NET_CANDIDATE_INFOEX2 / CANDIDATE_INFO_CEX) on the device.
 */
@Service
public class EventListener {

    @Autowired
    private ServerInstance server;
    @Autowired
    private NestForwarder forwarder;

    private NetSDKLib.LLong videoStatHandle;

    /**
     * Attach the realtime picture/alarm stream for all channels.
     */
    public boolean attach() {
        NetSDKLib.LLong handle = server.getNetSdk().CLIENT_RealLoadPictureEx(
                server.getLoginHandle(),
                -1,
                EM_EVENT_IVS_TYPE.EVENT_IVS_ALL.getType(),
                1,
                AnalyzerCB.get(forwarder),
                null,
                null);
        server.setRealLoadHandle(handle);
        boolean ok = handle.longValue() != 0;
        System.out.println("[EventListener] attach " + (ok ? "OK" : "FAIL"));

        // People Counting: attach summary sau khi picture stream OK
        attachVideoStat(0);
        attachVideoStat(1);

        return ok;
    }

    public boolean attachVideoStat(int channel) {
        NetSDKLib.NET_IN_ATTACH_VIDEOSTAT_SUM in = new NetSDKLib.NET_IN_ATTACH_VIDEOSTAT_SUM();
        in.nChannel = channel;
        in.cbVideoStatSum = VideoStatSumCB.get(forwarder);
        NetSDKLib.NET_OUT_ATTACH_VIDEOSTAT_SUM out = new NetSDKLib.NET_OUT_ATTACH_VIDEOSTAT_SUM();
        in.write();
        NetSDKLib.LLong handle = server.getNetSdk()
                .CLIENT_AttachVideoStatSummary(server.getLoginHandle(), in, out, 5000);
        in.read();
        boolean ok = handle != null && handle.longValue() != 0;
        System.out.println("[EventListener] attachVideoStat ch=" + channel + " " + (ok ? "OK" : "FAIL"));
        if (ok) {
            videoStatHandle = handle;
        }
        return ok;
    }

    public boolean detach() {
        boolean ok = server.getNetSdk().CLIENT_StopLoadPic(server.getRealLoadHandle());
        if (ok) {
            server.setRealLoadHandle(new NetSDKLib.LLong(0));
        }
        return ok;
    }

    /**
     * Singleton JNA callback (must outlive the call, hence static instance).
     */
    public static class AnalyzerCB implements NetSDKLib.fAnalyzerDataCallBack {

        private static AnalyzerCB instance;
        private final NestForwarder forwarder;

        private AnalyzerCB(NestForwarder forwarder) {
            this.forwarder = forwarder;
        }

        public static synchronized AnalyzerCB get(NestForwarder forwarder) {
            if (instance == null) {
                instance = new AnalyzerCB(forwarder);
            }
            return instance;
        }

        @Override
        public int invoke(NetSDKLib.LLong lAnalyzerHandle, int dwAlarmType, Pointer pAlarmInfo,
                Pointer pBuffer, int dwBufSize, Pointer dwUser, int nSequence, Pointer reserved) {
            if (lAnalyzerHandle == null || lAnalyzerHandle.longValue() == 0) {
                return -1;
            }
            if (dwAlarmType == 0x11c) {
                return -1;
            }
            System.out.println("[RAW EVENT] dwAlarmType=0x" + Integer.toHexString(dwAlarmType)
                    + " (" + dwAlarmType + ")");

            // --- ANPR camera IPC (Video Metadata "Motor Vehicle") -> TRAFFICJUNCTION 0x17 ---
            if (dwAlarmType == NetSDKLib.EVENT_IVS_TRAFFICJUNCTION) {
                handleTrafficJunction(pAlarmInfo);
                return 0;
            }
            if (dwAlarmType == NetSDKLib.EVENT_IVS_CAR_DRIVING_IN) {
                handleVehicle(pAlarmInfo, "enter");
                return 0;
            }
            if (dwAlarmType == NetSDKLib.EVENT_IVS_CAR_DRIVING_OUT) {
                handleVehicle(pAlarmInfo, "leave");
                return 0;
            }
            if (dwAlarmType == NetSDKLib.EVENT_IVS_NUMBERSTAT) {
                handleOccupancy(pAlarmInfo);
                return 0;
            }

            EM_EVENT_IVS_TYPE type = EM_EVENT_IVS_TYPE.getEventType(dwAlarmType);
            if (type == null) {
                return 0;
            }
            switch (type) {
                case EVENT_IVS_FACERECOGNITION ->
                    handleRecognition(pAlarmInfo, pBuffer);
                case EVENT_IVS_FACEDETECT ->
                    handleDetect(pAlarmInfo, pBuffer);
                default -> {
                    /* ignore other intelligent events */ }
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
            e.setUtc(toIsoWithOffset(info.UTC));
            e.setPersonUid(personUid);
            e.setImageBase64(extractImage(info, pBuffer));

            forwarder.forwardAsync(e);
        }

        private void handleVehicle(Pointer pAlarmInfo, String direction) {
            try {
                NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO car;
                int channelId;
                NET_TIME_EX utc;
                if ("enter".equals(direction)) {
                    DEV_EVENT_CAR_DRIVING_IN_INFO info = new DEV_EVENT_CAR_DRIVING_IN_INFO();
                    ToolKits.GetPointerData(pAlarmInfo, info);
                    channelId = info.nChannelID;
                    utc = info.UTC;
                    car = info.stuTrafficCar;
                } else {
                    DEV_EVENT_CAR_DRIVING_OUT_INFO info = new DEV_EVENT_CAR_DRIVING_OUT_INFO();
                    ToolKits.GetPointerData(pAlarmInfo, info);
                    channelId = info.nChannelID;
                    utc = info.UTC;
                    car = info.stuTrafficCar;
                }

                VehicleEventDto e = new VehicleEventDto();
                e.setChannelId(channelId);
                e.setUtc(toIsoWithOffset(utc));
                e.setEventAction(direction);

                // stuTrafficCar có thể null nếu JNA không tự init struct con → guard
                if (car != null) {
                    e.setPlateNumber(trim(car.szPlateNumber));
                    e.setPlateColor(trim(car.szPlateColor));
                    e.setVehicleColor(trim(car.szVehicleColor));
                }

                // LOG THÔ để debug live (xem biển đọc ra gì)
                System.out.println("[EventListener] VEHICLE " + direction
                        + " ch=" + channelId + " plate=[" + e.getPlateNumber() + "]");

                // Nếu không đọc được biển → vẫn forward (BE ghi unmatched) nhưng cảnh báo
                if (e.getPlateNumber() == null || e.getPlateNumber().isEmpty()) {
                    System.err.println("[EventListener] WARN: empty plate on " + direction
                            + " — struct/encoding có thể sai, kiểm stuTrafficCar");
                }

                forwarder.forwardVehicleAsync(e);
            } catch (Exception ex) {
                System.err.println("[EventListener] handleVehicle error: " + ex.getMessage());
            }
        }

        private void handleTrafficJunction(Pointer pAlarmInfo) {
            try {
                NetSDKLib.DEV_EVENT_TRAFFICJUNCTION_INFO info
                        = new NetSDKLib.DEV_EVENT_TRAFFICJUNCTION_INFO();
                ToolKits.GetPointerData(pAlarmInfo, info);

                int channelId = info.nChannelID;
                String utcIso = toIsoWithOffset(info.UTC);   // UTC kiểu NetSDKLib.NET_TIME_EX (inner) -> khớp overload có sẵn

                // --- BIỂN: thử nhiều nguồn ---
                String plate = null;
                // nguồn 1: stuPlateInfo (front/back)
                if (info.stuPlateInfo != null) {
                    String front = trim(info.stuPlateInfo.szFrontPlateNumber);
                    String back = trim(info.stuPlateInfo.szBackPlateNumber);
                    if (front != null && !front.isEmpty()) {
                        plate = front;
                    } else if (back != null && !back.isEmpty()) {
                        plate = back;
                    }
                }
                // nguồn 2: stTrafficCar.szPlateNumber (nếu nguồn 1 rỗng)
                if ((plate == null || plate.isEmpty()) && info.stTrafficCar != null) {
                    String p = trim(info.stTrafficCar.szPlateNumber);
                    if (p != null && !p.isEmpty()) {
                        plate = p;
                    }
                }

                // --- DIRECTION: byDirection 1=thuận 2=ngược; chưa rõ nghĩa thật -> log, default 'seen' ---
                String direction = "seen";
                // (sau khi xem log thực tế sẽ map byDirection/byVehicleDirection -> enter/leave)

                VehicleEventDto e = new VehicleEventDto();
                e.setChannelId(channelId);
                e.setUtc(utcIso);
                e.setEventAction(direction);
                if (plate != null) {
                    e.setPlateNumber(plate);
                }
                if (info.stTrafficCar != null) {
                    e.setPlateColor(trim(info.stTrafficCar.szPlateColor));
                    e.setVehicleColor(trim(info.stTrafficCar.szVehicleColor));
                }

                // LOG THÔ — xem biển + direction byte để map sau
                System.out.println("[EventListener] JUNCTION ch=" + channelId
                        + " plate=[" + plate + "]"
                        + " byDirection=" + info.byDirection
                        + " byVehicleDir=" + info.byVehicleDirection
                        + " bEventAction=" + info.bEventAction);

                if (plate == null || plate.isEmpty()) {
                    System.err.println("[EventListener] JUNCTION ch=" + channelId + ": empty plate — skip (chưa đọc được biển: góc/chất lượng/nét?)");
                    return;   // không forward biển rỗng → hết spam 400
                }
                forwarder.forwardVehicleAsync(e);
            } catch (Exception ex) {
                System.err.println("[EventListener] handleTrafficJunction error: " + ex.getMessage());
            }
        }

        private static String toIsoWithOffset(NetSDKLib.NET_TIME_EX t) {
            if (t == null) {
                return null;
            }
            try {
                return LocalDateTime.of(t.dwYear, t.dwMonth, t.dwDay, t.dwHour, t.dwMinute, t.dwSecond)
                        .atOffset(java.time.ZoneOffset.of("+07:00")).toString();
            } catch (Exception ex) {
                return null;
            }
        }

        private static String toIsoWithOffset(NET_TIME_EX t) {
            if (t == null) {
                return null;
            }
            try {
                return LocalDateTime.of(t.dwYear, t.dwMonth, t.dwDay, t.dwHour, t.dwMinute, t.dwSecond)
                        .atOffset(java.time.ZoneOffset.of("+07:00")).toString();
                // → "2026-07-29T14:49:20+07:00"
            } catch (Exception ex) {
                return null;
            }
        }

        private static String toIso(NET_TIME_EX t) {
            if (t == null) {
                return null;
            }
            try {
                return LocalDateTime.of(t.dwYear, t.dwMonth, t.dwDay, t.dwHour, t.dwMinute, t.dwSecond).toString();
            } catch (Exception ex) {
                return null;
            }
        }

        private void handleOccupancy(Pointer pAlarmInfo) {
            try {
                DEV_EVENT_NUMBERSTAT_INFO info = new DEV_EVENT_NUMBERSTAT_INFO();
                ToolKits.GetPointerData(pAlarmInfo, info);

                // bEventAction: 0=pulse, 1=continuous-start, 2=continuous-end
                String action = switch (info.bEventAction) {
                    case 1 ->
                        "start";
                    case 2 ->
                        "end";
                    default ->
                        "pulse";
                };

                // LOG THÔ để xác nhận lần chạy đầu (đi 2 người qua -> number nhảy)
                System.out.println("[EventListener] OCCUPANCY ch=" + info.nChannelID
                        + " number=" + info.nNumber
                        + " entered=" + info.nEnteredNumber
                        + " exited=" + info.nExitedNumber
                        + " action=" + action);

                OccupancyEventDto e = new OccupancyEventDto();
                e.setChannelId(info.nChannelID);
                e.setUtc(toIsoWithOffset(info.UTC));
                e.setNumber(info.nNumber);
                e.setEnteredNumber(info.nEnteredNumber);
                e.setExitedNumber(info.nExitedNumber);
                e.setEventAction(action);

                forwarder.forwardOccupancyAsync(e);
            } catch (Exception ex) {
                System.err.println("[EventListener] handleOccupancy error: " + ex.getMessage());
            }
        }

        private void handleDetect(Pointer pAlarmInfo, Pointer pBuffer) {
            NetSDKLib.DEV_EVENT_FACEDETECT_INFO info = new NetSDKLib.DEV_EVENT_FACEDETECT_INFO();
            ToolKits.GetPointerData(pAlarmInfo, info);

            FaceEventDto e = new FaceEventDto();
            e.setType("face_detect");
            e.setChannelId(info.nChannelID);
            e.setUtc(toIsoWithOffset(info.UTC));
            // detection has no identity
            forwarder.forwardAsync(e);
        }

        /**
         * Image bytes -> base64 data URL. Pattern verified in prior
         * LogAccessServiceImpl.
         */
        private String extractImage(NetSDKLib.DEV_EVENT_FACERECOGNITION_INFO info, Pointer pBuffer) {
            try {
                NetSDKLib.NET_PIC_INFO pic = info.stuObject.stPicInfo;
                if (pic == null || pic.dwFileLenth <= 0 || pBuffer == null) {
                    return null;
                }
                byte[] img = pBuffer.getByteArray(pic.dwOffSet, pic.dwFileLenth);
                if (img.length <= 0) {
                    return null;
                }
                return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(img);
            } catch (Exception ex) {
                return null;
            }
        }

        private static String toIso(NetSDKLib.NET_TIME_EX t) {
            if (t == null) {
                return null;
            }
            try {
                return LocalDateTime.of(t.dwYear, t.dwMonth, t.dwDay, t.dwHour, t.dwMinute, t.dwSecond).toString();
            } catch (Exception ex) {
                return null;
            }
        }

        private static String trim(byte[] b) {
            return b == null ? null : new String(b).trim().replace("\u0000", "");
        }
    }

    /**
     * Realtime People Counting summary callback — phải static để JNA giữ tham
     * chiếu.
     */
    public static class VideoStatSumCB implements NetSDKLib.fVideoStatSumCallBack {

        private static VideoStatSumCB instance;
        private final NestForwarder forwarder;

        private VideoStatSumCB(NestForwarder forwarder) {
            this.forwarder = forwarder;
        }

        public static synchronized VideoStatSumCB get(NestForwarder forwarder) {
            if (instance == null) {
                instance = new VideoStatSumCB(forwarder);
            }
            return instance;
        }

        @Override
        public void invoke(NetSDKLib.LLong lAttachHandle,
                NetSDKLib.NET_VIDEOSTAT_SUMMARY pBuf,
                int dwBufLen, Pointer dwUser) {
            try {
                if (pBuf == null) {
                    return;
                }
                int channel = pBuf.nChannelID;
                int entered = pBuf.stuEnteredSubtotal.nToday;   // vào trong ngày
                int exited = pBuf.stuExitedSubtotal.nToday;    // ra trong ngày
                int inside = pBuf.nInsidePeopleNum;            // hiện trong vùng

                // LOG THÔ để xác nhận
                System.out.println("[EventListener] VIDEOSTAT ch=" + channel
                        + " inside=" + inside + " entered=" + entered + " exited=" + exited);

                OccupancyEventDto e = new OccupancyEventDto();
                e.setChannelId(channel);
                e.setNumber(inside);            // headcount hiện tại
                e.setEnteredNumber(entered);
                e.setExitedNumber(exited);
                e.setUtc(java.time.OffsetDateTime.now().toString());
                forwarder.forwardOccupancyAsync(e);
            } catch (Exception ex) {
                System.err.println("[EventListener] VideoStatSumCB error: " + ex.getMessage());
            }
        }
    }
}
