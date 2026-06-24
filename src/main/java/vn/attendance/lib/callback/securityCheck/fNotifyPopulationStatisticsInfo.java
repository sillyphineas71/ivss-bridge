package vn.attendance.lib.callback.securityCheck;

import com.sun.jna.Pointer;
import vn.attendance.lib.NetSDKLib;
import vn.attendance.lib.SDKCallback;


/**
 * @author ： 291189
 * @since ： Created in 2021/7/5
// 接口 CLIENT_AttachPopulationStatistics 回调函数
// pstuPopulationStatisticsInfos 人数变化信息
 */
public interface fNotifyPopulationStatisticsInfo extends SDKCallback {

    void invoke(
            NetSDKLib.LLong lPopulationStatisticsHandle,
            Pointer pstuPopulationStatisticsInfos,
            Pointer dwUser);
    //typedef int (CALLBACK *fNotifyPopulationStatisticsInfo)(LLONG lPopulationStatisticsHandle, NET_POPULATION_STATISTICS_INFO* pstuPopulationStatisticsInfos, LDWORD dwUser);
}
