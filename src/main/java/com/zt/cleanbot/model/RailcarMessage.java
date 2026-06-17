package com.zt.cleanbot.model;

import lombok.Data;

import java.util.Date;

@Data
public class RailcarMessage {
    // 基本信息
    private String deviceId;
    private Date timestamp;
    private String companyCode;
    private String serialNumber;
    private String productModel;
    private String productNumber;

    // 兼容旧字段，D 新协议状态帧中通常不再携带这些值
    private String workMode;
    private String workModeDescription;
    private String weekYear;
    private String monthDay;
    private String hourMinute;

    // 运行与定位
    private Double longitude;
    private Double latitude;
    private Double singleRunTime;
    private Double totalRunTime;
    private Double singleRunDistance;
    private Double totalRunDistance;

    // 状态字
    private Integer bindSendEnable;
    private Integer interactionCommand;
    private String interactionPayloadHex;
    private String operationMode;
    private String operationModeDescription;
    private String operationEnable;
    private String operationEnableDescription;
    private Integer faultCode;
    private Integer currentRowPosition;
    private Integer workCycleCount;

    // 兼容旧前端/日志字段，现同步 H27
    private Integer workDataComplete;

    // 常规状态量
    private Double batteryLevel;
    private String backup1;
    private String backup2;
    private Double currentSpeed;
    private Double heartbeat;
    private Double brushSpeed;
    private Double bridgeSpeed;
    private String backup3;
    private String backup4;

    // D12 特有字段
    private String d12WorkWay;
    private String d12WorkWayDescription;
    private Integer leftRowStart;
    private Integer leftRowEnd;
    private Integer rightRowStart;
    private Integer rightRowEnd;
    private Integer robotInPositionTime;
    private Integer limitPositionCheckTime;
    private Integer walkPositionCheckTime;
    private Double walkFastSpeed;
    private Double walkSlowSpeed;
    private Integer waitStopRowPosition;
    private Double batteryLowLimit;

    // 原始数据
    private String rawData;
}
