package com.zt.cleanbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.DevicePlan;
import java.util.List;

public interface DevicePlanService extends IService<DevicePlan> {

    /**
     * 根据设备ID获取计划列表
     */
    List<DevicePlan> getPlansByDeviceId(String deviceId);
}
