package com.zt.cleanbot.controller;

import com.zt.cleanbot.dto.TRailcarCommandRequest;
import com.zt.cleanbot.dto.TRailcarControlResponse;
import com.zt.cleanbot.service.TRailcarControlService;
import com.zt.cleanbot.service.VehicleService;
import com.zt.cleanbot.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * T型号小车控制API
 */
@CrossOrigin(origins = { "*" }, maxAge = 3600L)
@RestController
@RequestMapping("/api/t-railcar")
@Slf4j
public class TRailcarController {

    @Autowired
    private TRailcarControlService tRailcarControlService;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private RedisUtil redisUtil;

    @PostMapping("/command")
    public ResponseEntity<TRailcarControlResponse> sendCommand(
            @RequestBody TRailcarCommandRequest request,
            HttpServletRequest httpRequest) {

        log.info("收到T型号小车控制请求 - 产品ID: {}, 命令: {}",
                request.getProductId(), request.getCommand());

        if (request.getProductId() == null || request.getProductId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    TRailcarControlResponse.failure("unknown", "unknown", "产品ID不能为空", null, null));
        }

        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String username = (String) httpRequest.getAttribute("username");

        if (userId == null) {
            return ResponseEntity.status(401).body(
                    TRailcarControlResponse.failure(request.getFullDeviceId(), request.getCommand(), "未登录", null, null));
        }

        // 越权校验
        if (!vehicleService.hasDeviceAccess(userId, roleId, request.getFullDeviceId())) {
            log.warn("T系列越权操作拦截：用户 {} 尝试控制未绑定设备 {}", username, request.getFullDeviceId());
            return ResponseEntity.status(403).body(
                    TRailcarControlResponse.failure(request.getFullDeviceId(), request.getCommand(), "权限不足", null, null));
        }

        request.setUserId(userId);
        request.setUsername(username);

        String clientIp = getClientIp(httpRequest);
        log.debug("客户端IP: {}", clientIp);

        TRailcarControlResponse response = tRailcarControlService.sendCommand(request);

        if (response.getSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 快速移动命令
     */
    @PostMapping("/move/{action}")
    public ResponseEntity<TRailcarControlResponse> quickMove(
            @PathVariable String action,
            @RequestParam String productId,
            HttpServletRequest httpRequest) {

        TRailcarCommandRequest request = new TRailcarCommandRequest();
        request.setProductId(productId);
        request.setCommand(action);
        return sendCommand(request, httpRequest);
    }

    /**
     * 调整速度
     */
    @PostMapping("/speed")
    public ResponseEntity<TRailcarControlResponse> adjustSpeed(
            @RequestBody Map<String, Object> params,
            HttpServletRequest httpRequest) {

        TRailcarCommandRequest request = new TRailcarCommandRequest();
        request.setProductId((String) params.get("productId"));
        request.setCommand("speed");
        request.setParams(params);
        return sendCommand(request, httpRequest);
    }

    /**
     * 高级功能控制
     */
    @PostMapping("/advanced/{function}")
    public ResponseEntity<TRailcarControlResponse> advancedFunction(
            @PathVariable String function,
            @RequestParam String productId,
            HttpServletRequest httpRequest) {

        TRailcarCommandRequest request = new TRailcarCommandRequest();
        request.setProductId(productId);
        request.setCommand(function);
        return sendCommand(request, httpRequest);
    }

    @GetMapping("/task-path/{productId}")
    public ResponseEntity<Map<String, Object>> getTaskPath(
            @PathVariable String productId,
            HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String deviceId = "-T01" + productId;
        Map<String, Object> cachedPath = tRailcarControlService.getCachedTaskPath(productId);
        Map<String, Object> response = new LinkedHashMap<>();
        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录");
            response.put("data", null);
            return ResponseEntity.status(401).body(response);
        }
        if (!vehicleService.hasDeviceAccess(userId, roleId, deviceId)) {
            response.put("success", false);
            response.put("message", "权限不足");
            response.put("data", null);
            return ResponseEntity.status(403).body(response);
        }
        if (cachedPath == null) {
            response.put("success", false);
            response.put("message", "路径未就绪");
            response.put("data", null);
            return ResponseEntity.ok(response);
        }
        response.put("success", true);
        response.put("message", "获取路径成功");
        response.put("data", cachedPath);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tasks/{productId}")
    public ResponseEntity<Map<String, Object>> getTaskOptions(
            @PathVariable String productId,
            HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String deviceId = "-T01" + productId;
        Map<String, Object> response = new LinkedHashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录");
            response.put("data", null);
            return ResponseEntity.status(401).body(response);
        }
        if (!vehicleService.hasDeviceAccess(userId, roleId, deviceId)) {
            response.put("success", false);
            response.put("message", "权限不足");
            response.put("data", null);
            return ResponseEntity.status(403).body(response);
        }

        Set<String> taskNames = redisUtil.smembersRawString("taskNameSet");
        String currentTaskName = redisUtil.getRawString("currentTaskName");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskNames", new ArrayList<>(taskNames));
        data.put("currentTaskName", currentTaskName);

        response.put("success", true);
        response.put("message", taskNames.isEmpty() ? "暂无任务" : "获取任务列表成功");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tasks/current")
    public ResponseEntity<Map<String, Object>> setCurrentTask(
            @RequestBody Map<String, String> payload,
            HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String username = (String) httpRequest.getAttribute("username");
        String productId = payload.get("productId");
        String taskName = payload.get("taskName");
        String deviceId = "-T01" + productId;
        Map<String, Object> response = new LinkedHashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录");
            response.put("data", null);
            return ResponseEntity.status(401).body(response);
        }
        if (productId == null || productId.trim().isEmpty() || taskName == null || taskName.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "产品ID和任务名称不能为空");
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        }
        if (!vehicleService.hasDeviceAccess(userId, roleId, deviceId)) {
            response.put("success", false);
            response.put("message", "权限不足");
            response.put("data", null);
            return ResponseEntity.status(403).body(response);
        }

        TRailcarControlResponse setCurrentResponse = sendTaskCommand(productId, "set_current_task", taskName, userId, username);
        if (!Boolean.TRUE.equals(setCurrentResponse.getSuccess())) {
            response.put("success", false);
            response.put("message", setCurrentResponse.getMessage());
            response.put("data", null);
            return ResponseEntity.status(500).body(response);
        }

        redisUtil.delete("task_path:" + deviceId);
        TRailcarControlResponse pathResponse = sendTaskCommand(productId, "get_task_path", (String) null, userId, username);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskName", taskName);
        data.put("setCurrentCommandId", setCurrentResponse.getCommandId());
        data.put("pathCommandId", pathResponse.getCommandId());
        data.put("pathCommandStatus", pathResponse.getCommandStatus());
        data.put("pathCommandMessage", pathResponse.getMessage());

        response.put("success", true);
        response.put("message", "当前任务已设置，并已触发路径生成");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/tasks/cache")
    public ResponseEntity<Map<String, Object>> deleteCachedTask(
            @RequestBody Map<String, String> payload,
            HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String productId = payload.get("productId");
        String taskName = payload.get("taskName");
        String deviceId = "-T01" + productId;
        Map<String, Object> response = new LinkedHashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "鏈櫥褰?");
            response.put("data", null);
            return ResponseEntity.status(401).body(response);
        }
        if (productId == null || productId.trim().isEmpty() || taskName == null || taskName.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "浜у搧ID鍜屼换鍔″悕绉颁笉鑳戒负绌?");
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        }
        if (!vehicleService.hasDeviceAccess(userId, roleId, deviceId)) {
            response.put("success", false);
            response.put("message", "鏉冮檺涓嶈冻");
            response.put("data", null);
            return ResponseEntity.status(403).body(response);
        }

        Long removed = redisUtil.sremoveRawString("taskNameSet", taskName);
        String currentTaskName = redisUtil.getRawString("currentTaskName");
        if (taskName.equals(currentTaskName)) {
            redisUtil.delete("currentTaskName");
            redisUtil.delete("taskList");
            redisUtil.delete("task_path:" + deviceId);
        }
        redisUtil.hdelete("loc_start_lat_lon", taskName);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskName", taskName);
        data.put("removed", removed);
        data.put("wasCurrentTask", taskName.equals(currentTaskName));

        response.put("success", true);
        response.put("message", "缂撳瓨浠诲姟宸插垹闄?");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tasks/generate")
    public ResponseEntity<Map<String, Object>> generateTask(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String username = (String) httpRequest.getAttribute("username");
        String productId = payload.get("productId") == null ? null : String.valueOf(payload.get("productId"));
        String taskName = payload.get("taskName") == null ? null : String.valueOf(payload.get("taskName"));
        Object areaList = payload.get("areaList");
        String deviceId = "-T01" + productId;
        Map<String, Object> response = new LinkedHashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录");
            response.put("data", null);
            return ResponseEntity.status(401).body(response);
        }
        if (productId == null || productId.trim().isEmpty() || taskName == null || taskName.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "产品ID和任务名称不能为空");
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        }
        if (!(areaList instanceof Iterable)) {
            response.put("success", false);
            response.put("message", "areaList 不能为空，且必须是数组");
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        }
        if (!vehicleService.hasDeviceAccess(userId, roleId, deviceId)) {
            response.put("success", false);
            response.put("message", "权限不足");
            response.put("data", null);
            return ResponseEntity.status(403).body(response);
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("taskName", taskName);
        params.put("areaList", areaList);

        TRailcarControlResponse createResponse = sendTaskCommand(productId, "create_task", params, userId, username);
        if (!Boolean.TRUE.equals(createResponse.getSuccess())) {
            response.put("success", false);
            response.put("message", createResponse.getMessage());
            response.put("data", null);
            return ResponseEntity.status(500).body(response);
        }

        int areaCount = 0;
        for (Object ignored : (Iterable<?>) areaList) {
            areaCount++;
        }
        int layoutV2AreaCount = countLayoutV2Areas((Iterable<?>) areaList);
        if (layoutV2AreaCount > 0) {
            log.info("T railcar layoutVersion 2 task generate request - productId: {}, taskName: {}, areaCount: {}, layoutV2AreaCount: {}",
                    productId, taskName, areaCount, layoutV2AreaCount);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskName", taskName);
        data.put("areaCount", areaCount);
        data.put("layoutV2AreaCount", layoutV2AreaCount);
        data.put("commandId", createResponse.getCommandId());
        data.put("traceId", createResponse.getTraceId());
        data.put("commandStatus", createResponse.getCommandStatus());

        response.put("success", true);
        response.put("message", "任务生成命令已发送");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    static int countLayoutV2Areas(Iterable<?> areaList) {
        int count = 0;
        for (Object area : areaList) {
            if (!(area instanceof Map)) {
                continue;
            }

            Object layoutVersion = ((Map<?, ?>) area).get("layoutVersion");
            if ("2".equals(String.valueOf(layoutVersion))) {
                count++;
            }
        }
        return count;
    }

    private TRailcarControlResponse sendTaskCommand(
            String productId,
            String command,
            String taskName,
            Integer userId,
            String username) {
        Map<String, Object> params = null;
        if (taskName != null && !taskName.trim().isEmpty()) {
            params = new LinkedHashMap<>();
            params.put("taskName", taskName);
        }
        return sendTaskCommand(productId, command, params, userId, username);
    }

    private TRailcarControlResponse sendTaskCommand(
            String productId,
            String command,
            Map<String, Object> params,
            Integer userId,
            String username) {
        TRailcarCommandRequest request = new TRailcarCommandRequest();
        request.setProductId(productId);
        request.setCommand(command);
        request.setUserId(userId);
        request.setUsername(username);
        if (params != null && !params.isEmpty()) {
            request.setParams(params);
        }
        return tRailcarControlService.sendCommand(request);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
