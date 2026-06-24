package vn.attendance.bridge.dto;

import lombok.Data;

@Data
public class EnrollFaceRequest {
    private String groupId;       // optional; falls back to configured default group
    private String personUid;      // our correlation id (e.g. username)
    private String name;
    private String imageBase64;   // portrait
}

