package com.zt.cleanbot.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 车辆/设备信息表
 */
@Data
@TableName("vehicle")
public class Vehicle {
    @TableId(type = IdType.AUTO)
    private Integer id;

    // ========== 设备标识信息（二维码数据） ==========
    private String companyCode; // 公司代号，8字节
    private String productType; // 产品型号，4字节（如 -D01）
    private String productId; // 产品编号，6字节（非唯一，不同型号可能相同）
    private String serialNumber; // 唯一标识（productType + productId，如 -D01250001）

    // ========== 设备基本信息 ==========
    private String name; // 设备名称
    private String brand; // 品牌
    private String model; // 型号详细信息
    private String status; // 设备状态：active, inactive, maintenance
    private String vehicleType; // 车辆类型

    // D12 绑定的 D01 设备完整序列号列表（逗号分隔，最多5个）
    private String boundD01Serials;

    // ========== 设备参数 ==========
    private Integer batteryCapacity; // 电池容量（mAh）
    private Float weight; // 重量（kg）
    private Float cleaningWidth; // 清洁宽度（m）

    // ========== 时间字段 ==========
    private LocalDateTime lastOnlineTime; // 最后在线时间
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间

    // ========== 累计统计数据 ==========
    private Double totalRunTime; // 累计运行时间(秒)
    private Double totalMileage; // 累计运行里程(km)
    private Double totalArea; // 累计清扫面积(m²)
}
