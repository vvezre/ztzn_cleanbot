package com.zt.cleanbot.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zt.cleanbot.model.Drone;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DroneMapper extends BaseMapper<Drone> {
    // 什么都不用写！自动获得所有CRUD方法
}
