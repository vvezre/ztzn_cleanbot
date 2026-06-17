package com.zt.cleanbot.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class VehicleCommandDTO implements Serializable {
    private String deviceId;
    private Long timestamp;
    private String command;

    public VehicleCommandDTO() {
        this.timestamp = System.currentTimeMillis();
    }

    public VehicleCommandDTO(String deviceId, String command) {
        this();
        this.deviceId = deviceId;
        this.command = command;
    }
}