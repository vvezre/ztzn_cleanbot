package com.zt.cleanbot.dto;

import lombok.Data;

@Data
public class VehicleLogDataDTO {
    private LocationDTO point;
    private Double battery;
    private Long timestamp;
    private String commandType;
}