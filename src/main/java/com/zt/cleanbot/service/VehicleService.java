package com.zt.cleanbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.dto.VehicleIntegratedDTO;
import com.zt.cleanbot.model.Vehicle;
import java.util.List;

public interface VehicleService extends IService<Vehicle> {
    List<Vehicle> getAllVehicles();

    // 新增方法：根据序列号查找车辆
    Vehicle getBySerialNumber(String serialNumber);

    // 新增方法：检查序列号是否存在
    boolean existsBySerialNumber(String serialNumber);

    // 新增方法：获取整合后的车辆列表
    List<VehicleIntegratedDTO> getIntegratedVehicles();

    // 新增方法：根据省份获取整合后的车辆数据
    List<VehicleIntegratedDTO> getIntegratedVehiclesByProvince(String province);

    // 添加这个方法
    List<Vehicle> getBySerialNumbers(List<String> serialNumbers);

    // 根据产品编号查询车辆
    Vehicle findByProductId(String productId);

    // 创建或更新车辆
    Vehicle createOrUpdate(Vehicle vehicle);

    // 根据用户ID获取绑定的车辆列表
    List<Vehicle> getVehiclesByUserId(Integer userId);

    // 获取 D12 已绑定的 D01 列表
    List<String> getBoundD01Serials(String d12SerialNumber);

    // 更新 D12 的 D01 绑定列表
    boolean updateBoundD01Serials(String d12SerialNumber, List<String> d01Serials);

    // 根据 D01 序列号反查绑定它的 D12
    Vehicle findD12ByBoundD01Serial(String d01SerialNumber);

    /**
     * 校验用户是否有权操作该设备
     * 
     * @param userId   用户ID
     * @param deviceId 设备序列号 (serialNumber)
     * @return 是否有权访问
     */
    boolean hasDeviceAccess(Integer userId, String deviceId);

    /**
     * 带角色的设备权限校验：roleId <= 2（admin/超管）直接放行
     */
    boolean hasDeviceAccess(Integer userId, Integer roleId, String deviceId);
}
