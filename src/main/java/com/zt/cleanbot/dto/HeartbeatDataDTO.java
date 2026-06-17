package com.zt.cleanbot.dto;
import lombok.Data;

@Data
public class HeartbeatDataDTO {
    private String status;
    private Double battery;
    private LocationDTO location;
}