package com.zt.cleanbot.model;
import lombok.Data;

import java.util.Date;

@Data
public class RouteLog {
    private String id;
    private String taskId;
    private String type;//发到vehicle就是vehicle
    private Date timestamp;
    private String vehicleId;//对应deviceId
    private String droneId;
    private String gpsPath;
    private double lat;
    private double lon;

}