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
 * T 型号小车的“HTTP 命令 -> MQTT 命令”转换服务。
 *
 * <p>完整调用链如下：</p>
 * <ol>
 *   <li>前端调用 {@code POST /api/t-railcar/command}，提交产品编号、命令名和参数；</li>
 *   <li>Controller 完成登录用户及设备权限检查后，把请求交给本服务；</li>
 *   <li>本服务生成 {@code commandId/traceId}，建立一条初始命令状态记录；</li>
 *   <li>把 HTTP 字段转换成 FSM 能识别的 MQTT JSON，并发布到该小车的专属主题；</li>
 *   <li>发布成功后只把状态标记为 {@code DISPATCHED}，等待小车异步返回 ACK 和执行结果；</li>
 *   <li>小车回包由 {@link RailcarMessageService} 接收，并用同一个 {@code commandId}
 *       更新成 {@code ACCEPTED/RUNNING/SUCCEEDED/FAILED}。</li>
 * </ol>
 *
 * <p>因此，“HTTP 返回 success=true”只说明云平台已经把命令发出，不能等同于机器人已经执行成功。
 * 前端如需最终点位、保存结果或启动结果，必须继续查询命令状态。</p>
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
     * 校验、登记并发布一条 T 型号小车命令。
     *
     * <p>request 中最重要的业务字段：</p>
     * <ul>
     *   <li>{@code productId}：设备产品编号，例如 {@code 250001}；</li>
     *   <li>{@code command}：FSM 命令，例如 {@code start_modeling}、
     *       {@code sample_modeling_point}、{@code save_modeling_task}；</li>
     *   <li>{@code params}：命令参数；没有参数时也会向小车发送空对象；</li>
     *   <li>{@code commandId}：一次命令的唯一编号；未提供时由云平台生成；</li>
     *   <li>{@code traceId}：跨 HTTP、MQTT、FSM 日志追踪同一次请求的编号。</li>
     * </ul>
     *
     * @param request 已通过 Controller 基础校验的命令请求
     * @return 发布受理结果；成功时包含 commandId，但此时小车可能尚未开始执行
     */
    public TRailcarControlResponse sendCommand(TRailcarCommandRequest request) {
        // commandId 用于前端查询最终结果，traceId 用于跨 HTTP、MQTT 和小车日志排查同一次调用。
        String deviceId = request.getFullDeviceId();
        String command = request.getCommand();
        String traceId = normalizeId(request.getTraceId(), "trace");
        String commandId = normalizeId(request.getCommandId(), "cmd");
        request.setTraceId(traceId);
        request.setCommandId(commandId);
        // 先创建 PENDING 状态快照。即使后续参数校验或 MQTT 发布失败，前端仍能用
        // commandId 查到明确的失败原因，而不是只能看到一个没有上下文的 HTTP 错误。
        commandStatusService.initializeCommand(
                commandId,
                traceId,
                deviceId,
                "T_PYTHON",
                command,
                request.getUsername(),
                DEFAULT_COMMAND_TIMEOUT_MS,
                buildCommandDetail(request));

        // 云平台只校验协议层必需字段，避免把明显不完整的命令发送给小车。
        // 具体能否执行（是否正在建模、是否已有任务等）仍由 FSM 根据实时状态判断。
        String validationError = validateCommand(command, request.getParams());
        if (validationError != null) {
            commandStatusService.markFailed(commandId, validationError, buildStatusDetail(request, "FAILED"));
            return TRailcarControlResponse.failure(deviceId, command, validationError, commandId, traceId);
        }

        try {
            // 主动查询建模路径前删除同 modelId 的旧缓存，避免本次小车未回包时
            // Controller 错把上一轮的路径当成最新结果返回。
            if ("get_modeling_path".equals(command) && request.getParams() != null) {
                Object modelId = request.getParams().get("modelId");
                if (modelId != null && !String.valueOf(modelId).trim().isEmpty()) {
                    redisUtil.delete("modeling_path:" + deviceId + ":" + String.valueOf(modelId));
                }
            }
            log.info("准备发送T型号小车控制命令 - traceId: {}, commandId: {}, 设备: {}, 命令: {}, 参数: {}",
                    traceId, commandId, deviceId, command, request.getParams());

            // 1. 构建MQTT消息JSON
            // 把 HTTP 请求转换为小车 MQTT 命令 JSON。
            Map<String, Object> mqttMessage = buildMqttMessage(request);
            String jsonPayload = objectMapper.writeValueAsString(mqttMessage);

            log.debug("MQTT消息JSON: {}", jsonPayload);

            // 2. 发送到MQTT Broker
            // 每台设备都使用自己的 MQTT 命令主题，避免不同小车互相影响。
            String topic = request.getPublishTopic();
            byte[] payload = jsonPayload.getBytes(StandardCharsets.UTF_8);

            Message<byte[]> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    // QoS 1 表示 Broker 至少投递一次；commandId 也可用于识别同一命令。
                    .setHeader(MqttHeaders.QOS, 1)
                    .build();

            boolean sent = mqttOutboundChannel.send(message);

            if (sent) {
                // DISPATCHED 表示云平台已成功发送，不代表小车已执行完成。
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

    /**
     * 读取某台设备最近一次通过 {@code get_task_path} 回传的已选任务路径。
     * 缓存键按完整设备序列号隔离，避免多台小车之间串数据。
     */
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

    /**
     * 读取指定设备、指定 modelId 的建模规划结果。
     * modelId 是一次建模会话的编号，同一设备的多次建模不会共用一个缓存键。
     */
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
     *     "command_id": "cmd_xxx",
     *     "trace_id": "trace_xxx",
     *     "command": "drive",
     *     "params": { ... }
     *   }
     * }
     */
    private Map<String, Object> buildMqttMessage(TRailcarCommandRequest request) {
        // 外层字段用于确认消息属于哪家公司的哪一类、哪一台设备。
        Map<String, Object> message = new HashMap<>();
        message.put("company_code", request.getCompanyCode());
        message.put("product_model", request.getProductModel());
        message.put("product_id", request.getProductId());
        message.put("timestamp", System.currentTimeMillis() / 1000); // Unix时间戳（秒）

        // data 是 FSM 真正处理的命令主体。command_id 必须原样穿过小车回包，
        // 云平台才能把异步结果更新到前端拿到的那一条命令记录上。
        Map<String, Object> data = new HashMap<>();
        data.put("command_id", request.getCommandId());
        data.put("trace_id", request.getTraceId());
        data.put("command", request.getCommand());

        // 始终保留 params 对象，使无参命令与有参命令拥有一致的协议结构。
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            data.put("params", request.getParams());
        } else {
            data.put("params", new HashMap<>());
        }

        message.put("data", data);

        return message;
    }

    private String normalizeId(String value, String prefix) {
        // 支持调用方传入已有 ID；没有时生成短 UUID，便于日志阅读和接口传递。
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private Map<String, Object> buildCommandDetail(TRailcarCommandRequest request) {
        // detail 是状态查询接口中的业务上下文，不参与发给小车的 MQTT 协议。
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
     * 在发布 MQTT 前检查不同命令的最小必需参数。
     *
     * <p>这里不负责路线规划，也不判断机器人当前是否允许执行；它只保护通信协议：</p>
     * <ul>
     *   <li>保存/选择任务必须有非空 {@code taskName}；</li>
     *   <li>删除点位必须有格式安全的唯一 {@code id}；</li>
     *   <li>建模会话编号只允许字母、数字、下划线和短横线，避免非法缓存键；</li>
     *   <li>摇杆、调速等命令必须携带各自需要的控制参数。</li>
     * </ul>
     *
     * @param command FSM 命令名称
     * @param params 前端提交的命令参数，可为空
     * @return 验证失败时返回可读错误；返回 null 表示可以发送给小车
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

        if ("select_task".equals(command)
                || "save_task".equals(command)
                || "set_current_task".equals(command)
                || "save_modeling_task".equals(command)) {
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
            case "save_modeling_task":
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
            case "new_modeling_area":
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
            case "get_task_names":
            case "get_saved_routes":
                // 这些命令不需要参数
                break;

            default:
                return "不支持的命令：" + command;
        }

        return null; // 验证通过
    }
}
