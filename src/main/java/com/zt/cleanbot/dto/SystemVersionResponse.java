package com.zt.cleanbot.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 系统版本信息响应。
 * 用于快速确认当前云平台服务是否已部署到指定发布批次。
 */
@Data
public class SystemVersionResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String serviceName;
    private String version;
    private String branch;
    private String releaseTag;
    private String updatedAt;
}
