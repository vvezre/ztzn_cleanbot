package com.zt.cleanbot.model;
import lombok.Data;

@Data
public class Register {

    private String deviceId;
    private Long  timestamp;
    private String name;
    private String brand;
    private String model;
    private String vehicleType;
    private Integer batteryCapacity;
    private double weight;
    private double cleaningWidth;
    private double lat;
    private double lon;
}
