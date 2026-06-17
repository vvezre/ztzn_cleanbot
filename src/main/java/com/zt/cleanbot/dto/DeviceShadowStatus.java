package com.zt.cleanbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 标准化设备状态快照。
 * 作为云平台统一状态出口的基础模型，兼容 T 型 Python 机器人和 D 型 IoT 设备。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceShadowStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean exists;
    private Integer vehicleId;
    private String deviceId;
    private String serialNumber;
    private String deviceType;
    private String productType;
    private String productId;
    private String companyCode;
    private String name;
    private String vehicleType;

    private String onlineState;
    private String missionState;
    private String controlState;
    private String healthState;
    private String faultState;
    private String lastCommandId;
    private String lastCommandStatus;

    private String rawStatus;
    private String mqttMessageType;
    private Double battery;
    private Double voltage;
    private Double angle;
    private Long updatedAt;
    private LocationData taskOrigin;
    private CurrentLocationData currentLocation;
    private Double distanceToTaskOriginM;
    private Double taskOriginToleranceM;
    private Boolean isAtTaskOrigin;

    private LocationData location;
    private List<String> supportedActions;
    private List<String> supportedParams;
    private List<String> supportedStatusFields;
    private Map<String, Object> detail;

    @Data
    public static class LocationData implements Serializable {
        private static final long serialVersionUID = 1L;

        private Double lon;
        private Double lat;
    }

    @Data
    public static class CurrentLocationData implements Serializable {
        private static final long serialVersionUID = 1L;

        private Double lon;
        private Double lat;
        private Double heading;
    }
}
