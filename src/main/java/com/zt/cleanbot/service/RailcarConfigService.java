package com.zt.cleanbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.RailcarConfig;

/**
 * 轨道车配置服务接口
 */
public interface RailcarConfigService extends IService<RailcarConfig> {

    /**
     * 保存或更新轨道车配置
     */
    boolean saveOrUpdateConfig(com.zt.cleanbot.model.RailcarControlMessage controlMessage);

    /**
     * 根据产品型号和编号查询配置
     */
    RailcarConfig getConfig(String productModel, String productNumber);

    /**
     * 保存或更新轨道车配置 (重载方法，接收 DeviceConfigRequest)
     */
    boolean saveOrUpdateConfig(com.zt.cleanbot.dto.DeviceConfigRequest request);

    /**
     * 根据完整设备ID查询配置（自动拆分 productModel / productNumber）
     * deviceId 格式：前4字节为型号，其余为编号，例如 -D12250001
     */
    RailcarConfig getConfigByDeviceId(String deviceId);

    /**
     * 缓存最新配置快照和设置帧
     */
    void cacheLatestConfig(com.zt.cleanbot.dto.DeviceConfigRequest request, String settingFrameHex);

    /**
     * 获取最新设置帧的 31-70 字节尾部（80位HEX）
     */
    String getLatestSettingFrameTailByDeviceId(String deviceId);
}
