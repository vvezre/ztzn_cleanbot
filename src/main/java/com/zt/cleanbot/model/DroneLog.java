package com.zt.cleanbot.model;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

@Data
public class DroneLog {
    private String id;
    private Integer droneId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date timestamp;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;
    private String operationType;
    private String taskLogId;
    private Integer operatorId;
    private String commandType;
    private String startPoint;
    private String endPoint;
    private Float startBattery;
    private Float endBattery;
}