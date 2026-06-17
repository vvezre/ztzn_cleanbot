package com.zt.cleanbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Redis 中存储的设备状态数据（完整版）
 *
 * 数据来源：MQTT Uplink 消息（RAILCAR/S/+）
 * 数据格式：JSON
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VehicleRedisData {
    private String deviceId;
    private String status;
    private Double battery;
    private LocationDTO location;
    private Long lastUpdateTime;
    private String companyCode;
    private String productModel;
    private String productId;
    private Integer speed;
    private Double lat;
    private Double lon;
    private Double heading;
    private String action;
    private String taskName;
    private Integer curTaskIndex;
    private Integer taskCount;
    private Long timestamp;

    // ========== 工作模式参数 ==========
    private Integer runControl;      // 运行控制模式 (0-5)
    private Integer runEnable;       // 运行使能模式 (0-4)
    private Integer workMode;        // 工作方式 (0-4)

    // ========== 速度参数 ==========
    private Integer walkSpeed;       // 行走速度 (%)
    private Integer brushSpeed;      // 滚刷速度 (%)
    private Integer bridgeSpeed;     // 垮桥速度 (%)

    // ========== 运行数据 ==========
    private Double runTimeSingle;    // 单次运行时长(秒)
    private Double runTimeTotal;     // 总运行时长(秒)
    private Double mileageSingle;    // 单次运行里程(km)
    private Double mileageTotal;     // 总运行里程(km)

    // ========== 心跳 ==========
    private Double heartbeat;        // 心跳脉冲(秒)

    // ========== T 型设备状态上报字段 ==========
    private Double voltage;          // 电压 (V)
    private Double angle;            // 航向角 (°)
    private Boolean tracking;        // 纠偏状态
    private String pathPlanning;     // 路径规划 (left/right)
    private Integer leftEdge;        // 左边缘距离 (mm)
    private Integer rightEdge;       // 右边缘距离 (mm)
    private Boolean moveJudge;       // 是否移动中
    private Boolean detectQrcode;    // 是否检测到二维码
    private Boolean enterGarage;     // 是否在吊篮内
    private String mqttMessageType;  // MQTT 消息类型
    private String lastCommandId;    // 最近一次命令ID
    private String lastTraceId;      // 最近一次链路追踪ID
    private String lastCommand;      // 最近一次命令动作
    private String lastCommandStatus; // 最近一次命令状态
    private String lastCommandMessage; // 最近一次命令结果消息
    private String onlineState;      // 标准在线状态
    private String missionState;     // 标准任务状态
    private String controlState;     // 标准控制状态
    private String healthState;      // 标准健康状态
    private String faultState;       // 标准故障状态
    private List<String> supportedActions; // 标准能力：支持动作
    private List<String> supportedParams; // 标准能力：支持参数
    private List<String> supportedStatusFields; // 标准能力：支持状态字段
    private Map<String, Object> shadowDetail; // 标准detail透传

    // ========== D12 接驳车特有字段 ==========
    private Integer d12WorkWay;      // D12工作方式 (0-3)
    private Integer leftRowStart;    // 左起排开始
    private Integer leftRowEnd;      // 左起排结束
    private Integer rightRowStart;   // 右起排开始
    private Integer rightRowEnd;     // 右起排结束
    private Integer walkFastSpeed;   // 行走快速度(%)
    private Integer walkSlowSpeed;   // 行走慢速度(%)
    private Integer currentRowPosition; // 当前排位置
    private Integer batteryLowLimit; // 电池低电量警戒线(%)
    private Integer robotInPositionTime; // 机器人在位判断时间(ms)
    private Integer limitPositionCheckTime; // 极限位检时间(ms)
    private Integer walkPositionCheckTime; // 行走到位检时间(ms)

    /**
     * 内部类：位置信息
     */
    @Data
    public static class LocationDTO {
        private Double lon;
        private Double lat;
    }
}
