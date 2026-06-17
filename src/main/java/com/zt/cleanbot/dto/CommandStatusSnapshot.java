package com.zt.cleanbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 统一命令状态快照。
 * 作为云平台显式命令生命周期查询接口的标准输出。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommandStatusSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean exists;
    private String commandId;
    private String traceId;
    private String deviceId;
    private String deviceType;
    private String action;
    private String status;
    private String message;
    private String operator;
    private Long timeoutMs;
    private Long createdAt;
    private Long updatedAt;
    private Boolean terminal;
    private Map<String, Object> detail;
}
