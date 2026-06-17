package com.zt.cleanbot.dao;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zt.cleanbot.model.ChargingStation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChargingStationMapper extends BaseMapper<ChargingStation> {
}