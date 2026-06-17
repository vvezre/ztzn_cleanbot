package com.zt.cleanbot.dto;

import lombok.Data;

/**
 * 设备信息响应DTO
 */
@Data
public class DeviceInfoResponse {
    private Integer deviceId;       // 设备ID（数据库主键）
    private String companyCode;     // 公司代号
    private String productType;     // 产品型号
    private String productId;       // 产品编号（设备唯一标识）
    private String serialNumber;    // 序列号（完整：companyCode + productType + productId）
    private String status;          // 设备状态：online, offline, running, charging, idle
    private Boolean bound;          // 是否已绑定到当前用户
    private String boundUsername;   // 绑定的用户名
    private Integer userId;         // 绑定的用户ID
}
