package com.zt.cleanbot.controller;

import com.zt.cleanbot.dto.DeviceStatusQueryResponse;
import com.zt.cleanbot.dto.DeviceShadowStatus;
import com.zt.cleanbot.service.DeviceShadowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设备状态查询 API
 * 提供设备状态查询接口（轮询方式）
 */
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/api/device-status")
@Slf4j
public class DeviceStatusController {

    private final DeviceShadowService deviceShadowService;

    public DeviceStatusController(DeviceShadowService deviceShadowService) {
        this.deviceShadowService = deviceShadowService;
    }

    /**
     * 查询设备实时状态
     *
     * GET /api/device-status/{deviceId}
     *
     * @param deviceId 设备ID（如 -D01250001）
     * @return 设备状态响应
     */
    @GetMapping("/{deviceId}")
    public DeviceStatusQueryResponse getDeviceStatus(@PathVariable String deviceId) {
        log.info("查询设备状态 - 设备ID: {}", deviceId);

        DeviceStatusQueryResponse response = new DeviceStatusQueryResponse();
        response.setDeviceId(deviceId);

        try {
            DeviceShadowStatus shadow = deviceShadowService.getDeviceShadow(deviceId);
            if (!Boolean.TRUE.equals(shadow.getExists())) {
                response.setExists(false);
                log.warn("设备状态未找到 - 设备ID: {}", deviceId);
                return response;
            }

            response.setExists(true);
            response.setBattery(shadow.getBattery());
            response.setStatus(toLegacyStatus(shadow));
            response.setOperationMode(shadow.getControlState());
            response.setLastUpdateTime(shadow.getUpdatedAt());
            response.setLocation(toLegacyLocation(shadow));

            log.info("设备状态查询成功 - 设备ID: {}, 电量: {}%, 状态: {}",
                    deviceId, response.getBattery(), response.getStatus());

        } catch (Exception e) {
            log.error("查询设备状态失败 - 设备ID: {}", deviceId, e);
            response.setExists(false);
        }

        return response;
    }

    private DeviceStatusQueryResponse.LocationData toLegacyLocation(DeviceShadowStatus shadow) {
        if (shadow.getLocation() == null) {
            return null;
        }
        DeviceStatusQueryResponse.LocationData location = new DeviceStatusQueryResponse.LocationData();
        location.setLon(shadow.getLocation().getLon());
        location.setLat(shadow.getLocation().getLat());
        return location;
    }

    private String toLegacyStatus(DeviceShadowStatus shadow) {
        if (shadow == null || !Boolean.TRUE.equals(shadow.getExists())) {
            return "offline";
        }
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

    /**
     * 批量查询设备状态
     *
     * POST /api/device-status/batch
     *
     * 请求体：
     * {
     *   "deviceIds": ["-D01250001", "-D01250002"]
     * }
     *
     * @param deviceIds 设备ID列表
     * @return 设备状态列表
     */
    @PostMapping("/batch")
    public Map<String, DeviceStatusQueryResponse> getDeviceStatusBatch(@RequestBody Map<String, String[]> request) {
        String[] deviceIds = request.get("deviceIds");
        log.info("批量查询设备状态 - 设备数量: {}", deviceIds != null ? deviceIds.length : 0);

        Map<String, DeviceStatusQueryResponse> resultMap = new java.util.HashMap<>();

        if (deviceIds != null) {
            for (String deviceId : deviceIds) {
                DeviceStatusQueryResponse response = getDeviceStatus(deviceId);
                resultMap.put(deviceId, response);
            }
        }

        return resultMap;
    }

    /**
     * 查询标准化设备状态快照（第一阶段新接口）
     *
     * GET /api/device-status/{deviceId}/shadow
     */
    @GetMapping("/{deviceId}/shadow")
    public DeviceShadowStatus getDeviceShadow(@PathVariable String deviceId) {
        log.info("查询标准状态快照 - 设备ID: {}", deviceId);
        return deviceShadowService.getDeviceShadow(deviceId);
    }

    /**
     * 批量查询标准化设备状态快照
     *
     * POST /api/device-status/shadow/batch
     */
    @PostMapping("/shadow/batch")
    public Map<String, DeviceShadowStatus> getDeviceShadowBatch(@RequestBody Map<String, String[]> request) {
        String[] deviceIds = request.get("deviceIds");
        log.info("批量查询标准状态快照 - 设备数量: {}", deviceIds != null ? deviceIds.length : 0);

        Map<String, DeviceShadowStatus> resultMap = new java.util.HashMap<>();
        if (deviceIds != null) {
            for (String deviceId : deviceIds) {
                resultMap.put(deviceId, deviceShadowService.getDeviceShadow(deviceId));
            }
        }
        return resultMap;
    }
}
