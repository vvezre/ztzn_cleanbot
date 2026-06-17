package com.zt.cleanbot.model;
import lombok.Data;
import java.util.Date;

@Data
public class ChargingLog {
    private String id;
    private Integer stationId;
    private String deviceType;
    private Date startTime;
    private Date endTime;
    private Float startBattery;
    private Float endBattery;
    private Float energyConsumed;
    private Float chargingSpeed;
}