package com.zt.cleanbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.Device;

public interface DeviceService extends IService<Device> {
    /**
     * 根据产品编号查询设备
     * @param productId 产品编号
     * @return 设备信息
     */
    Device findByProductId(String productId);

    /**
     * 创建或更新设备
     * @param device 设备信息
     * @return 设备
     */
    Device createOrUpdate(Device device);
}
