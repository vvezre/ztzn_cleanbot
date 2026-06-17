package com.zt.cleanbot.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zt.cleanbot.model.Device;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
}
