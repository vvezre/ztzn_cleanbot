package com.zt.cleanbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.DeviceMapper;
import com.zt.cleanbot.model.Device;
import com.zt.cleanbot.service.DeviceService;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    @Override
    public Device findByProductId(String productId) {
        QueryWrapper<Device> query = new QueryWrapper<>();
        query.eq("product_id", productId);
        return this.getOne(query);
    }

    @Override
    public Device createOrUpdate(Device device) {
        Device existDevice = findByProductId(device.getProductId());

        if (existDevice != null) {
            // 更新现有设备
            existDevice.setCompanyCode(device.getCompanyCode());
            existDevice.setProductType(device.getProductType());
            existDevice.setSerialNumber(device.getSerialNumber());
            existDevice.setDeviceName(device.getDeviceName());
            existDevice.setDeviceModel(device.getDeviceModel());
            existDevice.setUpdateTime(LocalDateTime.now());
            this.updateById(existDevice);
            return existDevice;
        } else {
            // 创建新设备
            device.setCreateTime(LocalDateTime.now());
            device.setUpdateTime(LocalDateTime.now());
            device.setStatus("active");
            this.save(device);
            return device;
        }
    }
}
