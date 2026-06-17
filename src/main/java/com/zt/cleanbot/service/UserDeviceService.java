package com.zt.cleanbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.dto.DeviceInfoResponse;
import com.zt.cleanbot.dto.QRCodeScanRequest;
import com.zt.cleanbot.model.UserDevice;
import java.util.List;

public interface UserDeviceService extends IService<UserDevice> {
    /**
     * 扫描二维码绑定设备
     * @param userId 用户ID
     * @param request 二维码数据
     * @return 设备信息
     */
    DeviceInfoResponse scanAndBind(Integer userId, QRCodeScanRequest request);

    /**
     * 获取用户的所有设备
     * @param userId 用户ID
     * @return 设备列表
     */
    List<DeviceInfoResponse> getUserDevices(Integer userId);

    /**
     * 解绑设备
     * @param userId 用户ID
     * @param serialNumber 设备序列号（productType + productId，如 -D01250001）
     * @return 是否成功
     */
    Boolean unbindDevice(Integer userId, String serialNumber);
}
