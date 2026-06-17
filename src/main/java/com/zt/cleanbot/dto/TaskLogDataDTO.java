package com.zt.cleanbot.dto;


import lombok.Data;

@Data
public class TaskLogDataDTO {
    private String taskId;
    private Integer areaId;
    private String routeId;
    private Long startTime;
    private Long endTime;
    private String status;
    private String cleaningMode;
    private String cleaningArea;
    private String taskType;
}
