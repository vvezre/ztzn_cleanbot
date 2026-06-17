package com.zt.cleanbot.dto;

import lombok.Data;

@Data
public class BaseMessageDTO {
    private String deviceId;
    private Long timestamp;
    private Object data;
}