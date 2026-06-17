package com.zt.cleanbot.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.VehicleLog;
import java.util.List;

public interface VehicleLogService extends IService<VehicleLog> {
    List<VehicleLog> getAllVehicleLogs();
    /**
     * 根据车辆ID获取最新的日志记录
     */
    VehicleLog getLatestByVehicleId(String vehicleId);
}