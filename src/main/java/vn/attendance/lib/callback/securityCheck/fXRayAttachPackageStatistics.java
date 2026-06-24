package vn.attendance.lib.callback.securityCheck;

import com.sun.jna.Pointer;
import vn.attendance.lib.NetSDKLib;
import vn.attendance.lib.SDKCallback;

/**
 * @author 291189
 * @version 1.0
 * @description  包裹信息回调函数
 * @date 2021/7/1
 */
public interface fXRayAttachPackageStatistics extends SDKCallback {
    /**
     * @param lAttachHandle 订阅句柄
     * @param pInfo 包裹信息回调函数，对应结构体{@link vn.attendance.lib.structure.NET_IN_XRAY_PACKAGE_STATISTICS_INFO}
     * @param dwUser 用户数据
     */
    void invoke(
            NetSDKLib.LLong lAttachHandle,
            Pointer pInfo,
            Pointer dwUser);

}
