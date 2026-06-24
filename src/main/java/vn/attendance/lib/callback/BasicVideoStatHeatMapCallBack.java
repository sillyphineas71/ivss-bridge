package vn.attendance.lib.callback;

import com.sun.jna.Pointer;
import vn.attendance.lib.NetSDKLib;
import vn.attendance.lib.ToolKits;
import vn.attendance.lib.enumeration.EM_HEATMAP_TYPE;
import vn.attendance.lib.structure.NET_CB_VIDEOSTAT_HEATMAP;

/**
 * @author 47081
 * @version 1.0
 * @description 热度图回调函数的基类
 * @date 2020/9/24
 */
public abstract class BasicVideoStatHeatMapCallBack implements fVideoStatHeatMapCallBack {
  @Override
  public void invoke(
      NetSDKLib.LLong lAttachHandle,
      Pointer pBuf,
      Pointer pBinData,
      int dwBinDataLen,
      Pointer dwUser) {
    /** 获取热度图数据 */
    NET_CB_VIDEOSTAT_HEATMAP heatmap = new NET_CB_VIDEOSTAT_HEATMAP();
    ToolKits.GetPointerData(pBuf, heatmap);
    byte[] bytes = new byte[dwBinDataLen];
    pBinData.read(0, bytes, 0, dwBinDataLen);
    /** 处理热度图数据 */
    parseData(
        lAttachHandle.longValue(),
        heatmap.nToken,
        EM_HEATMAP_TYPE.getEmHeatMap(heatmap.emHeatMapType),
        bytes);
  }

  public abstract void parseData(
      long attachHandle, int nToken, EM_HEATMAP_TYPE type, byte[] binData);
}
