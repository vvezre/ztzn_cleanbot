package com.zt.cleanbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dto.TRailcarCommandRequest;
import com.zt.cleanbot.dto.TRailcarControlResponse;
import com.zt.cleanbot.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * T型号小车控制服务
 * 负责构建JSON格式的MQTT消息并发送
 */
@Slf4j
@Service
public class TRailcarControlService {

    private static final long DEFAULT_COMMAND_TIMEOUT_MS = 30_000L;

    @Autowired
    private MessageChannel mqttOutboundChannel;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CommandStatusService commandStatusService;

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 发送T型号小车控制命令
     * @param request 命令请求
     * @return 控制响应
     */
    public TRailcarControlResponse sendCommand(TRailcarCommandRequest request) {
        String deviceId = request.getFullDeviceId();
        String command = request.getCommand();
        String traceId = normalizeId(request.getTraceId(), "trace");
        String commandId = normalizeId(request.getCommandId(), "cmd");
        request.setTraceId(traceId);
        request.setCommandId(commandId);
        commandStatusService.initializeCommand(
                commandId,
                traceId,
                deviceId,
                "T_PYTHON",
                command,
                request.getUsername(),
                DEFAULT_COMMAND_TIMEOUT_MS,
                buildCommandDetail(request));

        String validationError = validateCommand(command, request.getParams());
        if (validationError != null) {
            commandStatusService.markFailed(commandId, validationError, buildStatusDetail(request, "FAILED"));
            return TRailcarControlResponse.failure(deviceId, command, validationError, commandId, traceId);
        }

        try {
            if ("get_modeling_path".equals(command) && request.getParams() != null) {
                Object modelId = request.getParams().get("modelId");
                if (modelId != null && !String.valueOf(modelId).trim().isEmpty()) {
                    redisUtil.delete("modeling_path:" + deviceId + ":" + String.valueOf(modelId));
                }
            }
            log.info("准备发送T型号小车控制命令 - traceId: {}, commandId: {}, 设备: {}, 命令: {}, 参数: {}",
                    traceId, commandId, deviceId, command, request.getParams());

            // 1. 构建MQTT消息JSON
            Map<String, Object> mqttMessage = buildMqttMessage(request);
            String jsonPayload = objectMapper.writeValueAsString(mqttMessage);

            log.debug("MQTT消息JSON: {}", jsonPayload);

            // 2. 发送到MQTT Broker
            String topic = request.getPublishTopic();
            byte[] payload = jsonPayload.getBytes(StandardCharsets.UTF_8);

            Message<byte[]> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, 1) // QoS 1 确保到达
                    .build();

            boolean sent = mqttOutboundChannel.send(message);

            if (sent) {
                commandStatusService.markDispatched(commandId, "MQTT消息已发送", buildStatusDetail(request, "DISPATCHED"));
                log.info("T型号小车控制命令发送成功 - 主题: {}, 命令: {}, 长度: {} 字节",
                        topic, command, payload.length);

                // TODO: 保存审计日志到数据库
                Long operationId = null; // 从审计日志服务获取

                return TRailcarControlResponse.success(deviceId, command, topic, operationId, commandId, traceId);
            } else {
                commandStatusService.markFailed(commandId, "MQTT消息发送失败", buildStatusDetail(request, "FAILED"));
                log.error("T型号小车控制命令发送失败 - 主题: {}, 命令: {}", topic, command);
                return TRailcarControlResponse.failure(deviceId, command, "MQTT消息发送失败", commandId, traceId);
            }

        } catch (Exception e) {
            commandStatusService.markFailed(commandId, "服务器内部错误: " + e.getMessage(), buildStatusDetail(request, "FAILED"));
            log.error("发送T型号小车控制命令异常 - 设备: {}, 命令: {}", deviceId, command, e);
            return TRailcarControlResponse.failure(deviceId, command, "服务器内部错误: " + e.getMessage(), commandId, traceId);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedTaskPath(String productId) {
        String deviceId = "-T01" + productId;
        Object cached = redisUtil.get("task_path:" + deviceId);
        if (cached == null) {
            return null;
        }
        if (cached instanceof Map) {
            return (Map<String, Object>) cached;
        }
        return objectMapper.convertValue(cached, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedModelingPath(String productId, String modelId) {
        String deviceId = "-T01" + productId;
        Object cached = redisUtil.get("modeling_path:" + deviceId + ":" + modelId);
        if (cached == null) {
            return null;
        }
        if (cached instanceof Map) {
            return (Map<String, Object>) cached;
        }
        return objectMapper.convertValue(cached, Map.class);
    }

    /**
     * 构建MQTT消息JSON对象
     * 格式：
     * {
     *   "company_code": "ZTZN-PVC",
     *   "product_model": "-T01",
     *   "product_id": "250001",
     *   "timestamp": 1704067200,
     *   "data": {
     *     "command": "drive",
     *     "params": { ... }
     *   }
     * }
     */
    private Map<String, Object> buildMqttMessage(TRailcarCommandRequest request) {
        Map<String, Object> message = new HashMap<>();
        message.put("company_code", request.getCompanyCode());
        message.put("product_model", request.getProductModel());
        message.put("product_id", request.getProductId());
        message.put("timestamp", System.currentTimeMillis() / 1000); // Unix时间戳（秒）

        // 构建data对象
        Map<String, Object> data = new HashMap<>();
        data.put("command_id", request.getCommandId());
        data.put("trace_id", request.getTraceId());
        data.put("command", request.getCommand());

        // 如果有参数，添加params字段
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            data.put("params", request.getParams());
        } else {
            data.put("params", new HashMap<>());
        }

        message.put("data", data);

        return message;
    }

    private String normalizeId(String value, String prefix) {
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private Map<String, Object> buildCommandDetail(TRailcarCommandRequest request) {
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("deviceId", request.getFullDeviceId());
        detail.put("deviceType", "T_PYTHON");
        detail.put("action", request.getCommand());
        detail.put("mqttTopic", request.getPublishTopic());
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            detail.put("params", request.getParams());
        }
        return detail;
    }

    private Map<String, Object> buildStatusDetail(TRailcarCommandRequest request, String status) {
        Map<String, Object> detail = buildCommandDetail(request);
        detail.put("traceId", request.getTraceId());
        detail.put("status", status);
        return detail;
    }

    /**
     * 验证命令参数
     * @param command 命令名称
     * @param params 参数
     * @return 错误消息，null表示验证通过
     */
    public String validateCommand(String command, Map<String, Object> params) {
        if (command == null || command.trim().isEmpty()) {
            return "命令名称不能为空";
        }

        // 根据不同命令验证参数
        if ("create_task".equals(command)) {
            Object taskName = params == null ? null : params.get("taskName");
            if (taskName == null || String.valueOf(taskName).trim().isEmpty()) {
                return "create_task command requires non-empty taskName";
            }
            Object areaList = params.get("areaList");
            if (!(areaList instanceof Iterable)) {
                return "create_task command requires iterable areaList";
            }
        }

        if ("select_task".equals(command) || "save_task".equals(command) || "set_current_task".equals(command)) {
            Object taskName = params == null ? null : params.get("taskName");
            if (taskName == null || String.valueOf(taskName).trim().isEmpty()) {
                return command + " command requires non-empty taskName";
            }
        }

        if ("get_modeling_path".equals(command)) {
            Object modelId = params == null ? null : params.get("modelId");
            if (modelId != null && !String.valueOf(modelId).matches("[A-Za-z0-9_-]+")) {
                return "get_modeling_path command requires valid modelId when provided";
            }
        }

        if ("get_modeling_points".equals(command)
                || "get_modeling_link_points".equals(command)
                || "get_modeling_result".equals(command)) {
            Object modelId = params == null ? null : params.get("modelId");
            if (modelId != null && !String.valueOf(modelId).matches("[A-Za-z0-9_-]+")) {
                return command + " command requires valid modelId when provided";
            }
        }

        if ("sample_modeling_point".equals(command)) {
            Object modelId = params == null ? null : params.get("modelId");
            Object groupId = params == null ? null : params.get("groupId");
            if ((modelId == null) != (groupId == null)) {
                return "sample_modeling_point command requires both modelId and groupId when identifiers are provided";
            }
            if (modelId != null && (!String.valueOf(modelId).matches("[A-Za-z0-9_-]+")
                    || !String.valueOf(groupId).matches("[A-Za-z0-9_-]+"))) {
                return "sample_modeling_point command identifiers are invalid";
            }
        }

        if ("sample_modeling_link_point".equals(command)) {
            Object modelId = params == null ? null : params.get("modelId");
            Object linkId = params == null ? null : params.get("linkId");
            if ((modelId == null) != (linkId == null)) {
                return "sample_modeling_link_point command requires both modelId and linkId when identifiers are provided";
            }
            if (modelId != null && (!String.valueOf(modelId).matches("[A-Za-z0-9_-]+")
                    || !String.valueOf(linkId).matches("[A-Za-z0-9_-]+"))) {
                return "sample_modeling_link_point command identifiers are invalid";
            }
        }

        if ("undo_modeling_point".equals(command) || "clear_modeling_points".equals(command)) {
            Object pointType = params == null ? null : params.get("pointType");
            if (pointType != null
                    && !"area".equals(String.valueOf(pointType))
                    && !"link".equals(String.valueOf(pointType))) {
                return command + " command pointType must be area or link";
            }
        }

        if ("delete_modeling_point".equals(command)
                || "delete_modeling_link_point".equals(command)) {
            Object pointId = params == null ? null : params.get("id");
            if (pointId == null
                    || !String.valueOf(pointId).matches("[A-Za-z0-9_-]+")) {
                return command + " command requires valid id";
            }
        }

        switch (command) {
            case "drive":
            case "back":
                // distance和speed是可选参数，不强制校验
                break;

            case "turn_left":
            case "turn_right":
                // angle是可选参数，不强制校验
                break;

            case "joystick_move":
                if (params == null) {
                    return "joystick_move命令需要参数：distance, dirX, dirY";
                }
                if (!params.containsKey("distance") || !params.containsKey("dirX") || !params.containsKey("dirY")) {
                    return "joystick_move命令需要参数：distance, dirX, dirY";
                }
                break;

            case "adjust_speed":
            case "adjust_brush_speed":
                if (params == null || !params.containsKey("speed")) {
                    return command + "命令需要参数：speed";
                }
                break;

            case "toggle_tracking":
                if (params == null || !params.containsKey("tracking")) {
                    return "toggle_tracking命令需要参数：tracking";
                }
                break;

            case "toggle_path_planning":
                if (params == null || !params.containsKey("path")) {
                    return "toggle_path_planning命令需要参数：path";
                }
                break;

            case "set_garage_entry":
                if (params == null || !params.containsKey("lat") || !params.containsKey("lon")) {
                    return "set_garage_entry命令需要参数：lat, lon";
                }
                break;

            case "create_task":
                if (params == null) {
                    return "create_task命令需要任务参数";
                }
                String[] requiredFields = {"taskName", "areaList"};
                for (String field : requiredFields) {
                    if (!params.containsKey(field)) {
                        return "create_task命令缺少必需参数：" + field;
                    }
                }
                break;

            case "select_task":
            case "save_task":
            case "set_current_task":
                if (params == null || !params.containsKey("taskName")) {
                    return command + "命令需要参数：taskName";
                }
                break;

            case "sample_modeling_point":
                // modelId and groupId are validated above.
                break;

            case "sample_modeling_link_point":
                // modelId and linkId are validated above.
                break;

            case "start_modeling":
            case "finish_modeling":
            case "get_modeling_state":
            case "undo_modeling_point":
            case "delete_modeling_point":
            case "delete_modeling_link_point":
            case "clear_modeling_points":
            case "clear_modeling_area_points":
            case "clear_modeling_link_points":
                break;

            case "save_params":
                if (params == null) {
                    return "save_params命令需要任务参数";
                }
                String[] requiredParamFields = {
                        "goBackLen",
                        "goLeftOrRightBackLen",
                        "turnBackLen",
                        "panelWidth",
                        "panelHeight",
                        "leftOrRightBridgeLen",
                        "voltageWarn",
                        "heading",
                        "startLat",
                        "startLon",
                        "garageEntryLat",
                        "garageEntryLon",
                        "chargingPileLat",
                        "chargingPileLon",
                        "startToChargingPilePointLength",
                        "lastTaskBackLength",
                        "panelAngle",
                        "panelAngleX",
                        "gap",
                        "gapX",
                        "gapY",
                        "originHeading"
                };
                for (String field : requiredParamFields) {
                    if (!params.containsKey(field)) {
                        return "save_params命令缺少必需参数：" + field;
                    }
                }
                break;

            case "stop":
            case "parking":
            case "auto_drive":
            case "go_on":
            case "return_to_point":
            case "enter_garage":
            case "exit_garage":
            case "get_status":
            case "get_task_path":
            case "get_modeling_path":
            case "get_modeling_points":
            case "get_modeling_link_points":
            case "get_modeling_result":
                // 这些命令不需要参数
                break;

            default:
                return "不支持的命令：" + command;
        }

        return null; // 验证通过
    }
}
