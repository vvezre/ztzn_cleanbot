package com.zt.cleanbot.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 设备信息表
 */
@Data
@TableName("device")
public class Device {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String companyCode;       // 公司代号，8字节
    private String productType;       // 产品型号，4字节
    private String productId;         // 产品编号，6字节（非唯一）
    private String serialNumber;      // 完整序列号
    private String deviceName;        // 设备名称
    private String deviceModel;       // 设备型号详细信息
    private String status;            // 设备状态：active, inactive, maintenance
    private Integer batteryCapacity;  // 电池容量（mAh）
    private LocalDateTime lastOnlineTime; // 最后在线时间
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间
}
