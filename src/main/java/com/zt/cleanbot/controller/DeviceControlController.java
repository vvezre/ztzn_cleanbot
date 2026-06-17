package com.zt.cleanbot.controller;

import com.zt.cleanbot.dto.DeviceConfigRequest;
import com.zt.cleanbot.dto.DeviceControlResponse;
import com.zt.cleanbot.service.DeviceControlService;
import com.zt.cleanbot.service.VehicleService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

/**
 * 设备控制 API（安全模式）
 * 前端只传递业务参数，后端负责编码和发送
 */
@CrossOrigin(origins = { "*" }, maxAge = 3600L)
@RestController
@RequestMapping("/api/device-control")
@Slf4j
public class DeviceControlController {
    private static final int PARAMETER_ADMIN_ROLE_MAX = 2;

    @Autowired
    private DeviceControlService deviceControlService;

    @Autowired
    private VehicleService vehicleService;

    /**
     * 发送设备控制命令
     *
     * @param request     设备配置请求
     * @param httpRequest HTTP 请求（用于获取用户信息和客户端 IP）
     * @return 控制响应
     */
    @PostMapping("/send-command")
    public DeviceControlResponse sendCommand(
            @RequestBody DeviceConfigRequest request,
            HttpServletRequest httpRequest) {
        long requestStartNs = System.nanoTime();
        long serverReceivedAtMs = System.currentTimeMillis();
        String traceId = MDC.get("traceId");
        String clientIp = getClientIp(httpRequest);
        request.setServerReceivedTimestamp(serverReceivedAtMs);

        log.info("[latency] send-command 请求进入 - traceId: {}, 设备: {}, 控制模式: {}, 使能模式: {}, 客户端IP: {}",
                traceId, request.getDeviceId(), request.getControlMode(), request.getEnableMode(), clientIp);
        if (request.getClientClickTimestamp() != null) {
            log.info("[latency] 小程序按钮到服务端入口 - traceId: {}, 设备: {}, 耗时: {} ms",
                    traceId, request.getDeviceId(), Math.max(0L, serverReceivedAtMs - request.getClientClickTimestamp()));
        }

        // 使用鉴权拦截器解析出的用户信息
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String username = (String) httpRequest.getAttribute("username");

        if (userId == null) {
            log.warn("控制接口请求缺失用户信息，请检查拦截器转发是否正常");
            return DeviceControlResponse.failure(request.getDeviceId(), "未登录");
        }

        // 越权校验：检查该设备是否属于该用户
        if (!vehicleService.hasDeviceAccess(userId, roleId, request.getDeviceId())) {
            log.warn("越权访问尝试拦截：用户 {} (ID: {}) 尝试操作非绑定设备 {}", username, userId, request.getDeviceId());
            return DeviceControlResponse.failure(request.getDeviceId(), "权限不足：您未绑定该设备");
        }
        if (!canSendParameters(roleId) && !Boolean.TRUE.equals(request.getQuickAction())) {
            log.warn("参数下发权限拦截：用户 {} (ID: {}, roleId: {}) 尝试向设备 {} 下发参数，quickAction={}",
                    username, userId, roleId, request.getDeviceId(), request.getQuickAction());
            return DeviceControlResponse.failure(request.getDeviceId(), "权限不足：普通用户无法下发参数");
        }

        request.setUserId(userId);
        request.setUsername(username);

        log.debug("客户端 IP: {}", clientIp);

        try {
            DeviceControlResponse response = deviceControlService.sendControlCommand(request);
            long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStartNs);
            long responseAtMs = System.currentTimeMillis();
            response.setServerRequestTotalMs(totalMs);
            if (response.getTraceId() == null || response.getTraceId().trim().isEmpty()) {
                response.setTraceId(traceId);
            }
            if (request.getClientClickTimestamp() != null) {
                response.setClientToResponseMs(Math.max(0L, responseAtMs - request.getClientClickTimestamp()));
            }
            log.info("设备控制请求处理完成 - 设备: {}, 成功: {}", request.getDeviceId(), response.getSuccess());
            log.info("[latency] send-command 请求完成 - traceId: {}, 设备: {}, success: {}, 总耗时: {} ms",
                    traceId, request.getDeviceId(), response.getSuccess(), totalMs);
            return response;
        } catch (Exception e) {
            long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStartNs);
            log.error("设备控制请求处理异常 - 设备: {}", request.getDeviceId(), e);
            log.error("[latency] send-command 请求异常 - traceId: {}, 设备: {}, 总耗时: {} ms, 错误: {}",
                    traceId, request.getDeviceId(), totalMs, e.getMessage());
            DeviceControlResponse response = DeviceControlResponse.failure(request.getDeviceId(), "服务器内部错误: " + e.getMessage());
            response.setTraceId(traceId);
            response.setServerRequestTotalMs(totalMs);
            if (request.getClientClickTimestamp() != null) {
                response.setClientToResponseMs(Math.max(0L, System.currentTimeMillis() - request.getClientClickTimestamp()));
            }
            return response;
        }
    }

    /**
     * 交互帧专用模式设置
     */
    @PostMapping("/set-mode")
    public DeviceControlResponse setMode(@RequestBody DeviceConfigRequest request, HttpServletRequest httpRequest) {
        long requestStartNs = System.nanoTime();
        long serverReceivedAtMs = System.currentTimeMillis();
        String traceId = MDC.get("traceId");
        request.setServerReceivedTimestamp(serverReceivedAtMs);
        log.info("收到设备模式设置请求 - 设备: {}, 控制模式: {}, 使能模式: {}",
                request.getDeviceId(), request.getControlMode(), request.getEnableMode());
        if (request.getClientClickTimestamp() != null) {
            log.info("[latency] 小程序按钮到 set-mode 服务端入口 - traceId: {}, 设备: {}, 耗时: {} ms",
                    traceId, request.getDeviceId(), Math.max(0L, serverReceivedAtMs - request.getClientClickTimestamp()));
        }

        // 使用鉴权解析出的用户信息
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String username = (String) httpRequest.getAttribute("username");

        if (userId == null) {
            return DeviceControlResponse.failure(request.getDeviceId(), "未登录");
        }

        // 越权校验
        if (!vehicleService.hasDeviceAccess(userId, roleId, request.getDeviceId())) {
            log.warn("越权操作拦截：用户 {} 尝试设置未绑定设备 {} 的模式", username, request.getDeviceId());
            return DeviceControlResponse.failure(request.getDeviceId(), "权限不足");
        }
        if (!canSendParameters(roleId) && !Boolean.TRUE.equals(request.getQuickAction())) {
            log.warn("参数下发权限拦截：用户 {} (ID: {}, roleId: {}) 尝试设置设备 {} 的模式，quickAction={}",
                    username, userId, roleId, request.getDeviceId(), request.getQuickAction());
            return DeviceControlResponse.failure(request.getDeviceId(), "权限不足：普通用户无法下发参数");
        }

        request.setUserId(userId);
        request.setUsername(username);

        // set-mode 为交互帧专用入口，固定走交互帧(0x02)
        request.setInfoCommandType(0x00);
        if (request.getFaultCode() == null) {
            request.setFaultCode(0);
        }
        if (request.getCurrentRowPosition() == null) {
            request.setCurrentRowPosition(0);
        }

        try {
            DeviceControlResponse response = deviceControlService.sendControlCommand(request);
            long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStartNs);
            long responseAtMs = System.currentTimeMillis();
            response.setServerRequestTotalMs(totalMs);
            if (response.getTraceId() == null || response.getTraceId().trim().isEmpty()) {
                response.setTraceId(traceId);
            }
            if (request.getClientClickTimestamp() != null) {
                response.setClientToResponseMs(Math.max(0L, responseAtMs - request.getClientClickTimestamp()));
            }
            log.info("设备模式设置完成 - 设备: {}, 成功: {}", request.getDeviceId(), response.getSuccess());
            return response;
        } catch (Exception e) {
            log.error("设备模式设置异常 - 设备: {}", request.getDeviceId(), e);
            DeviceControlResponse response = DeviceControlResponse.failure(request.getDeviceId(), "服务器内部错误: " + e.getMessage());
            response.setTraceId(traceId);
            response.setServerRequestTotalMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStartNs));
            if (request.getClientClickTimestamp() != null) {
                response.setClientToResponseMs(Math.max(0L, System.currentTimeMillis() - request.getClientClickTimestamp()));
            }
            return response;
        }
    }

    /**
     * 获取客户端 IP 地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private boolean canSendParameters(Integer roleId) {
        return roleId != null && roleId <= PARAMETER_ADMIN_ROLE_MAX;
    }
}
