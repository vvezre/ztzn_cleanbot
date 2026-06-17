package com.zt.cleanbot.model;

import lombok.Data;

@Data
public class Heartbeat {
    private String deviceId;
    private Long  timestamp;
    private String status;
    private double battery;
    private double lat;
    private double lon;


}





