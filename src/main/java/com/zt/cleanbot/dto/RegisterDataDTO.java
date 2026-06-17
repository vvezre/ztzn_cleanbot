package com.zt.cleanbot.dto;
import lombok.Data;

@Data
public class RegisterDataDTO {
    private String name;
    private String serialNumber;
    private String brand;
    private String model;
    private String vehicleType;
    private Integer batteryCapacity;
    private Double weight;
    private Double cleaningWidth;
    private LocationDTO location;
}