package vn.attendance.lib.structure;


import vn.attendance.lib.NetSDKLib;

/** 
* @author 291189
* @description  挂空悬浮物检测异常输出结果 
* @date 2022/06/28 19:44:54
*/
public class NET_AIRBORNE_DETECT extends NetSDKLib.SdkStructure {
/** 
挂空悬浮物具体类型 {@link vn.attendance.lib.enumeration.EM_AIRBORNE_TYPE}
*/
public			int					emAirborneType;
/** 
包围盒
*/
public NET_RECT stuBoundingBox=new NET_RECT();

public NET_AIRBORNE_DETECT(){
}
}