package vn.attendance.bridge.dto;

import lombok.Data;

/**
 * Vehicle (ANPR) event pushed to NestJS capstone-be.
 * Khớp CONTRACT UC4 (VWH-001):
 *   POST /api/v1/internal/ivss/vehicle-events
 *   header X-Internal-Token: <IVSS_BRIDGE_TOKEN>
 *   body: plateNumber(raw) + channelId + utc bắt buộc; còn lại optional.
 */
@Data
public class VehicleEventDto {
    private String plateNumber;    // RAW từ camera (CHƯA normalize) — BE normalize
    private int    channelId;      // IVSS channel
    private String utc;            // ISO local time
    private String eventAction;    // "enter" (CAR_DRIVING_IN) | "leave" (CAR_DRIVING_OUT)
    private String plateColor;     // optional
    private String vehicleColor;   // optional
    private String vehicleType;    // optional (để trống — struct không có field rõ)
}