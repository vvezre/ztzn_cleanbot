package com.zt.cleanbot.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户设备绑定表（关联表）
 */
@Data
@TableName("user_device")
public class UserDevice {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;           // 用户ID
    private Integer vehicleId;        // 车辆/设备ID（关联vehicle表）
    private LocalDateTime bindTime;   // 绑定时间
    private LocalDateTime unbindTime; // 解绑时间
    private String status;            // 绑定状态：active(激活), deleted(已删除)
}
