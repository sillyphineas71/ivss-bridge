package vn.attendance.bridge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Manual envelope mirroring NestJS {success,message,data}. */
@Data
@AllArgsConstructor
public class ApiResponse {
    private boolean success;
    private String message;
    private Object data;

    public static ApiResponse ok(Object data) { return new ApiResponse(true, "OK", data); }
    public static ApiResponse fail(String msg) { return new ApiResponse(false, msg, null); }
}
