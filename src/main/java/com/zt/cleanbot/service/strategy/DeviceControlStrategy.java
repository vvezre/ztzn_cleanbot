package com.zt.cleanbot.service.strategy;

import com.zt.cleanbot.dto.DeviceConfigRequest;

/**
 * 设备控制策略接口
 * 定义不同设备型号的编码规范
 */
public interface DeviceControlStrategy {

    /**
     * 是否支持该设备型号
     * 
     * @param model 产品型号 (e.g. -D01, -D12)
     * @return true if supported
     */
    boolean supports(String model);

    /**
     * 将控制请求编码为十六进制字符串
     * 
     * @param request 设备配置请求
     * @return 十六进制字符串 (70字节 -> 140字符，或交互帧28字节 -> 56字符)
     */
    String encode(DeviceConfigRequest request);
}
