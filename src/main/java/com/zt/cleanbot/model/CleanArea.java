package com.zt.cleanbot.model;
import lombok.Data;

@Data
public class CleanArea {
    private Integer id;
    private String name;
    private String description;
    private String location;
    private Float size;
    private String status;
    private Integer siteId;
    private Integer priority;
    private String region_code;

}