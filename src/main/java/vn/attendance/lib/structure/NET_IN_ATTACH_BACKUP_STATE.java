package vn.attendance.lib.structure;

import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import vn.attendance.lib.NetSDKLib;
import vn.attendance.lib.NetSDKLib.SdkStructure;

;

/**
 * 
 * @author 119178
 * CLIENT_AttachBackupTaskState接口输入参数
 * {@link NetSDKLib#CLIENT_AttachBackupTaskState}
 */
public class NET_IN_ATTACH_BACKUP_STATE extends SdkStructure{
	public int						dwSize;						// 结构体大小
	public int				        nGroupID;					// 任务组ID
	public Callback                 cbAttachState;              // 订阅备份状态回调
	public Pointer				    dwUser;                     // 用户数据
	public byte[]					bReserved=new byte[4];		// 字节对齐
	
	public NET_IN_ATTACH_BACKUP_STATE(){
        this.dwSize = this.size();
    }
}
