package com.zt.cleanbot.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zt.cleanbot.model.DeviceControlAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备控制审计日志 Mapper
 */
@Mapper
public interface DeviceControlAuditMapper extends BaseMapper<DeviceControlAudit> {
}
