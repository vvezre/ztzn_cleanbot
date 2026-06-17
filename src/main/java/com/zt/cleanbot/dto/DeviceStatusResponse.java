package com.zt.cleanbot.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备状态响应 DTO
 * 从后端缓存或数据库返回设备实时状态
 */
@Data
public class DeviceStatusResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========== 基本信息 ==========
    /**
     * 设备 ID（完整序列号）
     */
    private String deviceId;

    /**
     * 公司代号
     */
    private String companyCode;

    /**
     * 产品型号
     */
    private String model;

    /**
     * 设备状态
     * online: 在线, offline: 离线, running: 运行中, charging: 充电中, idle: 空闲
     */
    private String status;

    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;

    // ========== 工作信息 ==========
    /**
     * 当前工作方式
     */
    private Integer curWorkWay;

    /**
     * 当前任务时间
     */
    private TaskTime task;

    /**
     * 单次运行时长 (x 0.01s)
     */
    private Long runTimeSingle;

    /**
     * 总运行时长 (秒)
     */
    private Long runTimeTotal;

    /**
     * 单次运行里程 (x 0.001km)
     */
    private Double distSingle;

    /**
     * 总运行里程 (km)
     */
    private Double distTotal;

    // ========== GPS 信息 ==========
    /**
     * GPS 经度 (x 0.00001)
     */
    private Double gpsLon;

    /**
     * GPS 纬度 (x 0.00001)
     */
    private Double gpsLat;

    // ========== 模式状态 ==========
    /**
     * 当前运行控制模式
     */
    private Integer modeStatus;

    /**
     * 当前使能状态
     */
    private Integer enableStatus;

    // ========== 电池信息 ==========
    /**
     * 电池电量 (百分比，已除以10)
     */
    private Integer batteryLevel;

    // ========== 速度信息 ==========
    /**
     * 当前行走速度 (百分比，已除以10)
     */
    private Integer curWalkSpeed;

    /**
     * 当前滚刷速度 (百分比，已除以10)
     */
    private Integer curBrushSpeed;

    /**
     * 当前垮桥速度 (百分比，已除以10)
     */
    private Integer curBridgeSpeed;

    // ========== 其他 ==========
    /**
     * 心跳脉冲状态 (秒，已除以100)
     */
    private Integer heartbeatStat;

    /**
     * 任务时间内部类
     */
    @Data
    public static class TaskTime implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 年周或年月
         */
        private String yearWeek;

        /**
         * 月日
         */
        private String monDay;

        /**
         * 时分
         */
        private String hrMin;
    }
}
