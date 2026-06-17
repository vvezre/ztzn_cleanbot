package com.zt.cleanbot.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备控制响应 DTO
 */
@Data
public class DeviceControlResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 设备 ID
     */
    private String deviceId;

    /**
     * MQTT 主题
     */
    private String mqttTopic;

    /**
     * 控制时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 操作 ID（用于审计日志查询）
     */
    private Long operationId;

    /**
     * 链路跟踪 ID
     */
    private String traceId;

    /**
     * 命令唯一标识
     */
    private String commandId;

    /**
     * 命令当前已知状态
     */
    private String commandStatus;

    /**
     * 客户端点击到服务器收到请求的耗时（毫秒）
     */
    private Long clientToServerMs;

    /**
     * 服务器收到请求到 MQTT 发出的耗时（毫秒）
     */
    private Long serverToMqttMs;

    /**
     * 客户端点击到 MQTT 发出的总耗时（毫秒）
     */
    private Long clientToMqttMs;

    /**
     * 服务器收到请求到返回响应的总耗时（毫秒）
     */
    private Long serverRequestTotalMs;

    /**
     * 客户端点击到收到响应的总耗时（毫秒）
     */
    private Long clientToResponseMs;

    /**
     * 成功响应
     */
    public static DeviceControlResponse success(String deviceId, String mqttTopic, Long operationId) {
        DeviceControlResponse response = new DeviceControlResponse();
        response.setSuccess(true);
        response.setMessage("设备控制命令发送成功");
        response.setDeviceId(deviceId);
        response.setMqttTopic(mqttTopic);
        response.setCommandStatus("DISPATCHED");
        response.setTimestamp(LocalDateTime.now());
        response.setOperationId(operationId);
        return response;
    }

    /**
     * 失败响应
     */
    public static DeviceControlResponse failure(String deviceId, String errorMessage) {
        DeviceControlResponse response = new DeviceControlResponse();
        response.setSuccess(false);
        response.setMessage(errorMessage);
        response.setDeviceId(deviceId);
        response.setCommandStatus("FAILED");
        response.setTimestamp(LocalDateTime.now());
        return response;
    }
}
