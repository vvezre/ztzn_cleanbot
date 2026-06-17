package com.zt.cleanbot.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 设备配置请求 DTO
 * 前端传递业务参数，后端负责编码为 70 字节二进制协议
 */
@Data
public class DeviceConfigRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========== 基本信息 ==========
    /**
     * 设备 ID（完整序列号，如 -D01250001）
     */
    private String deviceId;

    /**
     * 公司代号（8字节 ASCII）
     */
    private String companyCode;

    /**
     * 产品型号（4字节 ASCII）
     */
    private String model;

    // ========== 信息识别码 ==========
    /**
     * 信息识别码高字节（命令类型）
     * 00: 设置参数指令
     * 02: 捆绑交互指令
     */
    private Integer infoCommandType;

    /**
     * H10 璧峰鍦板潃锛岄粯璁?0
     */
    private Integer startAddress;

    /**
     * H11 鏁版嵁闀垮害锛岄粯璁?35
     */
    private Integer dataLength;

    /**
     * 信息识别码低字节（绑定状态）
     * 00: 未绑定
     * 01-05: 已绑定对应数量设备
     */
    private Integer bindStatus;

    /**
     * D12 绑定的 D01 设备完整 ID（如 -D01250001）
     */
    private String bindDeviceId;

    /**
     * D12 绑定的 D01 设备完整 ID 列表（最多5台）
     */
    private List<String> bindDeviceIds;

    // ========== 工作方式 ==========
    /**
     * 工作方式 (2字节 UInt16)
     * 0:无效, 1:每日, 2:每月, 3:每年, 4:每周
     */
    private Integer workWay;

    // ========== 时间组 (4组) ==========
    /**
     * 时间组 1
     */
    private TimeGroup time1;

    /**
     * 时间组 2
     */
    private TimeGroup time2;

    /**
     * 时间组 3
     */
    private TimeGroup time3;

    /**
     * 时间组 4
     */
    private TimeGroup time4;

    // ========== 控制模式 ==========
    /**
     * 控制模式 (2字节 UInt16)
     * 0:无效, 1:Auto, 2:Stop, 3:Reset, 4:Loop, 5:Manual
     */
    private Integer controlMode;

    /**
     * 使能模式 (2字节 UInt16)
     * 0:无效, 1:检+左+单, 2:检+左+双, 3:检+右+单, 4:检+右+双
     */
    private Integer enableMode;

    // ========== 检测参数 ==========
    /**
     * 到边检测延时 (2字节 UInt16，单位: ms)
     * 如 1000 = 1000ms = 1s
     */
    private Integer edgeDelay;

    /**
     * 垮桥检测时间 (2字节 UInt16，单位: 0.001s)
     * 如 6000 = 6.000s
     */
    private Integer bridgeTime;

    /**
     * 纠错校正长度控制设置 (2字节 UInt16，单位: 0.01m)
     * 如 1000 = 10.00m
     */
    private Integer errorReturnTime;

    // ========== 速度参数 ==========
    /**
     * 行走速度 (2字节 UInt16，百分比)
     * 如 800 = 80.0%
     */
    private Integer walkSpeed;

    /**
     * 滚刷速度 (2字节 UInt16，百分比)
     * 如 1000 = 100.0%
     */
    private Integer brushSpeed;

    /**
     * 垮桥速度 (2字节 UInt16，百分比)
     * 如 800 = 80.0%
     */
    private Integer bridgeSpeed;

    // ========== 其他 ==========
    /**
     * 心跳脉冲设置 (2字节 UInt16，单位: 0.01s)
     * 如 100 = 1.00s
     */
    private Integer heartbeatSet;

    /**
     * 电池低电量警戒线 (2字节，BCD)
     * 如 50 = 5.0%
     */
    private Integer batteryLowLimit;

    /**
     * 备用字段1 (2字节，默认为 0)
     */
    private Integer reserved;

    /**
     * 备用字段2 (2字节，默认为 0)
     */
    private Integer reserved2;

    // ========== D12 专用参数 ==========
    /**
     * 接驳车-机器人在位判断时间 (2字节，BCD)
     */
    private Integer robotInPositionTime;

    /**
     * 接驳车-前后极限位检时间 (2字节，BCD)
     */
    private Integer limitPositionCheckTime;

    /**
     * 接驳车-前后行走到位检时间 (2字节，BCD)
     */
    private Integer walkPositionCheckTime;

    /**
     * 接驳车-前后行走快速度 (2字节，BCD)
     */
    private Integer walkFastSpeed;

    /**
     * 接驳车-前后行走慢速度 (2字节，BCD)
     */
    private Integer walkSlowSpeed;

    /**
     * 接驳车-工作区域最大排数量 (2字节，BCD)
     */
    private Integer maxRowCount;

    /**
     * 交互指令-状态故障信息代码 (2字节，BCD)
     */
    private Integer faultCode;

    /**
     * 交互指令-当前排位置 (2字节，BCD)
     */
    private Integer currentRowPosition;

    /**
     * 交互指令-完成状态 (29-30字节，BCD)
     */
    private Integer interactionWorkDataComplete;

    /**
     * H13-H20 鎹嗙粦浜や簰鍖哄潡锛?16 瀛楄妭 / 32 浣?HEX
     */
    private String interactionPayloadHex;

    /**
     * 交互指令复用的设置帧尾部 (31-70字节，80位HEX)
     */
    private String interactionSettingTailHex;

    /**
     * 是否为快捷按钮触发的控制请求
     * 普通用户仅允许通过快捷按钮下发控制，不允许修改参数配置
     */
    private Boolean quickAction;

    /**
     * 小程序按钮按下时的客户端时间戳（毫秒）
     */
    private Long clientClickTimestamp;

    // ========== 内部字段 ==========
    /**
     * 用户 ID（从 JWT Token 中提取，用于审计）
     */
    private transient Integer userId;

    /**
     * 用户名（从 JWT Token 中提取，用于审计）
     */
    private transient String username;

    /**
     * 后端收到请求的服务器时间戳（毫秒）
     */
    private transient Long serverReceivedTimestamp;

    /**
     * 后端完成 MQTT 发送流程的服务器时间戳（毫秒）
     */
    private transient Long mqttSentTimestamp;

    /**
     * 命令唯一标识（由云平台生成，用于命令追踪）
     */
    private transient String commandId;

    /**
     * 链路追踪标识
     */
    private transient String traceId;

    /**
     * 时间组内部类
     */
    @Data
    public static class TimeGroup implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 年周或年月 (2字节 BCD/Hex)
         */
        private String yearWeek;

        /**
         * 月日 (2字节 BCD/Hex)
         */
        private String monDay;

        /**
         * 时分 (2字节 BCD/Hex)
         */
        private String hrMin;
    }

    /**
     * 扩展参数（用于不同策略的特定参数，如 D12 的作业排数）
     */
    private java.util.Map<String, Object> params;

    /**
     * 获取扩展参数（安全获取）
     */
    public Object getParam(String key) {
        if (params == null) {
            return null;
        }
        return params.get(key);
    }
}
