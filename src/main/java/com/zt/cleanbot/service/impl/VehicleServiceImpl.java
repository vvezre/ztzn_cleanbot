package com.zt.cleanbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dao.UserDeviceMapper;
import com.zt.cleanbot.dao.VehicleMapper;
import com.zt.cleanbot.dto.VehicleIntegratedDTO;
import com.zt.cleanbot.dto.VehicleRedisData;
import com.zt.cleanbot.model.UserDevice;
import com.zt.cleanbot.model.Vehicle;
import com.zt.cleanbot.service.LocalGeoService;
import com.zt.cleanbot.service.VehicleService;
import com.zt.cleanbot.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class VehicleServiceImpl extends ServiceImpl<VehicleMapper, Vehicle> implements VehicleService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleServiceImpl.class);

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocalGeoService localGeoService;

    @Autowired
    private UserDeviceMapper userDeviceMapper;

    @Override
    public List<Vehicle> getAllVehicles() {
        return this.list();
    }

    @Override
    public Vehicle getBySerialNumber(String serialNumber) {
        QueryWrapper<Vehicle> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("serial_number", serialNumber);
        return this.getOne(queryWrapper);
    }

    @Override
    public boolean existsBySerialNumber(String serialNumber) {
        QueryWrapper<Vehicle> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("serial_number", serialNumber);
        return this.count(queryWrapper) > 0;
    }

    @Override
    public List<VehicleIntegratedDTO> getIntegratedVehicles() {
        return getIntegratedVehiclesInternal(null);
    }

    // @Override
    // public List<VehicleIntegratedDTO> getIntegratedVehicles() {
    // List<VehicleIntegratedDTO> result = new ArrayList<>();
    //
    // try {
    // // 1. 从Redis获取所有车辆key
    // Set<String> keys = redisUtil.getVehicleKeys();
    // if (keys == null || keys.isEmpty()) {
    // logger.warn("Redis中没有找到车辆数据");
    // return result;
    // }
    //
    // logger.info("从Redis找到 " + keys.size() + " 个车辆key");
    //
    // // 2. 遍历每个key，获取Redis数据并整合MySQL数据
    // for (String key : keys) {
    // try {
    // // 获取Redis中的数据
    // Object redisData = redisUtil.getVehicle(key);
    // if (redisData == null) {
    // logger.debug("Redis中key " + key + " 的值为空");
    // continue;
    // }
    //
    // VehicleRedisData vehicleRedisData = parseRedisData(redisData);
    // if (vehicleRedisData == null) {
    // logger.warn("无法解析Redis数据，key: " + key);
    // continue;
    // }
    //
    // // 检查deviceId是否为空
    // if (vehicleRedisData.getDeviceId() == null) {
    // logger.warn("Redis数据中deviceId为空，key: " + key);
    // continue;
    // }
    //
    // // 重要调整：使用deviceId作为serialNumber查询MySQL
    // String deviceId = vehicleRedisData.getDeviceId();
    // Vehicle dbVehicle = this.getBySerialNumber(deviceId);
    // if (dbVehicle == null) {
    // logger.warn("MySQL中未找到序列号为 " + deviceId + " 的车辆");
    // // 可以选择创建部分数据或者跳过
    // continue;
    // }
    //
    // // 整合数据
    // VehicleIntegratedDTO integratedDTO = buildIntegratedDTO(vehicleRedisData,
    // dbVehicle);
    // result.add(integratedDTO);
    //
    // } catch (Exception e) {
    // logger.error("处理车辆key " + key + " 时发生错误", e);
    // }
    // }
    //
    // logger.info("成功整合 " + result.size() + " 个车辆数据");
    //
    // } catch (Exception e) {
    // logger.error("获取整合车辆数据时发生错误", e);
    // }
    //
    // return result;
    // }

    /**
     * 解析Redis数据
     */
    private VehicleRedisData parseRedisData(Object redisData) {
        try {
            if (redisData instanceof String) {
                // 如果是JSON字符串，反序列化
                return objectMapper.readValue((String) redisData, VehicleRedisData.class);
            } else if (redisData instanceof VehicleRedisData) {
                // 如果已经是对象，直接返回
                return (VehicleRedisData) redisData;
            } else {
                // 使用ObjectMapper转换任意对象到VehicleRedisData
                return objectMapper.convertValue(redisData, VehicleRedisData.class);
            }
        } catch (Exception e) {
            log.error("解析Redis数据失败", e);
            return null;
        }
    }

    /**
     * 构建整合DTO
     */
    private VehicleIntegratedDTO buildIntegratedDTO(VehicleRedisData redisData, Vehicle dbVehicle) {
        VehicleIntegratedDTO dto = new VehicleIntegratedDTO();

        // 设置Redis数据
        dto.setDeviceId(redisData.getDeviceId());
        dto.setBattery(redisData.getBattery());
        dto.setStatus(redisData.getStatus());
        dto.setLastUpdateTime(redisData.getLastUpdateTime());

        // 设置位置信息
        if (redisData.getLocation() != null) {
            dto.setLon(redisData.getLocation().getLon());
            dto.setLat(redisData.getLocation().getLat());
        }

        // 设置MySQL数据 - 重要调整：确保serialNumber正确映射
        dto.setName(dbVehicle.getName());
        // 注意：这里serialNumber应该与deviceId相同，因为是通过deviceId查询到的
        dto.setSerialNumber(dbVehicle.getSerialNumber());
        dto.setBrand(dbVehicle.getBrand());
        dto.setModel(dbVehicle.getModel());
        dto.setVehicleType(dbVehicle.getVehicleType());
        dto.setBatteryCapacity(dbVehicle.getBatteryCapacity());
        dto.setWeight(dbVehicle.getWeight());
        dto.setCleaningWidth(dbVehicle.getCleaningWidth());

        return dto;
    }

    @Override
    public List<VehicleIntegratedDTO> getIntegratedVehiclesByProvince(String province) {
        if (!localGeoService.isValidProvince(province)) {
            logger.warn("无效的省份名称: {}", province);
            return new ArrayList<>();
        }
        return getIntegratedVehiclesInternal(province);
    }

    // 在VehicleServiceImpl中确保实现这个方法
    @Override
    public List<Vehicle> getBySerialNumbers(List<String> serialNumbers) {
        if (serialNumbers == null || serialNumbers.isEmpty()) {
            return new ArrayList<>();
        }

        QueryWrapper<Vehicle> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("serial_number", serialNumbers);
        return this.list(queryWrapper);
    }

    @Override
    public Vehicle findByProductId(String productId) {
        QueryWrapper<Vehicle> query = new QueryWrapper<>();
        query.eq("product_id", productId);
        return this.getOne(query);
    }

    @Override
    public Vehicle createOrUpdate(Vehicle vehicle) {
        if (vehicle.getBoundD01Serials() != null) {
            String normalizedBoundSerials = normalizeBoundD01Serials(vehicle.getBoundD01Serials());
            validateExclusiveD01Bindings(vehicle.getSerialNumber(), normalizedBoundSerials);
            vehicle.setBoundD01Serials(normalizedBoundSerials);
        }

        // 按 serialNumber（productType + productId）查找，这是真正的唯一标识
        Vehicle existVehicle = this.getBySerialNumber(vehicle.getSerialNumber());

        if (existVehicle != null) {
            // 更新现有车辆 - 补全所有标识字段
            existVehicle.setCompanyCode(vehicle.getCompanyCode());
            existVehicle.setProductType(vehicle.getProductType());
            existVehicle.setSerialNumber(vehicle.getSerialNumber());
            existVehicle.setName(vehicle.getName());
            existVehicle.setModel(vehicle.getModel());
            if (vehicle.getVehicleType() != null) {
                existVehicle.setVehicleType(vehicle.getVehicleType());
            }
            if (vehicle.getBoundD01Serials() != null) {
                existVehicle.setBoundD01Serials(vehicle.getBoundD01Serials());
            }
            existVehicle.setUpdateTime(java.time.LocalDateTime.now());
            this.updateById(existVehicle);
            return existVehicle;
        } else {
            // 创建新车辆
            vehicle.setCreateTime(java.time.LocalDateTime.now());
            vehicle.setUpdateTime(java.time.LocalDateTime.now());
            if (vehicle.getStatus() == null) {
                vehicle.setStatus("active");
            }
            this.save(vehicle);
            return vehicle;
        }
    }

    /**
     * 内部方法：获取整合后的车辆数据
     * 
     * @param province 省份名称，如果为null则返回所有车辆
     */
    private List<VehicleIntegratedDTO> getIntegratedVehiclesInternal(String province) {
        List<VehicleIntegratedDTO> result = new ArrayList<>();

        try {
            // 1. 从Redis获取所有车辆key (使用getDeviceKeysOnly过滤掉token等非设备key)
            Set<String> keys = redisUtil.getDeviceKeysOnly();
            if (keys == null || keys.isEmpty()) {
                logger.warn("Redis中没有找到车辆数据");
                return result;
            }

            logger.info("从Redis找到 {} 个车辆key", keys.size());

            // 收集所有位置信息用于批量查询
            Map<String, Double[]> locationMap = new HashMap<>();
            Map<String, VehicleRedisData> redisDataMap = new HashMap<>();
            Set<String> deviceIds = new LinkedHashSet<>();

            // 2. 首先收集所有Redis数据
            for (String key : keys) {
                try {
                    Object redisData = redisUtil.getVehicle(key);
                    if (redisData == null) {
                        logger.debug("Redis中key {} 的值为空", key);
                        continue;
                    }

                    VehicleRedisData vehicleRedisData = parseRedisData(redisData);
                    if (vehicleRedisData == null || vehicleRedisData.getDeviceId() == null) {
                        logger.warn("无法解析Redis数据，key: {}", key);
                        continue;
                    }

                    // 收集位置信息
                    if (vehicleRedisData.getLocation() != null &&
                            vehicleRedisData.getLocation().getLon() != null &&
                            vehicleRedisData.getLocation().getLat() != null) {

                        Double[] location = new Double[] {
                                vehicleRedisData.getLocation().getLon(),
                                vehicleRedisData.getLocation().getLat()
                        };
                        locationMap.put(vehicleRedisData.getDeviceId(), location);
                    }

                    redisDataMap.put(vehicleRedisData.getDeviceId(), vehicleRedisData);
                    deviceIds.add(vehicleRedisData.getDeviceId());

                } catch (Exception e) {
                    logger.error("处理车辆key {} 时发生错误", key, e);
                }
            }

            // 3. 批量获取省份信息
            Map<String, String> provinceMap = localGeoService.getProvincesByLocations(locationMap);

            // 4. 批量查询MySQL数据
            List<Vehicle> dbVehicles = this.getBySerialNumbers(new ArrayList<>(deviceIds));
            Map<String, Vehicle> dbVehicleMap = dbVehicles.stream()
                    .collect(Collectors.toMap(Vehicle::getSerialNumber, vehicle -> vehicle,
                            (existing, replacement) -> existing)); // 处理重复，保留第一个

            // 5. 整合数据
            for (String deviceId : deviceIds) {
                VehicleRedisData redisData = redisDataMap.get(deviceId);
                Vehicle dbVehicle = dbVehicleMap.get(deviceId);

                if (redisData != null && dbVehicle != null) {
                    VehicleIntegratedDTO integratedDTO = buildIntegratedDTO(redisData, dbVehicle);

                    // 设置省份信息
                    String vehicleProvince = provinceMap.getOrDefault(deviceId, "未知");
                    integratedDTO.setProvince(vehicleProvince);

                    // 如果指定了省份，只返回该省份的车辆
                    if (province == null || province.equals(vehicleProvince)) {
                        result.add(integratedDTO);
                    }
                } else if (dbVehicle == null) {
                    logger.warn("MySQL中未找到序列号为 {} 的车辆", deviceId);
                }
            }

            logger.info("成功整合 {} 个车辆数据", result.size());

        } catch (Exception e) {
            logger.error("获取整合车辆数据时发生错误", e);
        }

        return result;
    }

    @Override
    public List<Vehicle> getVehiclesByUserId(Integer userId) {
        if (userId == null) {
            logger.warn("用户ID为空，返回空列表");
            return new ArrayList<>();
        }

        try {
            // 1. 查询用户绑定的设备ID列表
            QueryWrapper<UserDevice> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId)
                    .eq("status", "active");
            List<UserDevice> userDevices = userDeviceMapper.selectList(queryWrapper);

            if (userDevices == null || userDevices.isEmpty()) {
                logger.info("用户 {} 没有绑定的设备", userId);
                return new ArrayList<>();
            }

            // 2. 提取设备ID列表
            List<Integer> vehicleIds = userDevices.stream()
                    .map(UserDevice::getVehicleId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (vehicleIds.isEmpty()) {
                return new ArrayList<>();
            }

            // 3. 批量查询设备
            List<Vehicle> vehicles = this.listByIds(vehicleIds);
            logger.info("用户 {} 获取到 {} 个绑定设备", userId, vehicles.size());

            return vehicles;

        } catch (Exception e) {
            logger.error("获取用户绑定设备失败, userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> getBoundD01Serials(String d12SerialNumber) {
        if (d12SerialNumber == null || d12SerialNumber.trim().isEmpty()) {
            return new ArrayList<>();
        }
        Vehicle d12 = getBySerialNumber(d12SerialNumber.trim());
        if (d12 == null) {
            return new ArrayList<>();
        }
        return parseBoundD01Serials(d12.getBoundD01Serials());
    }

    @Override
    public boolean updateBoundD01Serials(String d12SerialNumber, List<String> d01Serials) {
        if (d12SerialNumber == null || d12SerialNumber.trim().isEmpty()) {
            return false;
        }
        Vehicle d12 = getBySerialNumber(d12SerialNumber.trim());
        if (d12 == null) {
            return false;
        }
        String normalizedBoundSerials = joinBoundD01Serials(d01Serials);
        try {
            validateExclusiveD01Bindings(d12.getSerialNumber(), normalizedBoundSerials);
        } catch (IllegalArgumentException e) {
            logger.error("更新 D12 绑定失败 - d12SerialNumber={}, reason={}", d12SerialNumber, e.getMessage());
            return false;
        }

        // updateById 在字段值为 null 时会受全局更新策略影响而跳过字段，
        // 解绑到空列表时必须显式写入 bound_d01_serials = NULL。
        UpdateWrapper<Vehicle> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", d12.getId());
        updateWrapper.set("bound_d01_serials", normalizedBoundSerials);
        updateWrapper.set("update_time", java.time.LocalDateTime.now());
        return this.update(null, updateWrapper);
    }

    @Override
    public Vehicle findD12ByBoundD01Serial(String d01SerialNumber) {
        if (d01SerialNumber == null || d01SerialNumber.trim().isEmpty()) {
            return null;
        }
        String keyword = d01SerialNumber.trim();
        QueryWrapper<Vehicle> query = new QueryWrapper<>();
        query.eq("product_type", "-D12");
        query.isNotNull("bound_d01_serials");
        query.ne("bound_d01_serials", "");
        List<Vehicle> d12List = this.list(query);
        Vehicle matched = null;
        for (Vehicle d12 : d12List) {
            List<String> boundList = parseBoundD01Serials(d12.getBoundD01Serials());
            if (boundList.contains(keyword)) {
                if (matched != null && !Objects.equals(matched.getSerialNumber(), d12.getSerialNumber())) {
                    logger.error("检测到重复绑定，D01 {} 同时绑定多个 D12: {}, {}",
                            keyword, matched.getSerialNumber(), d12.getSerialNumber());
                    return null;
                }
                matched = d12;
            }
        }
        return matched;
    }

    private List<String> parseBoundD01Serials(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        String[] parts = raw.split(",");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String joinBoundD01Serials(List<String> d01Serials) {
        if (d01Serials == null || d01Serials.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        for (String serial : d01Serials) {
            if (serial == null) {
                continue;
            }
            String trimmed = serial.trim();
            if (!trimmed.isEmpty() && !normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
            if (normalized.size() >= 5) {
                break;
            }
        }
        if (normalized.isEmpty()) {
            return null;
        }
        return String.join(",", normalized);
    }

    private String normalizeBoundD01Serials(String raw) {
        return joinBoundD01Serials(parseBoundD01Serials(raw));
    }

    private void validateExclusiveD01Bindings(String currentD12SerialNumber, String rawBoundD01Serials) {
        List<String> d01Serials = parseBoundD01Serials(rawBoundD01Serials);
        if (d01Serials.isEmpty()) {
            return;
        }

        QueryWrapper<Vehicle> query = new QueryWrapper<>();
        query.eq("product_type", "-D12");
        query.isNotNull("bound_d01_serials");
        query.ne("bound_d01_serials", "");
        List<Vehicle> d12List = this.list(query);

        for (String d01Serial : d01Serials) {
            for (Vehicle d12 : d12List) {
                if (d12 == null || d12.getSerialNumber() == null) {
                    continue;
                }
                String targetD12Serial = d12.getSerialNumber().trim();
                if (targetD12Serial.equals(currentD12SerialNumber)) {
                    continue;
                }
                List<String> boundList = parseBoundD01Serials(d12.getBoundD01Serials());
                if (boundList.contains(d01Serial)) {
                    throw new IllegalArgumentException(
                            String.format("D01 %s 已绑定到其他 D12 %s", d01Serial, targetD12Serial));
                }
            }
        }
    }

    @Override
    public boolean hasDeviceAccess(Integer userId, String deviceId) {
        return hasDeviceAccess(userId, null, deviceId);
    }

    public boolean hasDeviceAccess(Integer userId, Integer roleId, String deviceId) {
        if (deviceId == null)
            return false;

        // admin / 超级管理员（roleId <= 2）直接放行
        if (roleId != null && roleId <= 2) {
            return true;
        }

        if (userId == null)
            return false;

        // 查询该用户绑定的所有设备
        List<Vehicle> userVehicles = this.getVehiclesByUserId(userId);

        // 1. 直属设备校验
        if (userVehicles.stream().anyMatch(v -> deviceId.equals(v.getSerialNumber()))) {
            return true;
        }

        // 2. 检查是否为用户持有 D12 的从属绑定 D01
        //    D01 由 ensureVehicleExists 自动创建，不写入 user_device 表，
        //    但其访问权限由其主控 D12 的归属决定
        return userVehicles.stream()
                .filter(v -> v.getSerialNumber() != null && v.getSerialNumber().contains("-D12"))
                .anyMatch(d12 -> parseBoundD01Serials(d12.getBoundD01Serials()).contains(deviceId));
    }

}
