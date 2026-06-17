package com.zt.cleanbot.dto;

import lombok.Data;

@Data
public class VehicleIntegratedDTO {
    // 来自Redis
    private String deviceId;
    private Double lon;
    private Double lat;
    private Double battery;
    private String status;
    private Long lastUpdateTime;

    // 来自MySQL
    private String name;
    private String serialNumber;
    private String brand;
    private String model;
    private String vehicleType;
    private Integer batteryCapacity;
    private Float weight;
    private Float cleaningWidth;


    // 新增省份信息
    private String province;

    // 整合状态（Redis和数据库都有数据）
    private Boolean integrated = true;
}