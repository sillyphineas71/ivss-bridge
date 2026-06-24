package vn.attendance.lib.structure;

import vn.attendance.lib.NetSDKLib;

/**
 * @author 251823
 * @description CLIENT_AccessControlCaptureCmd 输出结构体
 * @date 2022/12/30 10:56:10
 */
public class NET_OUT_ACCESSCONTROL_CAPTURE_CMD extends NetSDKLib.SdkStructure {
	/**
	 * 结构体大小
	 */
	public int dwSize;

	public NET_OUT_ACCESSCONTROL_CAPTURE_CMD() {
		this.dwSize = this.size();
	}
}