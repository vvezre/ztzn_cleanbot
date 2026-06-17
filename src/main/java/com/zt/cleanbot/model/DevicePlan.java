package com.zt.cleanbot.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备计划任务实体类
 */
@Data
@TableName("device_plan")
public class DevicePlan {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId; // 所属用户ID
    private String deviceId; // 设备序列号

    private Integer isRepeat; // 是否重复 1:重复 0:单次
    private LocalDate executeDate; // 单次执行日期

    private LocalDate startDate; // 重复任务的周期基准日（首次执行日期），用于计算周期，不随重建变化

    private String intervalUnit; // 重复单位: day/week/month
    private Integer intervalValue; // 重复间隔数值
    private String executeDays; // 每周或每月的具体号数(逗号分隔)

    private String executeTime; // 执行时间HH:mm

    private Integer status; // 状态: 1启用 0停用

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
