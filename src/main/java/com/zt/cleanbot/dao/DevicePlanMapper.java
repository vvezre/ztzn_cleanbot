package com.zt.cleanbot.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zt.cleanbot.model.DevicePlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备计划任务 Mapper
 */
@Mapper
public interface DevicePlanMapper extends BaseMapper<DevicePlan> {
}
