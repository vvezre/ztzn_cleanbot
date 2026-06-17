package com.zt.cleanbot.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.VehicleLogMapper;
import com.zt.cleanbot.model.VehicleLog;
import com.zt.cleanbot.service.VehicleLogService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class VehicleLogServiceImpl extends ServiceImpl<VehicleLogMapper, VehicleLog> implements VehicleLogService {

    @Override
    public List<VehicleLog> getAllVehicleLogs() {
        return this.list();
    }

    @Override
    public VehicleLog getLatestByVehicleId(String vehicleId) {
        QueryWrapper<VehicleLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("vehicle_id", vehicleId)
                .orderByDesc("timestamp")
                .last("LIMIT 1");
        return this.getOne(queryWrapper);
    }
}