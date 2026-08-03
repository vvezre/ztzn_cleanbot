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
 * T 型清扫机器人的前端 HTTP 入口。
 *
 * 该控制器不执行机器人算法，主要负责：
 * 1. 从登录 Token 中取得用户与角色。
 * 2. 检查用户是否有权访问 productId 对应的小车。
 * 3. 将 HTTP 请求转换为 MQTT 命令发给小车 FSM。
 * 4. 根据 commandId 等待小车最终结果。
 * 5. 将小车内部数据整理为前端约定的 success/message/data 结构。
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

    /**
     * 前端控制命令的统一入口。
     * 云平台只负责登录权限校验和 MQTT 转发，具体建模与运动由小车 FSM 执行。
     *
     * 请求示例：
     * {"productId":"250001","command":"sample_modeling_point","params":{}}
     *
     * 该接口是异步命令入口。HTTP 200 只代表 MQTT 已发送，返回中的
     * commandStatus=DISPATCHED 不代表小车已完成。前端需使用返回的 commandId
     * 调用 GET /api/command-status/{commandId}，直到 SUCCEEDED 或 FAILED。
     *
     * @param request productId、command 和可选 params
     * @param httpRequest 由登录拦截器写入 userId/roleId/username
     * @return MQTT 发送状态、commandId、traceId 和设备主题
     */
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

        // 命令发送成功只代表已进入 MQTT；最终执行结果由 commandId 串联。
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

    /**
     * 获取当前建模的统一绘图数据：区域点、连接点和规划路径点。
     *
     * 此接口是“表面同步、内部 MQTT”的查询：
     * 1. 云平台向小车发送 get_modeling_result。
     * 2. 最多等待 10 秒钟。
     * 3. 小车回复成功后，从 detail.result.data 提取数据。
     * 4. 直接向前端返回 data.areaPoints/data.linkPoints/data.pathPoints。
     *
     * areaPoints 和 linkPoints 是用户记录的点；pathPoints 是 FSM 已生成的机器人执行路径点。
     * 该接口不会在云平台重新计算路径。
     */
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

    /**
     * 查询当前建模会话中的全部区域边界点。
     *
     * 返回 data.points[]，每个点包含 id、name、sequence、x、y、lat、lon。
     * id 是删除点时使用的唯一键，sequence 是用户的记录顺序，x/y 单位为厘米。
     */
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

    /**
     * 查询当前建模会话中的全部跨区域连接点。
     *
     * 返回格式与区域点一致，但数据来自连接桥记录。
     * 连接点会按 sequence 组成跨区域移动路线，不强制只有两个点。
     */
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

    /**
     * 区域点和连接点的公共查询流程。
     *
     * 两个接口的差别只是发给小车的 command：
     * get_modeling_points 或 get_modeling_link_points。
     * 登录校验、设备权限校验、MQTT 等待和 points[] 提取都共用该方法，
     * 避免两个接口出现不一致的超时或错误处理。
     */
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

        // 发送查询命令。此时 commandResponse 仅能确认 MQTT 是否成功发送。
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

        // 使用 commandId 等待小车的 command_result，而不是把 DISPATCHED 当成最终成功。
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

        // 小车结果位于 snapshot.detail.result.data.points，对前端则直接返回为 points。
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
        // 逐层校验 Map/List 类型，避免小车版本或数据异常导致 ClassCastException。
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
        // 统一绘图接口要求三个列表必须同时存在，否则认为小车回复不完整。
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
        // 保存、查询和选择等同步接口共用的 detail.result.data 提取方法。
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

    /**
     * 将 finish_modeling 已生成的路径按用户输入的 taskName 保存在小车中。
     *
     * 请求：{"productId":"250001","taskName":"路线1"}
     *
     * 云平台发送 save_modeling_task，并等待小车真正写入任务文件后才返回成功。
     * 成功返回 taskName、taskCount 和 modelId。如果路径尚未规划、名称非法或同名冲突，
     * 小车会返回失败，云平台用 HTTP 409 转达业务冲突。
     */
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

    /**
     * 根据产品编号查询小车已命名保存的全部路线和点位数据。
     *
     * 返回 data.productId、data.serialNumber、data.currentTaskName 和 data.routes[]。
     * routes[] 中每条路线包含名称以及 areaPoints/linkPoints/pathPoints，前端可直接用于路线列表和预览。
     *
     * 路线存储在小车端，所以该查询需要设备在线；10 秒内没有回复时返回 HTTP 504。
     */
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

    /**
     * 真正设置小车下一次 auto_drive 要执行的路线，不等同于前端页面高亮。
     *
     * 请求：{"productId":"250001","taskName":"路线1"}
     *
     * 小车端 set_current_task 成功后，会将路线配置同步为 config.json 和 Redis currentTaskName。
     * 只有完成该步骤，后续 auto_drive 才会执行该路线。
     * 云平台等待小车最终 SUCCEEDED 后才返回“路线选择成功”，并清理旧的路径缓存。
     */
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
