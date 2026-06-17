package com.zt.cleanbot.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备控制审计日志
 * 记录所有设备控制操作，用于安全审计和故障排查
 */
@Data
@TableName("device_control_audit")
public class DeviceControlAudit implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 设备 ID（完整序列号）
     */
    private String deviceId;

    /**
     * 操作用户 ID
     */
    private Integer userId;

    /**
     * 操作用户名
     */
    private String username;

    /**
     * 操作类型
     * CONTROL: 设备控制, CONFIG_UPDATE: 配置更新, MODE_CHANGE: 模式切换
     */
    private String operationType;

    /**
     * MQTT 主题
     */
    private String mqttTopic;

    /**
     * 操作结果
     * SUCCESS, FAILURE
     */
    private String operationResult;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 操作时间
     */
    private LocalDateTime operationTime;

    /**
     * 客户端 IP 地址
     */
    private String clientIp;

    /**
     * 额外信息（JSON 格式）
     */
    private String extraInfo;

    /**
     * 是否为紧急模式操作
     */
    private Boolean isEmergencyMode;

    /**
     * 追踪 ID（用于关联多个操作）
     */
    private String traceId;
}
