package com.zt.cleanbot.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.DroneLogMapper;
import com.zt.cleanbot.model.DroneLog;
import com.zt.cleanbot.service.DroneLogService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DroneLogServiceImpl extends ServiceImpl<DroneLogMapper, DroneLog> implements DroneLogService {

    @Override
    public List<DroneLog> getAllDroneLogs() {
        return this.list();
    }
}