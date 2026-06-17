package com.zt.cleanbot.model;
import lombok.Data;
import java.util.Date;

@Data
public class Drone {

    private Integer id;           // 主键ID
    private String name;         // 名字
    private String status;       // 状态：active-空闲, working-工作中, charging-充电中, disabled-维护
    private String brand;        // 品牌
    private String model;        // 型号
    private String serialNumber; // 设备唯一标识
    private Float maxPayload;    // 最大载重(KG)
}
