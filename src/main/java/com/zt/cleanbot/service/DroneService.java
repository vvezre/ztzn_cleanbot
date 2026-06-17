package com.zt.cleanbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.Drone;

import java.util.List;

public interface DroneService extends IService<Drone> {

    List<Drone> getAllDrones();

}
