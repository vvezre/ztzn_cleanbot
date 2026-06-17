package com.zt.cleanbot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.DroneMapper;
import com.zt.cleanbot.model.Drone;
import com.zt.cleanbot.service.DroneService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
/**
 * 无人机服务实现类
 * 继承ServiceImpl获得基础的CRUD方法
 */
@Service
@Slf4j
public class DroneServiceImpl extends ServiceImpl<DroneMapper, Drone> implements DroneService {
    @Override
    public List<Drone> getAllDrones() {
        return this.list();
    }
}
