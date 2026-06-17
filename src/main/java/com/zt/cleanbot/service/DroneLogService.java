package com.zt.cleanbot.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.DroneLog;
import java.util.List;

public interface DroneLogService extends IService<DroneLog> {
    List<DroneLog> getAllDroneLogs();
}