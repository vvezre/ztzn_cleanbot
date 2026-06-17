package com.zt.cleanbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.DevicePlanMapper;
import com.zt.cleanbot.model.DevicePlan;
import com.zt.cleanbot.service.DevicePlanService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DevicePlanServiceImpl extends ServiceImpl<DevicePlanMapper, DevicePlan> implements DevicePlanService {

    @Override
    public List<DevicePlan> getPlansByDeviceId(String deviceId) {
        QueryWrapper<DevicePlan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("device_id", deviceId);
        // 按执行时间或ID排序
        queryWrapper.orderByDesc("id");
        return baseMapper.selectList(queryWrapper);
    }
}
