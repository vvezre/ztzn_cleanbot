package com.zt.cleanbot.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 小程序/App 机器人设备响应 DTO
 *
 * 整合静态设备信息（Vehicle表）和实时状态数据（Redis/MQTT）
 * 数据来源：
 * 1. 静态信息：从 vehicle 表获取
 * 2. 实时状态：从 Redis 获取（由 MQTT 消息更新）
 */
@Data
public class MiniAppRobotResponse {

    // ========== 静态信息（来自 vehicle 表） ==========

    /**
     * 设备 ID（数据库主键）
     */
    private Integer id;

    /**
     * 完整序列号（18字节）
     * 格式：companyCode + productType + productId
     * 例如：ZTZN-PVC-D01-250001
     */
    private String serialNumber;

    /**
     * 设备名称
     */
    private String name;

    /**
     * 公司代号（8字节）
     */
    private String companyCode;

    /**
     * 产品型号（4字节）
     * 例如：-D01, -D11, -D21, -T01, -T11, -T21, -D12, -T12
     */
    private String productType;

    /**
     * 产品型号（与 MQTT/云平台字段保持一致）
     */
    private String productModel;

    /**
     * 产品编号（6字节，设备唯一标识）
     */
    private String productId;

    /**
     * MQTT 设备 ID（用于控制和订阅）
     * 格式：productType + productId
     * 例如：-D01250001
     */
    private String deviceId;

    /**
     * 车辆类型
     * railcar: 轨道车
     * tracklayer: 履带车
     */
    private String vehicleType;

    /**
     * 标准设备类型（T_PYTHON / D_IOT / UNKNOWN）
     */
    private String deviceType;

    // ========== 实时状态（来自 Redis/MQTT） ==========

    /**
     * 在线状态
     */
    private Boolean online;

    /**
     * 设备状态
     * running: 工作中
     * charging: 充电中
     * idle: 空闲
     * offline: 离线
     */
    private String status;

    /**
     * 标准在线状态（ONLINE / OFFLINE / UNKNOWN）
     */
    private String onlineState;

    /**
     * 标准任务状态（IDLE / RUNNING / CHARGING / FAILED ...）
     */
    private String missionState;

    /**
     * 标准控制状态（READY / BUSY / STOPPED ...）
     */
    private String controlState;

    /**
     * 标准健康状态（NORMAL / WARNING / ERROR / UNKNOWN）
     */
    private String healthState;

    /**
     * 标准故障状态（NONE / LOW_BATTERY / COMM_ERROR ...）
     */
    private String faultState;

    /**
     * 最近一次命令ID
     */
    private String lastCommandId;

    /**
     * 最近一次命令状态
     */
    private String lastCommandStatus;

    /**
     * 电池电量 (%)
     */
    private Double battery;

    /**
     * 标准状态更新时间（时间戳）
     */
    private Long updatedAt;

    /**
     * 最后在线时间
     */
    private LocalDateTime lastOnlineTime;

    // ========== 实时工作数据（来自 MQTT Uplink） ==========

    /**
     * 运行控制模式（工作模式）
     * 0:无效, 1:Auto, 2:Stop, 3:Reset, 4:Loop, 5:Manual
     */
    private Integer runControl;

    /**
     * 运行使能模式
     * 0:无效, 1:检+左+单, 2:检+左+双, 3:检+右+单, 4:检+右+双
     */
    private Integer runEnable;

    /**
     * 工作方式
     * 0:无效, 1:每日, 2:每月, 3:每年, 4:每周
     */
    private Integer workMode;

    /**
     * 行走速度 (%)
     */
    private Integer walkSpeed;

    /**
     * 滚刷速度 (%)
     */
    private Integer brushSpeed;

    /**
     * 垮桥速度 (%)
     */
    private Integer bridgeSpeed;

    /**
     * 单次运行时长（秒）
     */
    private Double runTimeSingle;

    /**
     * 总运行时长（秒）
     */
    private Double runTimeTotal;

    /**
     * 单次运行里程（km）
     */
    private Double mileageSingle;

    /**
     * 总运行里程（km）
     */
    private Double mileageTotal;

    /**
     * 心跳脉冲（秒）
     */
    private Double heartbeat;

    // ========== T 型设备状态上报字段 ==========

    /**
     * 电压 (V)
     */
    private Double voltage;

    /**
     * 航向角 (°)
     */
    private Double angle;

    /**
     * 纠偏功能状态
     */
    private Boolean tracking;

    /**
     * 路径规划模式 (left/right)
     */
    private String pathPlanning;

    /**
     * 左侧边缘距离 (mm)
     */
    private Integer leftEdge;

    /**
     * 右侧边缘距离 (mm)
     */
    private Integer rightEdge;

    /**
     * 是否正在移动
     */
    private Boolean moveJudge;

    /**
     * 是否检测到二维码
     */
    private Boolean detectQrcode;

    /**
     * 是否在吊篮内
     */
    private Boolean enterGarage;

    /**
     * MQTT 消息类型（vehicle_status / event / command_result / online / offline）
     */
    private String mqttMessageType;

    /**
     * 当前行走速度
     */
    private Integer speed;

    /**
     * 当前纬度
     */
    private Double lat;

    /**
     * 当前经度
     */
    private Double lon;

    /**
     * 当前航向角
     */
    private Double heading;

    /**
     * 当前动作
     */
    private String action;

    /**
     * 当前任务名称
     */
    private String taskName;

    /**
     * 当前任务段索引
     */
    private Integer curTaskIndex;

    /**
     * 当前任务总段数
     */
    private Integer taskCount;

    /**
     * 状态快照时间戳
     */
    private Long timestamp;

    private LocationData taskOrigin;

    private CurrentLocationData currentLocation;

    private Double distanceToTaskOriginM;

    private Double taskOriginToleranceM;

    private Boolean isAtTaskOrigin;

    // ========== GPS 位置信息 ==========

    /**
     * 经度
     */
    private Double longitude;

    /**
     * 纬度
     */
    private Double latitude;

    // ========== D12 接驳车特有字段 ==========

    /**
     * D12 工作方式
     * 0:无效, 1:接左, 2:接右, 3:接左右
     */
    private Integer d12WorkWay;

    /**
     * D12 已绑定 D01 设备序列号列表（如 -D01250001）
     */
    private List<String> boundDeviceIds;

    /**
     * D12 已绑定 D01 数量（0-5）
     */
    private Integer boundDeviceCount;

    /**
     * 左起第几排开始 (1-50)
     */
    private Integer leftRowStart;

    /**
     * 左起第几排结束 (1-50)
     */
    private Integer leftRowEnd;

    /**
     * 右起第几排开始 (1-50)
     */
    private Integer rightRowStart;

    /**
     * 右起第几排结束 (1-50)
     */
    private Integer rightRowEnd;

    /**
     * 行走快速度 (%)
     */
    private Integer walkFastSpeed;

    /**
     * 行走慢速度 (%)
     */
    private Integer walkSlowSpeed;

    /**
     * 运行当前排位置 (1-50)
     */
    private Integer currentRowPosition;

    /**
     * 电池低电量警戒线 (%)
     */
    private Integer batteryLowLimit;

    /**
     * 机器人在位判断时间 (ms)
     */
    private Integer robotInPositionTime;

    /**
     * 极限位检时间 (ms)
     */
    private Integer limitPositionCheckTime;

    /**
     * 行走到位检时间 (ms)
     */
    private Integer walkPositionCheckTime;

    /**
     * 设备支持的动作列表
     */
    private List<String> supportedActions;

    /**
     * 设备支持的参数字段列表
     */
    private List<String> supportedParams;

    /**
     * 设备支持的状态字段列表
     */
    private List<String> supportedStatusFields;

    /**
     * 设备扩展状态明细
     */
    private Map<String, Object> shadowDetail;

    @Data
    public static class LocationData {
        private Double lon;
        private Double lat;
    }

    @Data
    public static class CurrentLocationData {
        private Double lon;
        private Double lat;
        private Double heading;
    }
}
