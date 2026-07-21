package com.zt.cleanbot.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 启动门禁实体
 * 控制设备是否允许启动，通过 MQTT retained 消息下发给上位机
 */
@Data
@TableName("railcar_startup_gate")
public class RailcarStartupGate {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("serial_number")
    private String serialNumber;

    @TableField("disabled")
    private Boolean disabled;

    @TableField("created_by")
    private String createdBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
