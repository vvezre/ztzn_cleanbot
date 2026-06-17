package com.zt.cleanbot.model;
import lombok.Data;

@Data
public class ChargingStation {
    private Integer id;
    private String name;
    private String location;
    private String status;
    private String stationType;
    private Float voltageLevel;
}