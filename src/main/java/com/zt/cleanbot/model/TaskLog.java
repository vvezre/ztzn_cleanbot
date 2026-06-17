package com.zt.cleanbot.model;
import lombok.Data;
import java.util.Date;

@Data
public class TaskLog {
    private String id;//对应taskID
    private String droneId;
    private String vehicleId;//对应deviceId
    private Integer areaId;
    private String routeId;
    private Date startTime;
    private Date endTime;
    private String status;
    private Float cleaningArea;
    private Float efficiency;
    private Float powerRestored;
    private String cleaningMode;
    private String taskType;
}