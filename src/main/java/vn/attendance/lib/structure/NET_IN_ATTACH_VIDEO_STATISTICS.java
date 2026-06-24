package vn.attendance.lib.structure;

import com.sun.jna.Pointer;
import vn.attendance.lib.NetSDKLib;

/**
 * @author 251823
 * @description CLIENT_AttachVideoStatistics 输入参数
 * @date 2022/10/28 10:46:07
 */
public class NET_IN_ATTACH_VIDEO_STATISTICS extends NetSDKLib.SdkStructure {
	/**
	 * 结构体大小
	 */
	public int dwSize;
	/**
	 * 通道号
	 */
	public int nChannelID;
	/**
	 * 用户回调函数
	 */
	public NetSDKLib.fVideoStatisticsInfoCallBack cbCallBack;
	/**
	 * 用户数据
	 */
	public Pointer dwUser;
	/**
	 * 业务类型 {@link vn.attendance.lib.enumeration.NET_EM_VS_TYPE}
	 */
	public int emType;

	public NET_IN_ATTACH_VIDEO_STATISTICS() {
		this.dwSize = this.size();
	}
}