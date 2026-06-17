package com.zt.cleanbot.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.ChargingStation;
import java.util.List;

public interface ChargingStationService extends IService<ChargingStation> {
    List<ChargingStation> getAllChargingStations();
}