package com.zt.cleanbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.UserDeviceMapper;
import com.zt.cleanbot.dto.DeviceInfoResponse;
import com.zt.cleanbot.dto.QRCodeScanRequest;
import com.zt.cleanbot.model.Vehicle;
import com.zt.cleanbot.model.User;
import com.zt.cleanbot.model.UserDevice;
import com.zt.cleanbot.service.VehicleService;
import com.zt.cleanbot.service.UserDeviceService;
import com.zt.cleanbot.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserDeviceServiceImpl extends ServiceImpl<UserDeviceMapper, UserDevice> implements UserDeviceService {

    @Autowired
    private UserService userService;

    @Autowired
    private VehicleService vehicleService;

    @Override
    @Transactional
    public DeviceInfoResponse scanAndBind(Integer userId, QRCodeScanRequest request) {
        // 1. 验证用户是否存在
        User user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 2. 验证二维码数据
        if (request.getCompanyCode() == null || request.getCompanyCode().trim().isEmpty()) {
            throw new RuntimeException("公司代号不能为空");
        }
        if (request.getProductType() == null || request.getProductType().trim().isEmpty()) {
            throw new RuntimeException("产品型号不能为空");
        }
        if (request.getProductId() == null || request.getProductId().trim().isEmpty()) {
            throw new RuntimeException("产品编号不能为空");
        }

        // 3. 查询或创建车辆/设备
        // 按 serialNumber（productType + productId）查找，这是唯一标识
        String serialNumber = request.getProductType() + request.getProductId();
        Vehicle vehicle = vehicleService.getBySerialNumber(serialNumber);

        if (vehicle == null) {
            // 设备不存在，创建新设备
            vehicle = new Vehicle();
        }

        // 补全/更新设备标识信息（无论新建还是已存在）
        vehicle.setCompanyCode(request.getCompanyCode());
        vehicle.setProductType(request.getProductType());
        vehicle.setProductId(request.getProductId());

        // serialNumber 格式：productType + productId（与MQTT/Redis中的deviceId一致）
        // 例如：-D01 + 250001 = -D01250001
        vehicle.setSerialNumber(serialNumber);

        // name 格式：产品型号名称-产品编号后3位
        // 例如：D01干挂基本型-001
        String productTypeName = getDeviceModelName(request.getProductType());
        String productIdDisplay = request.getProductId().substring(request.getProductId().length() - 3);
        vehicle.setName(productTypeName + "-" + productIdDisplay);

        // model 使用产品型号名称
        vehicle.setModel(productTypeName);

        // 根据 productType 自动判断 vehicleType
        vehicle.setVehicleType(getVehicleType(request.getProductType()));

        // 如果是新设备，设置初始状态
        if (vehicle.getId() == null) {
            vehicle.setStatus("active");
        }

        vehicle = vehicleService.createOrUpdate(vehicle);

        // 4. 检查当前用户是否已绑定该设备（允许多用户绑定同一设备）
        QueryWrapper<UserDevice> userDeviceQuery = new QueryWrapper<>();
        userDeviceQuery.eq("user_id", userId)
                .eq("vehicle_id", vehicle.getId())
                .eq("status", "active");
        UserDevice userDevice = this.getOne(userDeviceQuery);

        if (userDevice == null) {
            // 创建新的绑定记录
            userDevice = new UserDevice();
            userDevice.setUserId(userId);
            userDevice.setVehicleId(vehicle.getId());
            userDevice.setStatus("active");
            userDevice.setBindTime(LocalDateTime.now());
            this.save(userDevice);
        }

        // 6. 构建响应
        DeviceInfoResponse response = new DeviceInfoResponse();
        response.setDeviceId(vehicle.getId());
        response.setCompanyCode(vehicle.getCompanyCode());
        response.setProductType(vehicle.getProductType());
        response.setProductId(vehicle.getProductId());
        response.setSerialNumber(vehicle.getSerialNumber());
        response.setStatus(null); // 默认离线，实际状态由MQTT更新
        response.setBound(true);
        response.setBoundUsername(user.getUsername());
        response.setUserId(userId);

        return response;
    }

    @Override
    public List<DeviceInfoResponse> getUserDevices(Integer userId) {
        // 查询用户的所有绑定记录
        QueryWrapper<UserDevice> query = new QueryWrapper<>();
        query.eq("user_id", userId)
                .eq("status", "active")
                .orderByDesc("bind_time");

        List<UserDevice> bindings = this.list(query);

        return bindings.stream().map(binding -> {
            // 查询车辆/设备详细信息
            Vehicle vehicle = vehicleService.getById(binding.getVehicleId());
            if (vehicle == null) {
                return null;
            }

            DeviceInfoResponse response = new DeviceInfoResponse();
            response.setDeviceId(vehicle.getId());
            response.setCompanyCode(vehicle.getCompanyCode());
            response.setProductType(vehicle.getProductType());
            response.setProductId(vehicle.getProductId());
            response.setSerialNumber(vehicle.getSerialNumber());
            response.setStatus(null); // 默认离线，实际状态由MQTT更新
            response.setBound(true);
            response.setUserId(userId);
            return response;
        }).filter(r -> r != null).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Boolean unbindDevice(Integer userId, String serialNumber) {
        // 按 serialNumber（productType + productId）查找设备
        Vehicle vehicle = vehicleService.getBySerialNumber(serialNumber);
        if (vehicle == null) {
            throw new RuntimeException("设备不存在");
        }

        // 查询绑定记录
        QueryWrapper<UserDevice> query = new QueryWrapper<>();
        query.eq("user_id", userId)
                .eq("vehicle_id", vehicle.getId())
                .eq("status", "active");

        UserDevice binding = this.getOne(query);
        if (binding == null) {
            throw new RuntimeException("设备未绑定");
        }

        // 软删除：更新状态为deleted
        binding.setStatus("deleted");
        binding.setUnbindTime(LocalDateTime.now());
        return this.updateById(binding);
    }

    /**
     * 根据产品型号获取设备型号名称
     */
    private String getDeviceModelName(String productType) {
        switch (productType) {
            case "-D01":
                return "D01干挂基本型";
            case "-D11":
                return "D11干挂带扭";
            case "-D12":
                return "D12干挂接驳车";
            case "-D21":
                return "D21干挂带跨";
            case "-T01":
                return "T01履带基本型";
            case "-T11":
                return "T11履带无人值守";
            case "-T12":
                return "T12履带充电仓";
            case "-T21":
                return "T21履带操控看守";
            default:
                return "未知型号";
        }
    }

    /**
     * 根据产品型号获取车辆类型
     * -D 系列 = railcar（轨道车）
     * -T 系列 = tracklayer（履带车）
     */
    private String getVehicleType(String productType) {
        if (productType == null) {
            return null;
        }
        // 以 -D 开头的是轨道车
        if (productType.startsWith("-D")) {
            return "railcar";
        }
        // 以 -T 开头的是履带车
        if (productType.startsWith("-T")) {
            return "tracklayer";
        }
        return null;
    }
}
