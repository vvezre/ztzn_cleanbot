package com.zt.cleanbot.model;
import lombok.Data;
import java.util.Date;

@Data
public class OperationLog {
    private String id;
    private Integer userId;
    private Date timestamp;
    private String ipAddress;
    private String operationType;
    private String operationTarget;
    private String targetId;
    private String details;
    private String result;
}