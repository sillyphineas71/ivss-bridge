package vn.attendance.bridge.sdk;

import com.sun.jna.Memory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.attendance.lib.NetSDKLib.*;
import vn.attendance.util.AvataUtils;

import java.io.UnsupportedEncodingException;

/**
 * Face database operations against the IVSS "Sample Database", via NetSDK config calls.
 * Sequences ported faithfully from sep490_ams_be FaceRecognitionServiceImpl
 * (addFaceRecognitionGroup / addFaceRecognitionDB), stripped of AMS user/role coupling.
 *
 * Encoding note from the proven project: "UTF-8" on Linux, "GBK" on Windows for szPersonName.
 * Keep this in sync with the host you run on.
 */
@Service
public class FaceDbService {

    private static final String ENCODE = "UTF-8"; // use "GBK" if running on Windows + Chinese names

    @Autowired private ServerInstance server;

    /** Create a face group (library) in the Sample Database. Returns groupId, or "" on failure. */
    public String createGroup(String groupName) {
        NET_ADD_FACERECONGNITION_GROUP_INFO addGroupInfo = new NET_ADD_FACERECONGNITION_GROUP_INFO();
        try {
            byte[] gn = groupName.getBytes(ENCODE);
            System.arraycopy(gn, 0, addGroupInfo.stuGroupInfo.szGroupName, 0, gn.length);
        } catch (UnsupportedEncodingException e) {
            System.err.println(e.getMessage());
        }

        NET_IN_OPERATE_FACERECONGNITION_GROUP in = new NET_IN_OPERATE_FACERECONGNITION_GROUP();
        in.emOperateType = EM_OPERATE_FACERECONGNITION_GROUP_TYPE.NET_FACERECONGNITION_GROUP_ADD;
        in.pOPerateInfo = addGroupInfo.getPointer();
        NET_OUT_OPERATE_FACERECONGNITION_GROUP out = new NET_OUT_OPERATE_FACERECONGNITION_GROUP();

        addGroupInfo.write();
        boolean ok = server.getNetSdk()
                .CLIENT_OperateFaceRecognitionGroup(server.getLoginHandle(), in, out, 4000);
        addGroupInfo.read();
        String token = ok ? new String(out.szGroupId).trim() : "";
        System.out.println("[FaceDbService] createGroup name='" + groupName + "' ok=" + ok + " -> GROUP_TOKEN='" + token + "'");
        return token;
    }

    /**
     * Enroll one person+face into a group. Returns the device-assigned szUID
     * (the key the recognition event carries back), or "" on failure.
     *
     * @param groupId    target group / face library id
     * @param personId   our correlation id (we pass username -> stored as szID)
     * @param personName display name
     * @param base64Jpg  portrait, base64 (with or without data: prefix)
     */
    public String enrollFace(String groupId, String personId, String personName, String base64Jpg) {
        try {
            AvataUtils.AvataInfo pic = AvataUtils.decodeBase64AndExtractInfo(base64Jpg);

            NET_IN_OPERATE_FACERECONGNITIONDB in = new NET_IN_OPERATE_FACERECONGNITIONDB();
            in.emOperateType = EM_OPERATE_FACERECONGNITIONDB_TYPE.NET_FACERECONGNITIONDB_ADD;
            in.bUsePersonInfoEx = 1;

            byte[] gid = groupId.getBytes();
            System.arraycopy(gid, 0, in.stPersonInfoEx.szGroupID, 0, gid.length);

            // minimal profile — only what recognition needs
            in.stPersonInfoEx.wYear = 2000;
            in.stPersonInfoEx.byMonth = 1;
            in.stPersonInfoEx.byDay = 1;
            in.stPersonInfoEx.bySex = 1;

            byte[] nm = personName.getBytes(ENCODE);
            System.arraycopy(nm, 0, in.stPersonInfoEx.szPersonName, 0, nm.length);
            in.stPersonInfoEx.byIDType = (byte) EM_CERTIFICATE_TYPE.CERTIFICATE_TYPE_UNKNOWN;
            byte[] sid = personId.getBytes();
            System.arraycopy(sid, 0, in.stPersonInfoEx.szID, 0, sid.length);
            byte[] cc = "VN".getBytes();
            System.arraycopy(cc, 0, in.stPersonInfoEx.szCountry, 0, cc.length);

            // image buffer
            in.stPersonInfoEx.wFacePicNum = 1;
            in.stPersonInfoEx.szFacePicInfo[0].dwFileLenth = pic.getPictureLeng();
            in.stPersonInfoEx.szFacePicInfo[0].dwOffSet = 0;
            in.stPersonInfoEx.szFacePicInfo[0].wWidth = (short) pic.getWidth();
            in.stPersonInfoEx.szFacePicInfo[0].wHeight = (short) pic.getHeight();
            in.nBufferLen = pic.getPictureLeng();
            in.pBuffer = (Memory) pic.getMemory();

            NET_OUT_OPERATE_FACERECONGNITIONDB out = new NET_OUT_OPERATE_FACERECONGNITIONDB();
            in.write();
            boolean ok = server.getNetSdk()
                    .CLIENT_OperateFaceRecognitionDB(server.getLoginHandle(), in, out, 3000);
            in.read();
            String outUid = ok ? new String(out.szUID).trim() : "";
            System.out.println("[FaceDbService] enrollFace group='" + groupId + "' personId='" + personId + "' ok=" + ok + " -> szUID='" + outUid + "' err=" + (ok ? "-" : vn.attendance.lib.ToolKits.getErrorCode()));
            return outUid;
        } catch (Exception ex) {
            System.err.println("[FaceDbService] enrollFace failed: " + ex.getMessage());
            return "";
        }
    }

    /**
     * Remove a person from a group by szUID.
     * Operate-type NET_FACERECONGNITIONDB_DELETE (see prior FaceRecognitionServiceImpl ~line 616).
     * PORT/VERIFY: confirm the exact delete-input fields (group + szUID) against the prior body.
     */
    public boolean deleteFace(String groupId, String szUid) {
        NET_IN_OPERATE_FACERECONGNITIONDB in = new NET_IN_OPERATE_FACERECONGNITIONDB();
        in.emOperateType = EM_OPERATE_FACERECONGNITIONDB_TYPE.NET_FACERECONGNITIONDB_DELETE;
        in.bUsePersonInfoEx = 1;
        System.arraycopy(groupId.getBytes(), 0, in.stPersonInfoEx.szGroupID, 0, groupId.getBytes().length);
        System.arraycopy(szUid.getBytes(), 0, in.stPersonInfoEx.szUID, 0, szUid.getBytes().length);
        NET_OUT_OPERATE_FACERECONGNITIONDB out = new NET_OUT_OPERATE_FACERECONGNITIONDB();
        in.write();
        boolean ok = server.getNetSdk()
                .CLIENT_OperateFaceRecognitionDB(server.getLoginHandle(), in, out, 3000);
        in.read();
        return ok;
    }

    // PORT (optional): listFaces(groupId) -> Map<personId, szUid>
    // Lift the CLIENT_StartFindFaceRecognition / CLIENT_DoFindFaceRecognition / CLIENT_StopFindFaceRecognition
    // loop from FaceRecognitionServiceImpl.findFaceRecognitionDB (~lines 360-430).
    // Not on the critical path for v1 (NestJS keeps the username<->szUid map itself at enroll time).
}
