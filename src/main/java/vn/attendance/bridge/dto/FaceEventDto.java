package vn.attendance.bridge.dto;

import lombok.Data;

/** Event pushed to NestJS. Mirrors the bridge<->NestJS contract. */
@Data
public class FaceEventDto {
    private String type;          // "face_recognition" | "face_detect"
    private int channelId;        // IVSS channel -> map to room in NestJS
    private String personUid;     // szUID of matched person (face_recognition only) -> map to user
    private String name;          // best-effort candidate name
    private Double similarity;    // best-effort match score
    private String eventAction;   // "pulse" | "start" | "end"  (enter/leave hint)
    private String utc;           // ISO local time from device
    private String imageBase64;   // snapshot, optional
}
