package com.zt.cleanbot.model;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

@Data
public class VehicleLog {
    private String id;
    private String vehicleId;    // 对应deviceID
    private double lat;        // 存储坐标点字符串，如 "39.9042,116.4074"
    private double lon;        // 存储坐标点字符串，如 "39.9042,116.4074"
    private Float battery;       // 电池电量
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date timestamp;      // 指令时间
    private String commandType;  // 指令类型
}