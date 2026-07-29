package com.zt.cleanbot.controller;

import com.zt.cleanbot.dto.CommandStatusSnapshot;
import com.zt.cleanbot.dto.TRailcarCommandRequest;
import com.zt.cleanbot.dto.TRailcarControlResponse;
import com.zt.cleanbot.service.CommandStatusService;
import com.zt.cleanbot.service.TRailcarControlService;
import com.zt.cleanbot.service.VehicleService;
import com.zt.cleanbot.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static final long MODELING_POINTS_QUERY_TIMEOUT_MS = 10_000L;

    @Autowired
    private TRailcarControlService tRailcarControlService;

    @Autowired
    private CommandStatusService commandStatusService;

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

    @GetMapping("/modeling-path/{productId}")
    public ResponseEntity<Map<String, Object>> getModelingPath(
            @PathVariable String productId,
            @RequestParam String modelId,
            HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String deviceId = "-T01" + productId;
        Map<String, Object> response = new LinkedHashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "not logged in");
            response.put("data", null);
            return ResponseEntity.status(401).body(response);
        }
        if (!vehicleService.hasDeviceAccess(userId, roleId, deviceId)) {
            response.put("success", false);
            response.put("message", "permission denied");
            response.put("data", null);
            return ResponseEntity.status(403).body(response);
        }
        if (modelId == null || !modelId.matches("[A-Za-z0-9_-]+")) {
            response.put("success", false);
            response.put("message", "invalid modelId");
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        }

        Map<String, Object> cachedPath = tRailcarControlService.getCachedModelingPath(productId, modelId);
        if (cachedPath == null) {
            response.put("success", false);
            response.put("message", "modeling path is not ready");
            response.put("data", null);
            return ResponseEntity.ok(response);
        }
        response.put("success", true);
        response.put("message", "modeling path fetched");
        response.put("data", cachedPath);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/modeling-result/{productId}")
    public ResponseEntity<Map<String, Object>> getModelingResult(
            @PathVariable String productId,
            HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String username = (String) httpRequest.getAttribute("username");
        Map<String, Object> response = new LinkedHashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "not logged in");
            response.put("data", null);
            return ResponseEntity.status(401).body(response);
        }
        if (productId == null || !productId.matches("\\d{6}")) {
            response.put("success", false);
            response.put("message", "invalid productId");
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        }

        String deviceId = "-T01" + productId;
        if (!vehicleService.hasDeviceAccess(userId, roleId, deviceId)) {
            response.put("success", false);
            response.put("message", "permission denied");
            response.put("data", null);
            return ResponseEntity.status(403).body(response);
        }

        TRailcarControlResponse commandResponse = sendTaskCommand(
                productId,
                "get_modeling_result",
                (Map<String, Object>) null,
                userId,
                username);
        if (!Boolean.TRUE.equals(commandResponse.getSuccess())) {
            response.put("success", false);
            response.put("message", commandResponse.getMessage());
            response.put("data", null);
            return ResponseEntity.status(502).body(response);
        }

        CommandStatusSnapshot snapshot = commandStatusService.waitForTerminal(
                commandResponse.getCommandId(),
                MODELING_POINTS_QUERY_TIMEOUT_MS);
        if (snapshot == null || !Boolean.TRUE.equals(snapshot.getTerminal())) {
            response.put("success", false);
            response.put("message", "robot response timeout");
            response.put("data", null);
            return ResponseEntity.status(504).body(response);
        }
        if (!"SUCCEEDED".equals(snapshot.getStatus())) {
            response.put("success", false);
            response.put("message", snapshot.getMessage());
            response.put("data", null);
            return ResponseEntity.status(502).body(response);
        }

        Map<String, Object> data = extractModelingResult(snapshot);
        if (data == null) {
            response.put("success", false);
            response.put("message", "robot modeling result response is invalid");
            response.put("data", null);
            return ResponseEntity.status(502).body(response);
        }

        response.put("success", true);
        response.put("message", "\u89c4\u5212\u6210\u529f");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/modeling-points/{productId}")
    public ResponseEntity<Map<String, Object>> getModelingPoints(
            @PathVariable String productId,
            HttpServletRequest httpRequest) {
        return getModelingPointList(
                productId,
                httpRequest,
                "get_modeling_points",
                "robot modeling points response is invalid");
    }

    @GetMapping("/modeling-link-points/{productId}")
    public ResponseEntity<Map<String, Object>> getModelingLinkPoints(
            @PathVariable String productId,
            HttpServletRequest httpRequest) {
        return getModelingPointList(
                productId,
                httpRequest,
                "get_modeling_link_points",
                "robot modeling link points response is invalid");
    }

    private ResponseEntity<Map<String, Object>> getModelingPointList(
            String productId,
            HttpServletRequest httpRequest,
            String command,
            String invalidResponseMessage) {
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String username = (String) httpRequest.getAttribute("username");
        Map<String, Object> response = new LinkedHashMap<>();

        if (userId == null) {
            response.put("message", "not logged in");
            return ResponseEntity.status(401).body(response);
        }
        if (productId == null || !productId.matches("\\d{6}")) {
            response.put("message", "invalid productId");
            return ResponseEntity.badRequest().body(response);
        }

        String deviceId = "-T01" + productId;
        if (!vehicleService.hasDeviceAccess(userId, roleId, deviceId)) {
            response.put("message", "permission denied");
            return ResponseEntity.status(403).body(response);
        }

        TRailcarControlResponse commandResponse = sendTaskCommand(
                productId,
                command,
                (Map<String, Object>) null,
                userId,
                username);
        if (!Boolean.TRUE.equals(commandResponse.getSuccess())) {
            response.put("message", commandResponse.getMessage());
            return ResponseEntity.status(502).body(response);
        }

        CommandStatusSnapshot snapshot = commandStatusService.waitForTerminal(
                commandResponse.getCommandId(),
                MODELING_POINTS_QUERY_TIMEOUT_MS);
        if (snapshot == null || !Boolean.TRUE.equals(snapshot.getTerminal())) {
            response.put("message", "robot response timeout");
            return ResponseEntity.status(504).body(response);
        }
        if (!"SUCCEEDED".equals(snapshot.getStatus())) {
            response.put("message", snapshot.getMessage());
            return ResponseEntity.status(502).body(response);
        }

        List<?> points = extractModelingPoints(snapshot);
        if (points == null) {
            response.put("message", invalidResponseMessage);
            return ResponseEntity.status(502).body(response);
        }
        response.put("points", points);
        return ResponseEntity.ok(response);
    }

    @SuppressWarnings("unchecked")
    static List<?> extractModelingPoints(CommandStatusSnapshot snapshot) {
        if (snapshot == null || snapshot.getDetail() == null) {
            return null;
        }
        Object resultValue = snapshot.getDetail().get("result");
        if (!(resultValue instanceof Map)) {
            return null;
        }
        Map<String, Object> result = (Map<String, Object>) resultValue;
        Object dataValue = result.get("data");
        if (!(dataValue instanceof Map)) {
            return null;
        }
        Object pointsValue = ((Map<String, Object>) dataValue).get("points");
        if (pointsValue == null) {
            return Collections.emptyList();
        }
        return pointsValue instanceof List ? (List<?>) pointsValue : null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> extractModelingResult(CommandStatusSnapshot snapshot) {
        if (snapshot == null || snapshot.getDetail() == null) {
            return null;
        }
        Object resultValue = snapshot.getDetail().get("result");
        if (!(resultValue instanceof Map)) {
            return null;
        }
        Object dataValue = ((Map<String, Object>) resultValue).get("data");
        if (!(dataValue instanceof Map)) {
            return null;
        }
        Map<String, Object> resultData = (Map<String, Object>) dataValue;
        Object areaPoints = resultData.get("areaPoints");
        Object linkPoints = resultData.get("linkPoints");
        Object pathPoints = resultData.get("pathPoints");
        if (!(areaPoints instanceof List)
                || !(linkPoints instanceof List)
                || !(pathPoints instanceof List)) {
            return null;
        }

        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("areaPoints", areaPoints);
        responseData.put("linkPoints", linkPoints);
        responseData.put("pathPoints", pathPoints);
        return responseData;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> extractCommandData(CommandStatusSnapshot snapshot) {
        if (snapshot == null || snapshot.getDetail() == null) {
            return null;
        }
        Object resultValue = snapshot.getDetail().get("result");
        if (!(resultValue instanceof Map)) {
            return null;
        }
        Object dataValue = ((Map<String, Object>) resultValue).get("data");
        return dataValue instanceof Map ? (Map<String, Object>) dataValue : null;
    }

    @PostMapping("/modeling-task/save")
    public ResponseEntity<Map<String, Object>> saveModelingTask(
            @RequestBody Map<String, String> payload,
            HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String username = (String) httpRequest.getAttribute("username");
        String productId = payload.get("productId");
        String taskName = payload.get("taskName");
        Map<String, Object> response = new LinkedHashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "not logged in");
            response.put("data", null);
            return ResponseEntity.status(401).body(response);
        }
        if (productId == null || !productId.matches("\\d{6}")
                || taskName == null || taskName.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "productId and taskName are required");
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        }

        String deviceId = "-T01" + productId;
        if (!vehicleService.hasDeviceAccess(userId, roleId, deviceId)) {
            response.put("success", false);
            response.put("message", "permission denied");
            response.put("data", null);
            return ResponseEntity.status(403).body(response);
        }

        TRailcarControlResponse commandResponse = sendTaskCommand(
                productId,
                "save_modeling_task",
                taskName.trim(),
                userId,
                username);
        if (!Boolean.TRUE.equals(commandResponse.getSuccess())) {
            response.put("success", false);
            response.put("message", commandResponse.getMessage());
            response.put("data", null);
            return ResponseEntity.status(502).body(response);
        }

        CommandStatusSnapshot snapshot = commandStatusService.waitForTerminal(
                commandResponse.getCommandId(),
                MODELING_POINTS_QUERY_TIMEOUT_MS);
        if (snapshot == null || !Boolean.TRUE.equals(snapshot.getTerminal())) {
            response.put("success", false);
            response.put("message", "robot response timeout");
            response.put("data", null);
            return ResponseEntity.status(504).body(response);
        }
        if (!"SUCCEEDED".equals(snapshot.getStatus())) {
            response.put("success", false);
            response.put("message", snapshot.getMessage());
            response.put("data", null);
            return ResponseEntity.status(409).body(response);
        }

        Map<String, Object> resultData = extractCommandData(snapshot);
        if (resultData == null || resultData.get("taskName") == null) {
            response.put("success", false);
            response.put("message", "robot task save response is invalid");
            response.put("data", null);
            return ResponseEntity.status(502).body(response);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskName", resultData.get("taskName"));
        data.put("taskCount", resultData.get("taskCount"));
        if (resultData.get("modelId") != null) {
            data.put("modelId", resultData.get("modelId"));
        }
        response.put("success", true);
        response.put("message", "\u8def\u7ebf\u4fdd\u5b58\u6210\u529f");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tasks/{productId}")
    public ResponseEntity<Map<String, Object>> getRobotTaskOptions(
            @PathVariable String productId,
            HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String username = (String) httpRequest.getAttribute("username");
        Map<String, Object> response = new LinkedHashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "not logged in");
            response.put("data", null);
            return ResponseEntity.status(401).body(response);
        }
        if (productId == null || !productId.matches("\\d{6}")) {
            response.put("success", false);
            response.put("message", "invalid productId");
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        }

        String deviceId = "-T01" + productId;
        if (!vehicleService.hasDeviceAccess(userId, roleId, deviceId)) {
            response.put("success", false);
            response.put("message", "permission denied");
            response.put("data", null);
            return ResponseEntity.status(403).body(response);
        }

        TRailcarControlResponse commandResponse = sendTaskCommand(
                productId,
                "get_task_names",
                (Map<String, Object>) null,
                userId,
                username);
        if (!Boolean.TRUE.equals(commandResponse.getSuccess())) {
            response.put("success", false);
            response.put("message", commandResponse.getMessage());
            response.put("data", null);
            return ResponseEntity.status(502).body(response);
        }

        CommandStatusSnapshot snapshot = commandStatusService.waitForTerminal(
                commandResponse.getCommandId(),
                MODELING_POINTS_QUERY_TIMEOUT_MS);
        if (snapshot == null || !Boolean.TRUE.equals(snapshot.getTerminal())) {
            response.put("success", false);
            response.put("message", "robot response timeout");
            response.put("data", null);
            return ResponseEntity.status(504).body(response);
        }
        if (!"SUCCEEDED".equals(snapshot.getStatus())) {
            response.put("success", false);
            response.put("message", snapshot.getMessage());
            response.put("data", null);
            return ResponseEntity.status(502).body(response);
        }

        Map<String, Object> resultData = extractCommandData(snapshot);
        if (resultData == null || !(resultData.get("taskNames") instanceof List)) {
            response.put("success", false);
            response.put("message", "robot task list response is invalid");
            response.put("data", null);
            return ResponseEntity.status(502).body(response);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskNames", resultData.get("taskNames"));
        data.put("currentTaskName", resultData.get("currentTaskName"));
        response.put("success", true);
        response.put("message", "\u83b7\u53d6\u8def\u7ebf\u5217\u8868\u6210\u529f");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/saved-routes/{productId}")
    public ResponseEntity<Map<String, Object>> getSavedRoutes(
            @PathVariable String productId,
            HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String username = (String) httpRequest.getAttribute("username");
        Map<String, Object> response = new LinkedHashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "not logged in");
            response.put("data", null);
            return ResponseEntity.status(401).body(response);
        }
        if (productId == null || !productId.matches("\\d{6}")) {
            response.put("success", false);
            response.put("message", "invalid productId");
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        }

        String deviceId = "-T01" + productId;
        if (!vehicleService.hasDeviceAccess(userId, roleId, deviceId)) {
            response.put("success", false);
            response.put("message", "permission denied");
            response.put("data", null);
            return ResponseEntity.status(403).body(response);
        }

        TRailcarControlResponse commandResponse = sendTaskCommand(
                productId,
                "get_saved_routes",
                (Map<String, Object>) null,
                userId,
                username);
        if (!Boolean.TRUE.equals(commandResponse.getSuccess())) {
            response.put("success", false);
            response.put("message", commandResponse.getMessage());
            response.put("data", null);
            return ResponseEntity.status(502).body(response);
        }

        CommandStatusSnapshot snapshot = commandStatusService.waitForTerminal(
                commandResponse.getCommandId(),
                MODELING_POINTS_QUERY_TIMEOUT_MS);
        if (snapshot == null || !Boolean.TRUE.equals(snapshot.getTerminal())) {
            response.put("success", false);
            response.put("message", "\u8bbe\u5907\u79bb\u7ebf\u6216\u54cd\u5e94\u8d85\u65f6");
            response.put("data", null);
            return ResponseEntity.status(504).body(response);
        }
        if (!"SUCCEEDED".equals(snapshot.getStatus())) {
            response.put("success", false);
            response.put("message", snapshot.getMessage());
            response.put("data", null);
            return ResponseEntity.status(502).body(response);
        }

        Map<String, Object> resultData = extractCommandData(snapshot);
        if (resultData == null || !(resultData.get("routes") instanceof List)) {
            response.put("success", false);
            response.put("message", "robot saved routes response is invalid");
            response.put("data", null);
            return ResponseEntity.status(502).body(response);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productId", productId);
        data.put("serialNumber", deviceId);
        data.put("currentTaskName", resultData.get("currentTaskName"));
        data.put("routes", resultData.get("routes"));
        response.put("success", true);
        response.put("message", "\u83b7\u53d6\u5df2\u4fdd\u5b58\u8def\u7ebf\u6210\u529f");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tasks/current")
    public ResponseEntity<Map<String, Object>> selectRobotTask(
            @RequestBody Map<String, String> payload,
            HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        Integer roleId = (Integer) httpRequest.getAttribute("roleId");
        String username = (String) httpRequest.getAttribute("username");
        String productId = payload.get("productId");
        String taskName = payload.get("taskName");
        Map<String, Object> response = new LinkedHashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "not logged in");
            response.put("data", null);
            return ResponseEntity.status(401).body(response);
        }
        if (productId == null || !productId.matches("\\d{6}")
                || taskName == null || taskName.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "productId and taskName are required");
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        }

        String deviceId = "-T01" + productId;
        if (!vehicleService.hasDeviceAccess(userId, roleId, deviceId)) {
            response.put("success", false);
            response.put("message", "permission denied");
            response.put("data", null);
            return ResponseEntity.status(403).body(response);
        }

        TRailcarControlResponse commandResponse = sendTaskCommand(
                productId,
                "set_current_task",
                taskName.trim(),
                userId,
                username);
        if (!Boolean.TRUE.equals(commandResponse.getSuccess())) {
            response.put("success", false);
            response.put("message", commandResponse.getMessage());
            response.put("data", null);
            return ResponseEntity.status(502).body(response);
        }

        CommandStatusSnapshot snapshot = commandStatusService.waitForTerminal(
                commandResponse.getCommandId(),
                MODELING_POINTS_QUERY_TIMEOUT_MS);
        if (snapshot == null || !Boolean.TRUE.equals(snapshot.getTerminal())) {
            response.put("success", false);
            response.put("message", "robot response timeout");
            response.put("data", null);
            return ResponseEntity.status(504).body(response);
        }
        if (!"SUCCEEDED".equals(snapshot.getStatus())) {
            response.put("success", false);
            response.put("message", snapshot.getMessage());
            response.put("data", null);
            return ResponseEntity.status(409).body(response);
        }

        redisUtil.delete("task_path:" + deviceId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskName", taskName.trim());
        response.put("success", true);
        response.put("message", "\u8def\u7ebf\u9009\u62e9\u6210\u529f");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

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
