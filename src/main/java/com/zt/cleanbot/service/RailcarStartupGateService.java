package com.zt.cleanbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.RailcarStartupGate;

/**
 * 启动门禁服务接口
 */
public interface RailcarStartupGateService extends IService<RailcarStartupGate> {

    /**
     * 根据设备序列号查询门禁状态
     * @param serialNumber 设备序列号
     * @return 门禁记录，不存在时返回 null
     */
    RailcarStartupGate getBySerialNumber(String serialNumber);

    /**
     * 设置设备启动门禁状态
     * 同时更新 MySQL 并发布 MQTT retained 消息
     * @param serialNumber 设备序列号
     * @param disabled 是否禁用启动
     * @param username 操作者用户名
     * @return 操作是否成功
     */
    boolean setDisabled(String serialNumber, boolean disabled, String username);
}
