package com.zt.cleanbot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.common.Result;
import com.zt.cleanbot.dto.D12BindingRequest;
import com.zt.cleanbot.dto.DeviceShadowStatus;
import com.zt.cleanbot.dto.MiniAppRobotResponse;
import com.zt.cleanbot.dto.VehicleRedisData;
import com.zt.cleanbot.model.Vehicle;
import com.zt.cleanbot.model.RailcarConfig;
import com.zt.cleanbot.service.DeviceShadowService;
import com.zt.cleanbot.service.RailcarConfigService;
import com.zt.cleanbot.service.VehicleService;
import com.zt.cleanbot.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 小程序/App 设备管理 API
 *
 * 数据来源：
 * 1. 静态信息：从 vehicle 表获取
 * 2. 实时状态：从 Redis 获取（由 MQTT 消息更新）
 */
@CrossOrigin(origins = { "*" }, maxAge = 3600L)
@RestController
@RequestMapping("/api/mini-app")
@Slf4j
public class MiniAppDeviceController {

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private RailcarConfigService railcarConfigService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceShadowService deviceShadowService;

    /**
     * 获取用户设备列表（静态信息 + 实时状态）
     *
     * GET /api/mini-app/devices
     *
     * @param request HTTP 请求（用于获取用户信息）
     * @return 设备列表
     */
    @GetMapping("/devices")
    public Result<List<MiniAppRobotResponse>> getDeviceList(HttpServletRequest request) {
        try {
            // 从请求中获取当前登录用户ID
            Integer userId = (Integer) request.getAttribute("userId");
            Integer roleId = (Integer) request.getAttribute("roleId");
            String username = (String) request.getAttribute("username");

            log.info("小程序获取设备列表 - 用户: {}, 角色ID: {}", username, roleId);

            List<Vehicle> vehicles;

            // 管理员返回所有设备
            if (roleId != null && roleId <= 2) {
                log.info("管理员用户 {} 获取所有设备", username);
                vehicles = vehicleService.getAllVehicles();
            }
            // 普通用户返回绑定的设备
            else if (userId != null) {
                log.info("普通用户 {} 获取绑定的设备", username);
                vehicles = vehicleService.getVehiclesByUserId(userId);
            }
            // 未认证用户返回空列表
            else {
                log.warn("未认证用户尝试获取设备列表");
                return Result.error(401, "未登录");
            }

            // 转换为小程序响应格式（整合静态信息和实时状态）
            // 过滤掉关键字段缺失的“幽灵设备”
            List<MiniAppRobotResponse> response = vehicles.stream()
                    .filter(v -> v.getCompanyCode() != null && !v.getCompanyCode().isEmpty())
                    .filter(v -> v.getProductType() != null && !v.getProductType().isEmpty())
                    .filter(v -> v.getProductId() != null && !v.getProductId().isEmpty())
                    .map(this::convertToMiniAppRobot)
                    .collect(Collectors.toList());

            log.info("返回 {} 个设备", response.size());
            return Result.success(response);

        } catch (Exception e) {
            log.error("获取设备列表失败", e);
            return Result.error(500, "获取设备列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个设备详情（静态信息 + 实时状态）
     *
     * GET /api/mini-app/devices/{deviceId}
     *
     * @param deviceId 设备 ID（数据库主键）
     * @param request  HTTP 请求
     * @return 设备详情
     */
    @GetMapping("/devices/{deviceId}")
    public Result<MiniAppRobotResponse> getDeviceDetail(
            @PathVariable Integer deviceId,
            HttpServletRequest request) {
        try {
            Integer userId = (Integer) request.getAttribute("userId");

            log.info("小程序获取设备详情 - 设备ID: {}, 用户ID: {}", deviceId, userId);

            // 查询设备
            Vehicle vehicle = vehicleService.getById(deviceId);
            if (vehicle == null) {
                return Result.error(404, "设备不存在");
            }

            // 转换为小程序响应格式
            MiniAppRobotResponse response = convertToMiniAppRobot(vehicle);

            return Result.success(response);

        } catch (Exception e) {
            log.error("获取设备详情失败 - 设备ID: {}", deviceId, e);
            return Result.error(500, "获取设备详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个设备的标准状态快照（供小程序渐进切换使用）
     *
     * GET /api/mini-app/devices/{deviceId}/shadow
     */
    @GetMapping("/devices/{deviceId}/shadow")
    public Result<DeviceShadowStatus> getDeviceShadow(
            @PathVariable Integer deviceId,
            HttpServletRequest request) {
        try {
            Integer userId = (Integer) request.getAttribute("userId");
            Integer roleId = (Integer) request.getAttribute("roleId");

            Vehicle vehicle = vehicleService.getById(deviceId);
            if (vehicle == null) {
                return Result.error(404, "设备不存在");
            }
            if (!hasDeviceAccess(roleId, userId, vehicle.getSerialNumber())) {
                return Result.error(403, "无权限访问该设备");
            }

            return Result.success(deviceShadowService.getDeviceShadow(vehicle.getSerialNumber()));
        } catch (Exception e) {
            log.error("获取设备标准状态失败 - 设备ID: {}", deviceId, e);
            return Result.error(500, "获取设备标准状态失败: " + e.getMessage());
        }
    }

    /**
     * 查询 D12 当前绑定的 D01 列表
     *
     * GET /api/mini-app/d12/{d12SerialNumber}/bindings
     */
    @GetMapping("/d12/{d12SerialNumber}/bindings")
    public Result<List<String>> getD12Bindings(
            @PathVariable String d12SerialNumber,
            HttpServletRequest request) {
        try {
            Integer userId = (Integer) request.getAttribute("userId");
            Integer roleId = (Integer) request.getAttribute("roleId");
            String normalizedD12Serial = normalizeSerialNumber(d12SerialNumber);

            if (normalizedD12Serial.isEmpty()) {
                return Result.error(400, "D12设备序列号不能为空");
            }

            Vehicle d12 = vehicleService.getBySerialNumber(normalizedD12Serial);
            if (d12 == null) {
                return Result.error(404, "D12设备不存在");
            }
            if (!"-D12".equals(d12.getProductType())) {
                return Result.error(400, "仅支持D12设备绑定");
            }
            if (!hasDeviceAccess(roleId, userId, normalizedD12Serial)) {
                return Result.error(403, "无权限访问该设备");
            }

            return Result.success(vehicleService.getBoundD01Serials(normalizedD12Serial));
        } catch (Exception e) {
            log.error("查询 D12 绑定列表失败 - d12SerialNumber: {}", d12SerialNumber, e);
            return Result.error(500, "查询绑定列表失败: " + e.getMessage());
        }
    }

    /**
     * D12 新增绑定 D01
     *
     * POST /api/mini-app/d12/{d12SerialNumber}/bindings
     */
    @PostMapping("/d12/{d12SerialNumber}/bindings")
    public Result<List<String>> bindD01ToD12(
            @PathVariable String d12SerialNumber,
            @RequestBody D12BindingRequest bindingRequest,
            HttpServletRequest request) {
        try {
            Integer userId = (Integer) request.getAttribute("userId");
            Integer roleId = (Integer) request.getAttribute("roleId");

            String normalizedD12Serial = normalizeSerialNumber(d12SerialNumber);
            String normalizedD01Serial = normalizeSerialNumber(
                    bindingRequest != null ? bindingRequest.getD01SerialNumber() : null);

            if (normalizedD12Serial.isEmpty()) {
                return Result.error(400, "D12设备序列号不能为空");
            }
            if (normalizedD01Serial.isEmpty()) {
                return Result.error(400, "D01设备序列号不能为空");
            }

            Vehicle d12 = vehicleService.getBySerialNumber(normalizedD12Serial);
            if (d12 == null || !"-D12".equals(d12.getProductType())) {
                return Result.error(400, "D12设备不存在或型号不正确");
            }

            Vehicle d01 = vehicleService.getBySerialNumber(normalizedD01Serial);
            if (d01 == null || !"-D01".equals(d01.getProductType())) {
                return Result.error(400, "D01设备不存在或型号不正确");
            }

            if (!hasDeviceAccess(roleId, userId, normalizedD12Serial)
                    || !hasDeviceAccess(roleId, userId, normalizedD01Serial)) {
                return Result.error(403, "仅可绑定当前账号已拥有的设备");
            }

            List<String> currentBindings = vehicleService.getBoundD01Serials(normalizedD12Serial);
            if (currentBindings.stream().anyMatch(item -> serialEquals(item, normalizedD01Serial))) {
                return Result.success(currentBindings);
            }
            if (currentBindings.size() >= 5) {
                return Result.error(400, "最多仅支持绑定5台D01设备");
            }

            currentBindings.add(normalizedD01Serial);
            boolean updated = vehicleService.updateBoundD01Serials(normalizedD12Serial, currentBindings);
            if (!updated) {
                return Result.error(500, "绑定失败，请稍后重试");
            }

            return Result.success(vehicleService.getBoundD01Serials(normalizedD12Serial));
        } catch (Exception e) {
            log.error("D12 绑定 D01 失败 - d12SerialNumber: {}", d12SerialNumber, e);
            return Result.error(500, "绑定失败: " + e.getMessage());
        }
    }

    /**
     * D12 解绑 D01
     *
     * DELETE /api/mini-app/d12/{d12SerialNumber}/bindings/{d01SerialNumber}
     */
    @DeleteMapping("/d12/{d12SerialNumber}/bindings/{d01SerialNumber}")
    public Result<List<String>> unbindD01FromD12(
            @PathVariable String d12SerialNumber,
            @PathVariable String d01SerialNumber,
            HttpServletRequest request) {
        try {
            Integer userId = (Integer) request.getAttribute("userId");
            Integer roleId = (Integer) request.getAttribute("roleId");
            String normalizedD12Serial = normalizeSerialNumber(d12SerialNumber);
            String normalizedD01Serial = normalizeSerialNumber(d01SerialNumber);

            if (normalizedD12Serial.isEmpty()) {
                return Result.error(400, "D12设备序列号不能为空");
            }
            if (normalizedD01Serial.isEmpty()) {
                return Result.error(400, "D01设备序列号不能为空");
            }

            Vehicle d12 = vehicleService.getBySerialNumber(normalizedD12Serial);
            if (d12 == null || !"-D12".equals(d12.getProductType())) {
                return Result.error(400, "D12设备不存在或型号不正确");
            }
            if (!hasDeviceAccess(roleId, userId, normalizedD12Serial)) {
                return Result.error(403, "无权限操作该设备");
            }

            List<String> currentBindings = vehicleService.getBoundD01Serials(normalizedD12Serial);
            List<String> beforeBindings = new ArrayList<>(currentBindings);
            int beforeSize = currentBindings.size();
            currentBindings.removeIf(item -> serialEquals(item, normalizedD01Serial));
            boolean removed = currentBindings.size() < beforeSize;

            if (!removed) {
                log.warn("D12解绑未命中 - d12Serial: {}, d01Serial: {}, currentBindings: {}",
                        normalizedD12Serial, normalizedD01Serial, beforeBindings);
            }

            boolean updated = vehicleService.updateBoundD01Serials(normalizedD12Serial, currentBindings);
            if (!updated) {
                return Result.error(500, "解绑失败，请稍后重试");
            }

            List<String> latestBindings = vehicleService.getBoundD01Serials(normalizedD12Serial);
            log.info("D12解绑处理完成 - d12Serial: {}, d01Serial: {}, removed: {}, before: {}, after: {}",
                    normalizedD12Serial, normalizedD01Serial, removed, beforeBindings, latestBindings);
            return Result.success(latestBindings);
        } catch (Exception e) {
            log.error("D12 解绑 D01 失败 - d12SerialNumber: {}, d01SerialNumber: {}", d12SerialNumber, d01SerialNumber, e);
            return Result.error(500, "解绑失败: " + e.getMessage());
        }
    }

    /**
     * 将 Vehicle 实体转换为小程序 Robot 响应 DTO
     * 整合静态信息（Vehicle表）和实时状态（Redis）
     */
    private MiniAppRobotResponse convertToMiniAppRobot(Vehicle vehicle) {
        MiniAppRobotResponse response = new MiniAppRobotResponse();
        DeviceShadowStatus shadow = deviceShadowService.getDeviceShadow(vehicle.getSerialNumber());

        // ========== 静态信息（来自 Vehicle 表） ==========

        // 基本信息
        response.setId(vehicle.getId());
        response.setSerialNumber(vehicle.getSerialNumber());
        response.setName(vehicle.getName());

        // 产品信息
        response.setCompanyCode(vehicle.getCompanyCode());
        response.setProductType(vehicle.getProductType());
        response.setProductModel(vehicle.getProductType());
        response.setProductId(vehicle.getProductId());

        // 构建设备 ID（MQTT Topic 使用）
        if (vehicle.getProductType() != null && vehicle.getProductId() != null) {
            String mqttDeviceId = vehicle.getProductType() + vehicle.getProductId();
            response.setDeviceId(mqttDeviceId);
        }

        // 车辆类型
        response.setVehicleType(vehicle.getVehicleType());
        response.setDeviceType(shadow.getDeviceType());
        response.setOnlineState(shadow.getOnlineState());
        response.setMissionState(shadow.getMissionState());
        response.setControlState(shadow.getControlState());
        response.setHealthState(shadow.getHealthState());
        response.setFaultState(shadow.getFaultState());
        response.setLastCommandId(shadow.getLastCommandId());
        response.setLastCommandStatus(shadow.getLastCommandStatus());
        response.setUpdatedAt(shadow.getUpdatedAt());
        response.setSupportedActions(shadow.getSupportedActions());
        response.setSupportedParams(shadow.getSupportedParams());
        response.setSupportedStatusFields(shadow.getSupportedStatusFields());
        response.setShadowDetail(shadow.getDetail());

        if ("-D12".equals(vehicle.getProductType())) {
            List<String> boundList = parseBoundD01Serials(vehicle.getBoundD01Serials());
            response.setBoundDeviceIds(boundList);
            response.setBoundDeviceCount(boundList.size());
        } else {
            response.setBoundDeviceIds(new ArrayList<String>());
            response.setBoundDeviceCount(0);
        }

        // ========== 实时状态（从 Redis 获取） ==========

        try {
            // 从 Redis 获取实时数据
            Object redisDataObj = redisUtil.getVehicle(vehicle.getSerialNumber());

            if (redisDataObj != null) {
                // 解析 Redis 数据
                VehicleRedisData redisData;
                if (redisDataObj instanceof VehicleRedisData) {
                    redisData = (VehicleRedisData) redisDataObj;
                } else {
                    redisData = objectMapper.convertValue(redisDataObj, VehicleRedisData.class);
                }

                // 设置实时状态
                String frontendStatus = "OFFLINE".equalsIgnoreCase(shadow.getOnlineState())
                        ? "offline"
                        : mapStatus(redisData.getStatus());
                response.setStatus(frontendStatus);
                response.setOnline(!"offline".equals(frontendStatus));
                response.setBattery(redisData.getBattery());
                response.setLastOnlineTime(vehicle.getLastOnlineTime());

                // 工作模式参数
                response.setRunControl(redisData.getRunControl());
                response.setRunEnable(redisData.getRunEnable());
                response.setWorkMode(redisData.getWorkMode());

                // 速度参数
                response.setWalkSpeed(redisData.getWalkSpeed());
                response.setBrushSpeed(redisData.getBrushSpeed());
                response.setBridgeSpeed(redisData.getBridgeSpeed());

                // 运行数据
                response.setRunTimeSingle(redisData.getRunTimeSingle());
                response.setRunTimeTotal(redisData.getRunTimeTotal());
                response.setMileageSingle(redisData.getMileageSingle());
                response.setMileageTotal(redisData.getMileageTotal());
                response.setHeartbeat(redisData.getHeartbeat());

                // T 型状态上报字段
                response.setVoltage(redisData.getVoltage());
                response.setAngle(redisData.getAngle());
                response.setSpeed(redisData.getSpeed() != null ? redisData.getSpeed() : redisData.getWalkSpeed());
                response.setLat(redisData.getLat());
                response.setLon(redisData.getLon());
                response.setHeading(redisData.getHeading());
                response.setAction(redisData.getAction());
                response.setTaskName(redisData.getTaskName());
                response.setCurTaskIndex(redisData.getCurTaskIndex());
                response.setTaskCount(redisData.getTaskCount());
                response.setTimestamp(redisData.getTimestamp() != null ? redisData.getTimestamp() : redisData.getLastUpdateTime());
                response.setTaskOrigin(toMiniAppLocation(redisData.getTaskOrigin()));
                response.setCurrentLocation(toMiniAppCurrentLocation(redisData.getCurrentLocation()));
                response.setDistanceToTaskOriginM(redisData.getDistanceToTaskOriginM());
                response.setTaskOriginToleranceM(redisData.getTaskOriginToleranceM());
                response.setIsAtTaskOrigin(redisData.getIsAtTaskOrigin());
                response.setTracking(redisData.getTracking());
                response.setPathPlanning(redisData.getPathPlanning());
                response.setLeftEdge(redisData.getLeftEdge());
                response.setRightEdge(redisData.getRightEdge());
                response.setMoveJudge(redisData.getMoveJudge());
                response.setDetectQrcode(redisData.getDetectQrcode());
                response.setEnterGarage(redisData.getEnterGarage());
                response.setMqttMessageType(redisData.getMqttMessageType());

                // GPS 位置
                if (redisData.getLocation() != null) {
                    response.setLongitude(redisData.getLocation().getLon());
                    response.setLatitude(redisData.getLocation().getLat());
                    if (response.getLon() == null) {
                        response.setLon(redisData.getLocation().getLon());
                    }
                    if (response.getLat() == null) {
                        response.setLat(redisData.getLocation().getLat());
                    }
                }

                // D12 接驳车特有字段
                response.setD12WorkWay(redisData.getD12WorkWay());
                response.setLeftRowStart(redisData.getLeftRowStart());
                response.setLeftRowEnd(redisData.getLeftRowEnd());
                response.setRightRowStart(redisData.getRightRowStart());
                response.setRightRowEnd(redisData.getRightRowEnd());
                response.setWalkFastSpeed(redisData.getWalkFastSpeed());
                response.setWalkSlowSpeed(redisData.getWalkSlowSpeed());
                response.setCurrentRowPosition(redisData.getCurrentRowPosition());
                response.setBatteryLowLimit(redisData.getBatteryLowLimit());
                response.setRobotInPositionTime(redisData.getRobotInPositionTime());
                response.setLimitPositionCheckTime(redisData.getLimitPositionCheckTime());
                response.setWalkPositionCheckTime(redisData.getWalkPositionCheckTime());

                log.debug("设备 {} 实时数据获取成功", vehicle.getSerialNumber());

                // 🕵️‍♂️ 侦探日志：帮用户把那个幽灵设备的序列号抓出来
                if ((vehicle.getProductId() == null || vehicle.getProductId().isEmpty())
                        && Boolean.TRUE.equals(response.getOnline())) {
                    log.warn("==================================================");
                    log.warn("👻 捉到一只幽灵设备！");
                    log.warn("状态: {}", response.getStatus());
                    log.warn("序列号 (Serial Number): {}", vehicle.getSerialNumber());
                    log.warn("数据库ID: {}", vehicle.getId());
                    log.warn("Redis中对应Key: {}", redisData.getDeviceId());
                    log.warn("请在数据库 vehicle 表中删除或修复此序列号的记录");
                    log.warn("==================================================");
                }
            } else {
                // Redis 中没有数据时统一视为离线
                log.debug("设备 {} Redis 中没有实时数据，返回离线状态", vehicle.getSerialNumber());
                response.setOnline(false);
                response.setStatus("offline");
                response.setBattery(null);
            }

        } catch (Exception e) {
            log.error("获取设备 {} 实时数据失败，返回离线状态", vehicle.getSerialNumber(), e);
            response.setOnline(false);
            response.setStatus("offline");
            response.setBattery(null);
        }

        applyShadowFallbacks(response, shadow);

        // ========== 回退配置数据（来自 railcar_config） ==========
        // 应对设备刚重启或MQTT心跳未包含配置参数导致前端显示为 0/0 的问题
        try {
            boolean trackRobot = isTrackRobotProductType(vehicle.getProductType());
            boolean dControlDevice = isDControlProductType(vehicle.getProductType());

            RailcarConfig config = railcarConfigService.getConfig(vehicle.getProductType(), vehicle.getProductId());
            if (config != null) {
                if (response.getWalkSpeed() == null || response.getWalkSpeed() <= 0) {
                    response.setWalkSpeed(config.getWalkingSpeed());
                }
                if (response.getBrushSpeed() == null || response.getBrushSpeed() <= 0) {
                    response.setBrushSpeed(config.getBrushSpeed());
                }
                if (dControlDevice && (response.getBridgeSpeed() == null || response.getBridgeSpeed() <= 0)) {
                    response.setBridgeSpeed(config.getBridgeSpeed());
                }
                if (dControlDevice && (response.getWorkMode() == null || response.getWorkMode() <= 0)) {
                    response.setWorkMode(config.getWorkMode());
                }
                if (dControlDevice && (response.getRunControl() == null || response.getRunControl() <= 0)) {
                    response.setRunControl(config.getOperationMode());
                }
                if (dControlDevice && (response.getRunEnable() == null || response.getRunEnable() <= 0)) {
                    response.setRunEnable(config.getOperationEnable());
                }
                if (trackRobot) {
                    response.setBridgeSpeed(null);
                    response.setWorkMode(null);
                    response.setRunControl(null);
                    response.setRunEnable(null);
                }
            }
        } catch (Exception e) {
            log.error("获取设备 {} 轨道车配置作为回退失败", vehicle.getSerialNumber(), e);
        }

        normalizeDeviceSpecificFields(response, vehicle.getProductType());

        return response;
    }

    private void applyShadowFallbacks(MiniAppRobotResponse response, DeviceShadowStatus shadow) {
        if (shadow == null || !Boolean.TRUE.equals(shadow.getExists())) {
            return;
        }

        java.util.Map<String, Object> detail = shadow.getDetail();

        if (response.getOnline() == null) {
            response.setOnline("ONLINE".equalsIgnoreCase(shadow.getOnlineState()));
        }
        if (response.getStatus() == null || response.getStatus().trim().isEmpty()) {
            response.setStatus(mapShadowToLegacyStatus(shadow));
        }
        if (response.getBattery() == null) {
            response.setBattery(shadow.getBattery());
        }
        if (response.getVoltage() == null) {
            response.setVoltage(shadow.getVoltage());
        }
        if (response.getAngle() == null) {
            response.setAngle(shadow.getAngle());
        }
        if (response.getSpeed() == null) {
            response.setSpeed(getDetailInteger(detail, "speed"));
        }
        if (response.getBrushSpeed() == null) {
            response.setBrushSpeed(getDetailInteger(detail, "brush_speed"));
        }
        if (response.getLon() == null) {
            response.setLon(getDetailDouble(detail, "lon"));
        }
        if (response.getLat() == null) {
            response.setLat(getDetailDouble(detail, "lat"));
        }
        if (response.getHeading() == null) {
            response.setHeading(getDetailDouble(detail, "heading"));
        }
        if (response.getAction() == null) {
            response.setAction(getDetailString(detail, "action"));
        }
        if (response.getTaskName() == null) {
            response.setTaskName(getDetailString(detail, "task_name"));
        }
        if (response.getCurTaskIndex() == null) {
            response.setCurTaskIndex(getDetailInteger(detail, "cur_task_index"));
        }
        if (response.getTaskCount() == null) {
            response.setTaskCount(getDetailInteger(detail, "task_count"));
        }
        if (response.getTimestamp() == null) {
            response.setTimestamp(shadow.getUpdatedAt());
        }
        if (response.getMqttMessageType() == null) {
            response.setMqttMessageType(shadow.getMqttMessageType());
        }
        if (response.getLongitude() == null && shadow.getLocation() != null) {
            response.setLongitude(shadow.getLocation().getLon());
        }
        if (response.getLatitude() == null && shadow.getLocation() != null) {
            response.setLatitude(shadow.getLocation().getLat());
        }
        if (response.getLon() == null && shadow.getLocation() != null) {
            response.setLon(shadow.getLocation().getLon());
        }
        if (response.getLat() == null && shadow.getLocation() != null) {
            response.setLat(shadow.getLocation().getLat());
        }

        if (response.getTracking() == null) {
            response.setTracking(getDetailBoolean(detail, "tracking"));
        }
        if (response.getPathPlanning() == null) {
            response.setPathPlanning(getDetailString(detail, "path_planning"));
        }
        if (response.getLeftEdge() == null) {
            response.setLeftEdge(getDetailInteger(detail, "left_edge"));
        }
        if (response.getRightEdge() == null) {
            response.setRightEdge(getDetailInteger(detail, "right_edge"));
        }
        if (response.getMoveJudge() == null) {
            response.setMoveJudge(getDetailBoolean(detail, "move_judge"));
        }
        if (response.getDetectQrcode() == null) {
            response.setDetectQrcode(getDetailBoolean(detail, "detect_qrcode"));
        }
        if (response.getEnterGarage() == null) {
            response.setEnterGarage(getDetailBoolean(detail, "enter_garage"));
        }

        if (response.getRunControl() == null) {
            response.setRunControl(getDetailInteger(detail, "run_control"));
        }
        if (response.getRunEnable() == null) {
            response.setRunEnable(getDetailInteger(detail, "run_enable"));
        }
        if (response.getWorkMode() == null) {
            response.setWorkMode(getDetailInteger(detail, "work_mode"));
        }
        if (response.getWalkSpeed() == null) {
            response.setWalkSpeed(getDetailInteger(detail, "walk_speed"));
        }
        if (response.getBrushSpeed() == null) {
            response.setBrushSpeed(getDetailInteger(detail, "brush_speed"));
        }
        if (response.getBridgeSpeed() == null) {
            response.setBridgeSpeed(getDetailInteger(detail, "bridge_speed"));
        }
        if (response.getRunTimeSingle() == null) {
            response.setRunTimeSingle(getDetailDouble(detail, "run_time_single"));
        }
        if (response.getRunTimeTotal() == null) {
            response.setRunTimeTotal(getDetailDouble(detail, "run_time_total"));
        }
        if (response.getMileageSingle() == null) {
            response.setMileageSingle(getDetailDouble(detail, "mileage_single"));
        }
        if (response.getMileageTotal() == null) {
            response.setMileageTotal(getDetailDouble(detail, "mileage_total"));
        }
        if (response.getHeartbeat() == null) {
            response.setHeartbeat(getDetailDouble(detail, "heartbeat"));
        }

        if (response.getD12WorkWay() == null) {
            response.setD12WorkWay(getDetailInteger(detail, "d12_work_way"));
        }
        if (response.getLeftRowStart() == null) {
            response.setLeftRowStart(getDetailInteger(detail, "left_row_start"));
        }
        if (response.getLeftRowEnd() == null) {
            response.setLeftRowEnd(getDetailInteger(detail, "left_row_end"));
        }
        if (response.getRightRowStart() == null) {
            response.setRightRowStart(getDetailInteger(detail, "right_row_start"));
        }
        if (response.getRightRowEnd() == null) {
            response.setRightRowEnd(getDetailInteger(detail, "right_row_end"));
        }
        if (response.getWalkFastSpeed() == null) {
            response.setWalkFastSpeed(getDetailInteger(detail, "walk_fast_speed"));
        }
        if (response.getWalkSlowSpeed() == null) {
            response.setWalkSlowSpeed(getDetailInteger(detail, "walk_slow_speed"));
        }
        if (response.getCurrentRowPosition() == null) {
            response.setCurrentRowPosition(getDetailInteger(detail, "current_row_position"));
        }
        if (response.getBatteryLowLimit() == null) {
            response.setBatteryLowLimit(getDetailInteger(detail, "battery_low_limit"));
        }
        if (response.getRobotInPositionTime() == null) {
            response.setRobotInPositionTime(getDetailInteger(detail, "robot_in_position_time"));
        }
        if (response.getLimitPositionCheckTime() == null) {
            response.setLimitPositionCheckTime(getDetailInteger(detail, "limit_position_check_time"));
        }
        if (response.getWalkPositionCheckTime() == null) {
            response.setWalkPositionCheckTime(getDetailInteger(detail, "walk_position_check_time"));
        }
    }

    private void normalizeDeviceSpecificFields(MiniAppRobotResponse response, String productType) {
        if (isTrackRobotProductType(productType)) {
            clearDControlFields(response);
            clearBindingFields(response);
            return;
        }
        if (productType != null && productType.startsWith("-D")) {
            clearTFields(response);
            if (!"-D12".equals(productType)) {
                clearBindingFields(response);
            }
        }
    }

    private boolean isTrackRobotProductType(String productType) {
        return productType != null && productType.startsWith("-T") && !"-T12".equals(productType);
    }

    private boolean isDControlProductType(String productType) {
        return productType != null && (productType.startsWith("-D") || "-T12".equals(productType));
    }

    private void clearDControlFields(MiniAppRobotResponse response) {
        response.setRunControl(null);
        response.setRunEnable(null);
        response.setWorkMode(null);
        response.setBridgeSpeed(null);
        response.setHeartbeat(null);
    }

    private void clearBindingFields(MiniAppRobotResponse response) {
        response.setD12WorkWay(null);
        response.setBoundDeviceIds(new ArrayList<String>());
        response.setBoundDeviceCount(0);
        response.setLeftRowStart(null);
        response.setLeftRowEnd(null);
        response.setRightRowStart(null);
        response.setRightRowEnd(null);
        response.setWalkFastSpeed(null);
        response.setWalkSlowSpeed(null);
        response.setCurrentRowPosition(null);
        response.setBatteryLowLimit(null);
        response.setRobotInPositionTime(null);
        response.setLimitPositionCheckTime(null);
        response.setWalkPositionCheckTime(null);
    }

    private void clearTFields(MiniAppRobotResponse response) {
        response.setVoltage(null);
        response.setAngle(null);
        response.setSpeed(null);
        response.setLat(null);
        response.setLon(null);
        response.setHeading(null);
        response.setAction(null);
        response.setTaskName(null);
        response.setCurTaskIndex(null);
        response.setTaskCount(null);
        response.setTimestamp(null);
        response.setTaskOrigin(null);
        response.setCurrentLocation(null);
        response.setDistanceToTaskOriginM(null);
        response.setTaskOriginToleranceM(null);
        response.setIsAtTaskOrigin(null);
        response.setTracking(null);
        response.setPathPlanning(null);
        response.setLeftEdge(null);
        response.setRightEdge(null);
        response.setMoveJudge(null);
        response.setDetectQrcode(null);
        response.setEnterGarage(null);
        response.setMqttMessageType(null);
    }

    private String getDetailString(java.util.Map<String, Object> detail, String key) {
        Object value = getDetailValue(detail, key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Integer getDetailInteger(java.util.Map<String, Object> detail, String key) {
        Object value = getDetailValue(detail, key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.valueOf(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double getDetailDouble(java.util.Map<String, Object> detail, String key) {
        Object value = getDetailValue(detail, key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.valueOf(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean getDetailBoolean(java.util.Map<String, Object> detail, String key) {
        Object value = getDetailValue(detail, key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                return false;
            }
        }
        return null;
    }

    private Object getDetailValue(java.util.Map<String, Object> detail, String key) {
        if (detail == null || detail.isEmpty() || key == null) {
            return null;
        }
        return detail.get(key);
    }

    private MiniAppRobotResponse.LocationData toMiniAppLocation(VehicleRedisData.LocationDTO source) {
        if (source == null) {
            return null;
        }
        MiniAppRobotResponse.LocationData location = new MiniAppRobotResponse.LocationData();
        location.setLon(source.getLon());
        location.setLat(source.getLat());
        return location;
    }

    private MiniAppRobotResponse.CurrentLocationData toMiniAppCurrentLocation(
            VehicleRedisData.CurrentLocationDTO source) {
        if (source == null) {
            return null;
        }
        MiniAppRobotResponse.CurrentLocationData location = new MiniAppRobotResponse.CurrentLocationData();
        location.setLon(source.getLon());
        location.setLat(source.getLat());
        location.setHeading(source.getHeading());
        return location;
    }

    private String mapShadowToLegacyStatus(DeviceShadowStatus shadow) {
        if (!"ONLINE".equalsIgnoreCase(shadow.getOnlineState())) {
            return "offline";
        }
        if ("CHARGING".equalsIgnoreCase(shadow.getMissionState())) {
            return "charging";
        }
        if ("RUNNING".equalsIgnoreCase(shadow.getMissionState())) {
            return "running";
        }
        return "idle";
    }

    private boolean hasDeviceAccess(Integer roleId, Integer userId, String serialNumber) {
        String normalizedSerial = normalizeSerialNumber(serialNumber);
        if (normalizedSerial.isEmpty()) {
            return false;
        }
        if (roleId != null && roleId <= 2) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        List<Vehicle> userVehicles = vehicleService.getVehiclesByUserId(userId);
        return userVehicles.stream().anyMatch(v -> serialEquals(normalizedSerial, v.getSerialNumber()));
    }

    private String normalizeSerialNumber(String serialNumber) {
        if (serialNumber == null) {
            return "";
        }
        return serialNumber.trim().toUpperCase();
    }

    private boolean serialEquals(String left, String right) {
        String normalizedLeft = normalizeSerialNumber(left);
        String normalizedRight = normalizeSerialNumber(right);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(normalizedRight);
    }

    private List<String> parseBoundD01Serials(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<String>();
        }
        Set<String> unique = new LinkedHashSet<String>();
        String[] parts = raw.split(",");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String serial = part.trim();
            if (!serial.isEmpty()) {
                unique.add(serial);
            }
            if (unique.size() >= 5) {
                break;
            }
        }
        return new ArrayList<String>(unique);
    }

    /**
     * 映射状态
     * 将数据库状态映射为小程序使用的状态
     */
    private String mapStatus(String status) {
        if (status == null) {
            return "offline";
        }

        switch (status) {
            case "working":
            case "cleaning":
            case "running":
                return "running";
            case "charging":
                return "charging";
            case "active":
            case "idle":
            case "stopped":
            case "manual":
            case "resetting":
            case "unknown":
            case "disabled":
                return "idle";
            case "offline":
                return "offline";
            default:
                return "idle";
        }
    }
}
