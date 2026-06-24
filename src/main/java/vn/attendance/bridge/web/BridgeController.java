package vn.attendance.bridge.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import vn.attendance.bridge.dto.ApiResponse;
import vn.attendance.bridge.dto.CreateGroupRequest;
import vn.attendance.bridge.dto.EnrollFaceRequest;
import vn.attendance.bridge.sdk.FaceDbService;
import vn.attendance.bridge.sdk.IvssConnection;

import java.util.Map;

/**
 * Control API the NestJS capstone-be calls. Thin: validates, delegates to SDK services,
 * returns the {success,message,data} envelope (mirrors NestJS style).
 *
 * NOTE: this API trusts the local network. If exposed, put it behind the same
 * X-Internal-Token check NestJS uses, or bind to localhost only.
 */
@RestController
@RequestMapping("/api/ivss")
public class BridgeController {

    @Value("${device.default-group-id:}")   private String defaultGroupId;
    @Value("${device.default-group-name:SMRMPTS}") private String defaultGroupName;

    @Autowired private IvssConnection connection;
    @Autowired private FaceDbService faceDb;

    @GetMapping("/status")
    public ApiResponse status() {
        return ApiResponse.ok(Map.of("connected", connection.isConnected()));
    }

    @PostMapping("/login")
    public ApiResponse login(@RequestBody Map<String, Object> body) {
        boolean ok = connection.login(
                (String) body.get("ip"),
                Integer.parseInt(String.valueOf(body.get("port"))),
                (String) body.get("username"),
                (String) body.get("password"));
        return ok ? ApiResponse.ok(null) : ApiResponse.fail("LOGIN_FAILED");
    }

    @PostMapping("/groups")
    public ApiResponse createGroup(@RequestBody CreateGroupRequest req) {
        String id = faceDb.createGroup(req.getName());
        return id.isEmpty() ? ApiResponse.fail("GROUP_CREATE_FAILED") : ApiResponse.ok(Map.of("groupId", id));
    }

    /** Enroll a user's portrait. Returns szUid (the key recognition events carry back). */
    @PostMapping("/faces")
    public ApiResponse enroll(@RequestBody EnrollFaceRequest req) {
        if (req.getImageBase64() == null || req.getPersonUid() == null) {
            return ApiResponse.fail("MISSING_FIELDS");
        }
        String group = (req.getGroupId() != null && !req.getGroupId().isEmpty())
                ? req.getGroupId() : defaultGroupId;
        if (group == null || group.isEmpty()) {
            return ApiResponse.fail("NO_GROUP_CONFIGURED");
        }
        String uid = faceDb.enrollFace(group, req.getPersonUid(),
                req.getName() != null ? req.getName() : req.getPersonUid(), req.getImageBase64());
        return uid.isEmpty() ? ApiResponse.fail("ENROLL_FAILED")
                             : ApiResponse.ok(Map.of("szUid", uid, "groupId", group));
    }

    @DeleteMapping("/faces")
    public ApiResponse delete(@RequestParam String groupId, @RequestParam String szUid) {
        return faceDb.deleteFace(groupId, szUid) ? ApiResponse.ok(null) : ApiResponse.fail("DELETE_FAILED");
    }

    // GET /cameras -> PORT: wrap CLIENT_MatrixGetCameras (see prior DeviceServiceImpl.SynchronizeCamera)
    // to list IVSS channels + state, so NestJS can build the channel<->room mapping.
}

