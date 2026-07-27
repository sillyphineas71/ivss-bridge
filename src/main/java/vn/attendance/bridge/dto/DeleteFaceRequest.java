package vn.attendance.bridge.dto;

import lombok.Data;

@Data
public class DeleteFaceRequest {
    private String groupId;     // optional; fallback default group
    private String personUid;   // = device_person_code BE gửi (set vào szID lúc enroll)
}