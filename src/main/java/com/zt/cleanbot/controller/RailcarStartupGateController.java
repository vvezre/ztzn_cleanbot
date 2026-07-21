package com.zt.cleanbot.controller;

import com.zt.cleanbot.common.Result;
import com.zt.cleanbot.model.RailcarStartupGate;
import com.zt.cleanbot.model.Vehicle;
import com.zt.cleanbot.service.RailcarStartupGateService;
import com.zt.cleanbot.service.VehicleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 启动门禁 API
 * 控制设备是否允许启动，通过 MQTT retained 消息下发给上位机
 *
 * 上位机 startup_gate.py 在启动时订阅 RAILCAR/S/{serialNumber}/startup
 * 并读取 retained 消息中的 disabled 字段决定是否退出进程
 */
@CrossOrigin(origins = { "*" }, maxAge = 3600L)
@RestController
@RequestMapping("/api/railcar/startup-gate")
@Slf4j
public class RailcarStartupGateController {

    @Autowired
    private RailcarStartupGateService startupGateService;

    @Autowired
    private VehicleService vehicleService;

    /**
     * 查询设备启动门禁状态
     *
     * GET /api/railcar/startup-gate/{serialNumber}
     *
     * @param serialNumber 设备序列号，如 -T01250001
     * @return { serialNumber, disabled, exists } — disabled 默认为 false（允许启动）
     */
    @GetMapping("/{serialNumber}")
    public Result<Map<String, Object>> getStartupGate(
            @PathVariable String serialNumber,
            HttpServletRequest request) {
        try {
            Integer userId = (Integer) request.getAttribute("userId");
            Integer roleId = (Integer) request.getAttribute("roleId");

            if (userId == null) {
                return Result.unauthorized("未登录");
            }

            String normalizedSerial = normalizeSerial(serialNumber);

            // 校验设备是否存在
            Vehicle vehicle = vehicleService.getBySerialNumber(normalizedSerial);
            if (vehicle == null) {
                return Result.error(404, "设备不存在: " + normalizedSerial);
            }

            // 权限校验
            if (!vehicleService.hasDeviceAccess(userId, roleId, normalizedSerial)) {
                return Result.forbidden("无权限访问该设备");
            }

            RailcarStartupGate gate = startupGateService.getBySerialNumber(normalizedSerial);
            boolean disabled = gate != null && Boolean.TRUE.equals(gate.getDisabled());

            Map<String, Object> result = new HashMap<>();
            result.put("serialNumber", normalizedSerial);
            result.put("disabled", disabled);
            result.put("exists", gate != null);

            log.info("查询启动门禁 - 设备: {}, disabled: {}", normalizedSerial, disabled);
            return Result.success(result);

        } catch (Exception e) {
            log.error("查询启动门禁失败 - 设备: {}", serialNumber, e);
            return Result.error(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 设置设备启动门禁状态
     *
     * PUT /api/railcar/startup-gate/{serialNumber}
     * Body: { "disabled": true/false }
     *
     * 设置后会立即通过 MQTT retained 消息推送给设备
     */
    @PutMapping("/{serialNumber}")
    public Result<Map<String, Object>> setStartupGate(
            @PathVariable String serialNumber,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        try {
            Integer userId = (Integer) request.getAttribute("userId");
            Integer roleId = (Integer) request.getAttribute("roleId");
            String username = (String) request.getAttribute("username");

            if (userId == null) {
                return Result.unauthorized("未登录");
            }

            String normalizedSerial = normalizeSerial(serialNumber);
            if (normalizedSerial.isEmpty()) {
                return Result.error(400, "设备序列号不能为空");
            }

            // 校验设备是否存在
            Vehicle vehicle = vehicleService.getBySerialNumber(normalizedSerial);
            if (vehicle == null) {
                return Result.error(404, "设备不存在: " + normalizedSerial);
            }

            // 权限校验：管理员(roleId≤2) 或 设备绑定用户
            if (!vehicleService.hasDeviceAccess(userId, roleId, normalizedSerial)) {
                return Result.forbidden("无权限操作该设备");
            }

            // 解析 disabled 字段
            Object disabledObj = body.get("disabled");
            boolean disabled;
            if (disabledObj instanceof Boolean) {
                disabled = (Boolean) disabledObj;
            } else if (disabledObj instanceof String) {
                String text = ((String) disabledObj).trim().toLowerCase();
                if ("true".equals(text) || "1".equals(text)) {
                    disabled = true;
                } else if ("false".equals(text) || "0".equals(text)) {
                    disabled = false;
                } else {
                    return Result.error(400, "disabled 字段值无效，需要 boolean 类型");
                }
            } else if (disabledObj instanceof Number) {
                disabled = ((Number) disabledObj).intValue() != 0;
            } else {
                return Result.error(400, "缺少 disabled 字段");
            }

            String operator = username != null ? username : "userId:" + userId;
            boolean success = startupGateService.setDisabled(normalizedSerial, disabled, operator);

            if (success) {
                log.info("设置启动门禁成功 - 设备: {}, disabled: {}, 操作者: {}",
                        normalizedSerial, disabled, operator);

                Map<String, Object> result = new HashMap<>();
                result.put("serialNumber", normalizedSerial);
                result.put("disabled", disabled);
                return Result.success(result);
            } else {
                return Result.error(500, "设置失败，请稍后重试");
            }

        } catch (Exception e) {
            log.error("设置启动门禁失败 - 设备: {}", serialNumber, e);
            return Result.error(500, "设置失败: " + e.getMessage());
        }
    }

    private String normalizeSerial(String serialNumber) {
        if (serialNumber == null) {
            return "";
        }
        return serialNumber.trim().toUpperCase();
    }
}
