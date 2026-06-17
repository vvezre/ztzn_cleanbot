package com.zt.cleanbot.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

/**
 * 轨道车配置实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("railcar_config")
public class RailcarConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("product_model")
    private String productModel;

    @TableField("product_number")
    private String productNumber;

    @TableField("company_code")
    private String companyCode;

    @TableField("work_mode")
    private Integer workMode;

    @TableField("operation_mode")
    private Integer operationMode;

    @TableField("operation_enable")
    private Integer operationEnable;

    @TableField("edge_detection_delay")
    private Integer edgeDetectionDelay;

    @TableField("bridge_detection_time")
    private Integer bridgeDetectionTime;

    @TableField("error_return_time")
    private Integer errorReturnTime;

    @TableField("walking_speed")
    private Integer walkingSpeed;

    @TableField("brush_speed")
    private Integer brushSpeed;

    @TableField("bridge_speed")
    private Integer bridgeSpeed;

    @TableField("heartbeat_pulse")
    private Integer heartbeatPulse;

    @TableField("backup")
    private Integer backup;

    @TableField("battery_low_limit")
    private Integer batteryLowLimit;

    // ========== D12 接驳车专属参数 ==========

    @TableField("robot_in_position_time")
    private Integer robotInPositionTime;

    @TableField("limit_position_check_time")
    private Integer limitPositionCheckTime;

    @TableField("walk_position_check_time")
    private Integer walkPositionCheckTime;

    @TableField("walk_fast_speed")
    private Integer walkFastSpeed;

    @TableField("walk_slow_speed")
    private Integer walkSlowSpeed;

    @TableField("max_row_count")
    private Integer maxRowCount;

    @TableField("work_time_groups")
    private String workTimeGroups; // JSON字符串格式

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}