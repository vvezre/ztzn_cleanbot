package com.zt.cleanbot.model;
import lombok.Data;

@Data
public class Site {
    private Integer id;
    private String name;
    private double lat;
    private double lon;
    private String address;
    private Float totalCapacity;
    private String panelType;
    private Float siteArea;
    private Integer panelCount;
    private String siteOwner;
    private String contactPerson;
    private String contactPhone;
    private String siteStatus;
}