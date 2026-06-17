package com.zt.cleanbot.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 设备状态查询响应
 */
@Data
public class DeviceStatusQueryResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 设备是否存在
     */
    private Boolean exists;

    /**
     * 电量百分比
     */
    private Double battery;

    /**
     * 设备状态
     * cleaning: 清扫中
     * stopped: 已停止
     * manual: 手动模式
     * resetting: 复位中
     * unknown: 未知
     */
    private String status;

    /**
     * 工作模式描述
     */
    private String operationMode;

    /**
     * 位置信息
     */
    private LocationData location;

    /**
     * 最后更新时间（时间戳）
     */
    private Long lastUpdateTime;

    /**
     * 位置数据
     */
    @Data
    public static class LocationData {
        /**
         * 经度
         */
        private Double lon;

        /**
         * 纬度
         */
        private Double lat;
    }
}
