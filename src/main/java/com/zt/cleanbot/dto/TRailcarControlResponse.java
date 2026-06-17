package com.zt.cleanbot.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * T型号小车控制响应 DTO
 */
@Data
public class TRailcarControlResponse implements Serializable {

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
     * 完整设备 ID（如：-T01250001）
     */
    private String deviceId;

    /**
     * 命令名称
     */
    private String command;

    /**
     * MQTT 主题
     */
    private String mqttTopic;

    /**
     * 命令唯一标识
     */
    private String commandId;

    /**
     * 链路追踪 ID
     */
    private String traceId;

    /**
     * 命令当前已知状态
     */
    private String commandStatus;

    /**
     * 控制时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 操作 ID（用于审计日志查询）
     */
    private Long operationId;

    /**
     * 成功响应
     */
    public static TRailcarControlResponse success(
            String deviceId,
            String command,
            String mqttTopic,
            Long operationId,
            String commandId,
            String traceId) {
        TRailcarControlResponse response = new TRailcarControlResponse();
        response.setSuccess(true);
        response.setMessage("T型号小车控制命令发送成功");
        response.setDeviceId(deviceId);
        response.setCommand(command);
        response.setMqttTopic(mqttTopic);
        response.setCommandId(commandId);
        response.setTraceId(traceId);
        response.setCommandStatus("DISPATCHED");
        response.setTimestamp(LocalDateTime.now());
        response.setOperationId(operationId);
        return response;
    }

    /**
     * 失败响应
     */
    public static TRailcarControlResponse failure(
            String deviceId,
            String command,
            String errorMessage,
            String commandId,
            String traceId) {
        TRailcarControlResponse response = new TRailcarControlResponse();
        response.setSuccess(false);
        response.setMessage(errorMessage);
        response.setDeviceId(deviceId);
        response.setCommand(command);
        response.setCommandId(commandId);
        response.setTraceId(traceId);
        response.setCommandStatus("FAILED");
        response.setTimestamp(LocalDateTime.now());
        return response;
    }
}
