package com.zt.cleanbot.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.ChargingStationMapper;
import com.zt.cleanbot.model.ChargingStation;
import com.zt.cleanbot.service.ChargingStationService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ChargingStationServiceImpl extends ServiceImpl<ChargingStationMapper, ChargingStation> implements ChargingStationService {

    @Override
    public List<ChargingStation> getAllChargingStations() {
        return this.list();
    }
}