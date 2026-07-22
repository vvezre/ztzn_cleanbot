package com.zt.cleanbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dto.DeviceConfigRequest;
import com.zt.cleanbot.model.RailcarConfig;
import com.zt.cleanbot.model.RailcarMessage;
import com.zt.cleanbot.model.TaskLog;
import com.zt.cleanbot.model.Vehicle;
import com.zt.cleanbot.model.VehicleLog;
import com.zt.cleanbot.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class RailcarMessageService {
    private static final Logger log = LoggerFactory.getLogger(RailcarMessageService.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private TaskLogService taskLogService;
    @Autowired
    private VehicleLogService vehicleLogService;

    @Autowired
    private DeviceStatusPublisher deviceStatusPublisher;

    @Autowired
    private MessageChannel mqttOutboundChannel;

    @Autowired
    private RailcarControlService railcarControlService;

    @Autowired
    private RailcarConfigService railcarConfigService;

    @Autowired
    private CommandStatusService commandStatusService;

    private static final long REDIS_EXPIRE_TIME = 24 * 60 * 60;
    private static final long VEHICLE_EXISTENCE_CHECK_INTERVAL_MS = 60_000L;
    private static final long VEHICLE_METADATA_REFRESH_INTERVAL_MS = 60_000L;
    private static final long LAST_OPERATION_MODE_EXPIRE_SECONDS = 7 * 24 * 60 * 60L;
    private static final long INTERACTION_RELAY_DISPATCH_INTERVAL_MS = 120L;
    private static final long INTERACTION_RELAY_STATE_EXPIRE_MS = 30_000L;
    private static final long INTERACTION_RELAY_NO_INPUT_EXPIRE_MS = 2_000L;
    private final Map<String, Long> vehicleExistenceCheckCache = new ConcurrentHashMap<String, Long>();
    private final Map<String, Long> vehicleMetadataRefreshCache = new ConcurrentHashMap<String, Long>();
    private final Map<String, PendingInteractionRelay> pendingInteractionRelayCache =
            new ConcurrentHashMap<String, PendingInteractionRelay>();
    public RailcarMessageService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**


     */
    public void handleRailcarStatus(String serialNumber, String payload) {
        try {
            log.info("Railcar info event", serialNumber, payload);


            ensureVehicleExists(serialNumber);


            String hexString = payload.replaceAll(" ", "").toUpperCase();

            if (isBindingRelayEchoFrame(hexString)) {
                log.warn("Railcar warning event",
                        serialNumber);
                return;
            }

            log.info("Railcar info event", hexString, hexString.length());


            RailcarMessage railcarMessage = parseRailcarStatus(hexString);

            if (railcarMessage != null) {

                railcarMessage.setSerialNumber(serialNumber);


                String payloadSerial = (railcarMessage.getProductModel() != null
                        ? railcarMessage.getProductModel().trim()
                        : "")
                        + (railcarMessage.getProductNumber() != null ? railcarMessage.getProductNumber().trim() : "");

                log.info("Railcar info event",
                        serialNumber, payloadSerial, railcarMessage.getProductModel(),
                        railcarMessage.getProductNumber());


                if (!serialNumber.equals(payloadSerial)) {
                    log.warn("identity mismatch, topic={}, payload={}; send correction", serialNumber, payloadSerial);
                    sendIdentityCorrectionMessage(serialNumber, hexString);
                }


                relayInteractionIfNeeded(serialNumber, railcarMessage);


                if (log.isDebugEnabled()) {
                    logRailcarStatusDetails(railcarMessage);
                }


                checkAndSaveVehicle(railcarMessage);

                updateRailcarStatusToRedis(railcarMessage);


                handleStatusChangeAndTask(railcarMessage);


            } else {
                log.error("Railcar error event", serialNumber, payload);
            }

        } catch (Exception e) {
            log.error("Railcar error event", serialNumber, payload, e);
        }
    }

    /**


     */
    public void handleTRailcarStatus(String serialNumber, String payload) {
        try {
            log.info("Railcar info event", serialNumber, payload);

            ensureVehicleExists(serialNumber);

            JsonNode root = objectMapper.readTree(payload);
            JsonNode dataNode = root.path("data");
            String messageType = dataNode.path("type").asText("");

            if (messageType.isEmpty()) {
                log.warn("Railcar warning event", serialNumber, payload);
                return;
            }

            if ("vehicle_status".equals(messageType)) {
                JsonNode statusNode = dataNode.path("data");
                if (statusNode.isMissingNode() || statusNode.isNull()) {
                    log.warn("Railcar warning event", serialNumber);
                    return;
                }

                Map<String, Object> redisData = buildTRailcarRedisData(serialNumber, root, statusNode);
                boolean success = redisUtil.setVehicle(serialNumber, redisData, REDIS_EXPIRE_TIME, TimeUnit.SECONDS);

                if (success) {
                    log.info("Railcar info event",
                            serialNumber, redisData.get("battery"), redisData.get("status"));
                    deviceStatusPublisher.publishDeviceStatus(serialNumber, redisData);
                } else {
                    log.error("Railcar error event", serialNumber);
                }
                return;
            }

            if ("vehicle_position".equals(messageType)) {
                JsonNode positionNode = dataNode.path("data");
                if (positionNode.isMissingNode() || positionNode.isNull()) {
                    log.warn("Railcar position event has no data: {}", serialNumber);
                    return;
                }

                Integer localX = readInt(positionNode, "local_x");
                Integer localY = readInt(positionNode, "local_y");
                if (localX == null || localY == null) {
                    log.warn("Railcar position event has invalid coordinates: {}", serialNumber);
                    return;
                }

                Map<String, Object> redisData = getExistingRedisData(serialNumber);
                redisData.put("deviceId", serialNumber);
                redisData.put("localX", localX);
                redisData.put("localY", localY);
                redisData.put("lastUpdateTime", parseTRailcarUpdateTime(root, positionNode));

                boolean success = redisUtil.setVehicle(serialNumber, redisData, REDIS_EXPIRE_TIME, TimeUnit.SECONDS);
                if (success) {
                    deviceStatusPublisher.publishDeviceStatus(serialNumber, redisData);
                } else {
                    log.error("Railcar position event persistence failed: {}", serialNumber);
                }
                return;
            }

            if ("online".equals(messageType) || "offline".equals(messageType)) {
                Map<String, Object> redisData = getExistingRedisData(serialNumber);
                redisData.put("deviceId", serialNumber);
                redisData.put("status", "online".equals(messageType) ? "active" : "offline");
                redisData.put("onlineState", "online".equals(messageType) ? "ONLINE" : "OFFLINE");
                redisData.put("lastUpdateTime", parseTRailcarUpdateTime(root, dataNode.path("data")));
                redisData.put("mqttMessageType", messageType);

                boolean success = redisUtil.setVehicle(serialNumber, redisData, REDIS_EXPIRE_TIME, TimeUnit.SECONDS);
                if (success) {
                    log.info("Railcar info event", serialNumber, messageType);
                    deviceStatusPublisher.publishDeviceStatus(serialNumber, redisData);
                } else {
                    log.error("Railcar error event", serialNumber);
                }
                return;
            }

            if ("ack".equals(messageType) || "command_result".equals(messageType)) {
                Map<String, Object> redisData = getExistingRedisData(serialNumber);
                redisData.put("deviceId", serialNumber);
                redisData.put("mqttMessageType", messageType);
                redisData.put("lastUpdateTime", parseTRailcarUpdateTime(root, dataNode));
                applyCommandTracking(redisData, dataNode, messageType);
                updateCommandSnapshot(serialNumber, dataNode, messageType);
                cacheTaskPathIfPresent(serialNumber, dataNode, messageType);
                cacheModelingPathIfPresent(serialNumber, dataNode, messageType);

                boolean success = redisUtil.setVehicle(serialNumber, redisData, REDIS_EXPIRE_TIME, TimeUnit.SECONDS);
                if (success) {
                    log.info("Railcar info event",
                            serialNumber,
                            messageType,
                            redisData.get("lastCommandId"),
                            redisData.get("lastCommandStatus"));
                    deviceStatusPublisher.publishDeviceStatus(serialNumber, redisData);
                } else {
                    log.error("Railcar error event", serialNumber, messageType);
                }
                return;
            }

            log.info("Railcar info event", serialNumber, messageType);

        } catch (Exception e) {
            log.error("Railcar error event", serialNumber, payload, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getExistingRedisData(String serialNumber) {
        Object existing = redisUtil.getVehicle(serialNumber);
        if (existing == null) {
            return new HashMap<>();
        }
        if (existing instanceof Map) {
            return new HashMap<>((Map<String, Object>) existing);
        }
        return objectMapper.convertValue(existing, Map.class);
    }

    private Map<String, Object> buildTRailcarRedisData(String serialNumber, JsonNode root, JsonNode statusNode) {
        Map<String, Object> redisData = getExistingRedisData(serialNumber);

        redisData.put("deviceId", serialNumber);
        redisData.put("status", parseTRailcarStatusForRedis(statusNode));
        redisData.put("lastUpdateTime", parseTRailcarUpdateTime(root, statusNode));
        redisData.put("mqttMessageType", "vehicle_status");

        Double lat = readDouble(statusNode, "lat");
        Double lon = readDouble(statusNode, "lon");
        Map<String, Object> location = readObjectMap(statusNode, "location");
        if ((lat == null || lon == null) && location != null && !location.isEmpty()) {
            lat = readDouble(location, "lat");
            lon = readDouble(location, "lon");
        }
        if (lat != null) {
            redisData.put("lat", lat);
        }
        if (lon != null) {
            redisData.put("lon", lon);
        }
        if (lat != null && lon != null) {
            Map<String, Object> normalizedLocation = new HashMap<>();
            normalizedLocation.put("lat", lat);
            normalizedLocation.put("lon", lon);
            redisData.put("location", normalizedLocation);
        } else
        if (location != null && !location.isEmpty()) {
            redisData.put("location", location);
        }

        Integer speed = readInt(statusNode, "speed");
        if (speed != null) {
            redisData.put("speed", speed);
            redisData.put("walkSpeed", speed);
        }

        Integer brushSpeed = readInt(statusNode, "brush_speed");
        if (brushSpeed != null) {
            redisData.put("brushSpeed", brushSpeed);
        }

        Double battery = readDouble(statusNode, "battery");
        if (battery == null) {
            battery = readDouble(statusNode, "battery_percent");
        }
        if (battery != null) {
            redisData.put("battery", battery);
        }

        Double batteryRaw = readDouble(statusNode, "battery_raw");
        if (batteryRaw == null) {
            batteryRaw = readDouble(statusNode, "battery_percent_raw");
        }
        if (batteryRaw != null) {
            redisData.put("batteryRaw", batteryRaw);
        }

        Integer localX = readInt(statusNode, "local_x");
        if (localX != null) {
            redisData.put("localX", localX);
        }
        Integer localY = readInt(statusNode, "local_y");
        if (localY != null) {
            redisData.put("localY", localY);
        }

        Double voltage = readDouble(statusNode, "voltage");
        if (voltage != null) {
            redisData.put("voltage", voltage);
        }

        Double heading = readDouble(statusNode, "heading");
        if (heading != null) {
            redisData.put("heading", heading);
            redisData.put("angle", heading);
        } else {
            Double angle = readDouble(statusNode, "angle");
            if (angle != null) {
                redisData.put("angle", angle);
                redisData.put("heading", angle);
            }
        }

        String action = readText(statusNode, "action");
        if (action != null) {
            redisData.put("action", action);
        }

        String taskName = readText(statusNode, "task_name");
        if (taskName != null) {
            redisData.put("taskName", taskName);
        }

        Integer curTaskIndex = readInt(statusNode, "cur_task_index");
        if (curTaskIndex != null) {
            redisData.put("curTaskIndex", curTaskIndex);
        }

        Integer taskCount = readInt(statusNode, "task_count");
        if (taskCount != null) {
            redisData.put("taskCount", taskCount);
        }

        Map<String, Object> taskOrigin = readObjectMap(statusNode, "taskOrigin");
        if (taskOrigin != null && !taskOrigin.isEmpty()) {
            redisData.put("taskOrigin", taskOrigin);
        }

        Map<String, Object> currentLocation = readObjectMap(statusNode, "currentLocation");
        if (currentLocation != null && !currentLocation.isEmpty()) {
            redisData.put("currentLocation", currentLocation);
        }

        Double distanceToTaskOriginM = readDouble(statusNode, "distanceToTaskOriginM");
        if (distanceToTaskOriginM != null) {
            redisData.put("distanceToTaskOriginM", distanceToTaskOriginM);
        }

        Double taskOriginToleranceM = readDouble(statusNode, "taskOriginToleranceM");
        if (taskOriginToleranceM != null) {
            redisData.put("taskOriginToleranceM", taskOriginToleranceM);
        }

        Boolean isAtTaskOrigin = readBoolean(statusNode, "isAtTaskOrigin");
        if (isAtTaskOrigin != null) {
            redisData.put("isAtTaskOrigin", isAtTaskOrigin);
        }

        String onlineState = readText(statusNode, "online_state");
        if (onlineState != null) {
            redisData.put("onlineState", onlineState);
        }

        String missionState = readText(statusNode, "mission_state");
        if (missionState != null) {
            redisData.put("missionState", missionState);
        }

        String controlState = readText(statusNode, "control_state");
        if (controlState != null) {
            redisData.put("controlState", controlState);
        }

        String healthState = readText(statusNode, "health_state");
        if (healthState != null) {
            redisData.put("healthState", healthState);
        }

        String faultState = readText(statusNode, "fault_state");
        if (faultState != null) {
            redisData.put("faultState", faultState);
        }

        List<String> supportedActions = readStringList(statusNode, "supported_actions");
        if (supportedActions != null && !supportedActions.isEmpty()) {
            redisData.put("supportedActions", supportedActions);
        }

        List<String> supportedParams = readStringList(statusNode, "supported_params");
        if (supportedParams != null && !supportedParams.isEmpty()) {
            redisData.put("supportedParams", supportedParams);
        }

        List<String> supportedStatusFields = readStringList(statusNode, "supported_status_fields");
        if (supportedStatusFields != null && !supportedStatusFields.isEmpty()) {
            redisData.put("supportedStatusFields", supportedStatusFields);
        }

        Map<String, Object> shadowDetail = readObjectMap(statusNode, "detail");
        if (shadowDetail != null && !shadowDetail.isEmpty()) {
            redisData.put("shadowDetail", shadowDetail);
        }

        String companyCode = readText(root, "company_code");
        if (companyCode != null) {
            redisData.put("companyCode", companyCode);
        }
        String productModel = readText(root, "product_model");
        if (productModel != null) {
            redisData.put("productModel", productModel);
        }
        String productId = readText(root, "product_id");
        if (productId != null) {
            redisData.put("productId", productId);
        }

        Long payloadTimestamp = readLong(statusNode, "timestamp");
        if (payloadTimestamp == null) {
            payloadTimestamp = readLong(root, "timestamp");
        }
        if (payloadTimestamp != null) {
            redisData.put("timestamp", payloadTimestamp);
        }

        return redisData;
    }

    private String parseTRailcarStatusForRedis(JsonNode statusNode) {
        String status = readText(statusNode, "status");
        if (status != null) {
            return "disabled".equalsIgnoreCase(status) ? "active" : status;
        }

        String onlineState = readText(statusNode, "online_state");
        if ("OFFLINE".equalsIgnoreCase(onlineState)) {
            return "offline";
        }

        String missionState = readText(statusNode, "mission_state");
        if ("CHARGING".equalsIgnoreCase(missionState) || "DOCKING".equalsIgnoreCase(missionState)) {
            return "charging";
        }
        if ("RUNNING".equalsIgnoreCase(missionState) || "RETURNING".equalsIgnoreCase(missionState)) {
            return "working";
        }

        Boolean moveJudge = readBoolean(statusNode, "move_judge");
        Boolean enterGarage = readBoolean(statusNode, "enter_garage");

        if (Boolean.TRUE.equals(moveJudge)) {
            return "working";
        }
        if (Boolean.TRUE.equals(enterGarage)) {
            return "charging";
        }
        return "active";
    }

    private void applyCommandTracking(Map<String, Object> redisData, JsonNode dataNode, String messageType) {
        String commandId = readText(dataNode, "command_id");
        String traceId = readText(dataNode, "trace_id");
        String command = readText(dataNode, "command");
        String lastCommandStatus = "ack".equalsIgnoreCase(messageType) ? "ACCEPTED" : "UNKNOWN";
        String lastCommandMessage = readText(dataNode, "status");

        JsonNode resultNode = dataNode.path("result");
        if ("command_result".equals(messageType)) {
            boolean resultSuccess = resultNode.path("success").asBoolean(false);
            lastCommandStatus = resultSuccess ? "SUCCEEDED" : "FAILED";
            String resultMessage = readText(resultNode, "message");
            if (resultMessage != null) {
                lastCommandMessage = resultMessage;
            }
        }

        if (commandId != null) {
            redisData.put("lastCommandId", commandId);
        }
        if (traceId != null) {
            redisData.put("lastTraceId", traceId);
        }
        if (command != null) {
            redisData.put("lastCommand", command);
        }
        redisData.put("lastCommandStatus", lastCommandStatus);
        if (lastCommandMessage != null) {
            redisData.put("lastCommandMessage", lastCommandMessage);
        }
    }

    private void cacheTaskPathIfPresent(String serialNumber, JsonNode dataNode, String messageType) {
        if (!"command_result".equals(messageType)) {
            return;
        }
        String command = readText(dataNode, "command");
        if (!"get_task_path".equals(command)) {
            return;
        }
        JsonNode resultNode = dataNode.path("result");
        if (!resultNode.path("success").asBoolean(false)) {
            return;
        }
        JsonNode pathDataNode = resultNode.path("data");
        if (pathDataNode.isMissingNode() || pathDataNode.isNull()) {
            return;
        }

        Map<String, Object> cachedPath = objectMapper.convertValue(pathDataNode, Map.class);
        cachedPath.put("deviceId", serialNumber);
        if (!cachedPath.containsKey("updatedAt")) {
            cachedPath.put("updatedAt", System.currentTimeMillis());
        }
        redisUtil.set("task_path:" + serialNumber, cachedPath, 24, TimeUnit.HOURS);
    }

    private void cacheModelingPathIfPresent(String serialNumber, JsonNode dataNode, String messageType) {
        if (!"command_result".equals(messageType)) {
            return;
        }
        String command = readText(dataNode, "command");
        if (!"get_modeling_path".equals(command) && !"finish_modeling".equals(command)) {
            return;
        }
        JsonNode resultNode = dataNode.path("result");
        if (!resultNode.path("success").asBoolean(false)) {
            return;
        }
        JsonNode pathDataNode = resultNode.path("data");
        if (pathDataNode.isMissingNode() || pathDataNode.isNull()) {
            return;
        }
        String modelId = readText(pathDataNode, "modelId");
        if (modelId == null || !modelId.matches("[A-Za-z0-9_-]+")) {
            return;
        }

        Map<String, Object> cachedPath = objectMapper.convertValue(pathDataNode, Map.class);
        cachedPath.put("deviceId", serialNumber);
        if (!cachedPath.containsKey("updatedAt")) {
            cachedPath.put("updatedAt", System.currentTimeMillis());
        }
        redisUtil.set("modeling_path:" + serialNumber + ":" + modelId, cachedPath, 24, TimeUnit.HOURS);
    }

    private void updateCommandSnapshot(String serialNumber, JsonNode dataNode, String messageType) {
        String commandId = readText(dataNode, "command_id");
        if (commandId == null) {
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("deviceId", serialNumber);
        detail.put("deviceType", "T_PYTHON");
        putIfNotNull(detail, "traceId", readText(dataNode, "trace_id"));
        putIfNotNull(detail, "action", readText(dataNode, "command"));
        putIfNotNull(detail, "messageType", messageType);

        if ("ack".equals(messageType)) {
            String ackStatus = readText(dataNode, "status");
            String ackMessage = ackStatus != null ? ackStatus : "ACK_ACCEPTED";
            if ("running".equalsIgnoreCase(ackStatus)) {
                commandStatusService.markRunning(commandId, ackMessage, detail);
            } else {
                commandStatusService.markAccepted(commandId, ackMessage, detail);
            }
            return;
        }

        JsonNode resultNode = dataNode.path("result");
        boolean resultSuccess = resultNode.path("success").asBoolean(false);
        String resultMessage = readText(resultNode, "message");
        if (resultMessage == null) {
            resultMessage = resultSuccess ? "RESULT_OK" : "RESULT_FAILED";
        }
        detail.put("result", objectMapper.convertValue(resultNode, Map.class));
        if (resultSuccess) {
            commandStatusService.markSucceeded(commandId, resultMessage, detail);
        } else {
            commandStatusService.markFailed(commandId, resultMessage, detail);
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private long parseTRailcarUpdateTime(JsonNode root, JsonNode statusNode) {
        long tsSec = root.path("timestamp").asLong(0L);
        if (tsSec <= 0L) {
            tsSec = statusNode.path("timestamp").asLong(0L);
        }
        if (tsSec > 0L) {
            return tsSec * 1000L;
        }
        return System.currentTimeMillis();
    }

    private Double readDouble(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asDouble();
    }

    private Integer readInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asInt();
    }

    private Integer readInt(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean readBoolean(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asBoolean();
    }

    private String readText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return text;
    }

    private Double readDouble(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Long readLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asLong();
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !value.isArray()) {
            return null;
        }
        return objectMapper.convertValue(value, List.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObjectMap(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !value.isObject()) {
            return null;
        }
        return objectMapper.convertValue(value, Map.class);
    }

    private boolean shouldThrottleDeviceCheck(Map<String, Long> cache, String serialNumber, long intervalMs) {
        long now = System.currentTimeMillis();
        Long lastCheck = cache.get(serialNumber);
        if (lastCheck != null && now - lastCheck < intervalMs) {
            return true;
        }
        cache.put(serialNumber, now);
        return false;
    }

    /**



     */
    private void ensureVehicleExists(String serialNumber) {
        try {
            if (serialNumber == null || serialNumber.length() < 10) {
                log.warn("Railcar warning event", serialNumber);
                return;
            }
            if (shouldThrottleDeviceCheck(vehicleExistenceCheckCache, serialNumber, VEHICLE_EXISTENCE_CHECK_INTERVAL_MS)) {
                return;
            }


            Vehicle vehicle = vehicleService.getBySerialNumber(serialNumber);
            if (vehicle != null) {
                return;
            }

            log.warn("Railcar device not scanned; skip auto-create from MQTT status - serialNumber={}", serialNumber);

        } catch (Exception e) {
            log.error("Railcar error event", serialNumber, e);
        }
    }

    /**


     *


     */
    private void sendIdentityCorrectionMessage(String topicSerialNumber, String originalHex) {
        try {


            if (topicSerialNumber == null || topicSerialNumber.length() < 10) {
                log.error("Railcar error event", topicSerialNumber);
                return;
            }
            String correctProductType = topicSerialNumber.substring(0, 4); // -D01
            String correctProductId = topicSerialNumber.substring(4); // 250001



            String companyCode = null;
            Vehicle vehicle = vehicleService.getBySerialNumber(topicSerialNumber);
            if (vehicle != null && vehicle.getCompanyCode() != null) {
                companyCode = vehicle.getCompanyCode();
            } else if (originalHex.length() >= 16) {

                companyCode = hexToString(originalHex.substring(0, 16));
            }

            if (companyCode == null || companyCode.isEmpty()) {
                companyCode = "ZTZN-PVC";
            }




            DeviceConfigRequest correctionRequest = new DeviceConfigRequest();
            correctionRequest.setDeviceId(topicSerialNumber);
            correctionRequest.setModel(correctProductType);
            correctionRequest.setCompanyCode(companyCode);
            correctionRequest.setInfoCommandType(0);
            correctionRequest.setStartAddress(0);
            correctionRequest.setDataLength(35);
            correctionRequest.setBindStatus(resolveBindEnabledForRelay(topicSerialNumber));
            correctionRequest.setInteractionPayloadHex("00000000000000000000000000000000");
            fillRelayRequestFromConfig(correctionRequest);

            String correctionHex = railcarControlService.encodeControlCommand(correctionRequest);
            if (correctionHex == null || correctionHex.length() < 40) {
                log.error("identity correction encode failed - serialNumber={}, hex={}", topicSerialNumber, correctionHex);
                return;
            }
            correctionHex = correctionHex.substring(0, 36) + "0001" + correctionHex.substring(40);


            String topic = "RAILCAR/R/" + topicSerialNumber;
            byte[] payloadBytes = hexStringToByteArray(correctionHex);

            Message<byte[]> message = MessageBuilder
                    .withPayload(payloadBytes)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, 1)
                    .build();

            boolean sent = mqttOutboundChannel.send(message);

            if (sent) {
                log.info("Railcar info event",
                        topic, correctionHex, companyCode, correctProductType, correctProductId);
            } else {
                log.error("Railcar error event", topic);
            }

        } catch (Exception e) {
            log.error("Railcar error event", topicSerialNumber, e);
        }
    }

    /**

     */
    private String stringToHex(String str, int byteLength) {
        StringBuilder sb = new StringBuilder();
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int i = 0; i < byteLength; i++) {
            if (i < bytes.length) {
                sb.append(String.format("%02X", bytes[i]));
            } else {
                sb.append("00");
            }
        }
        return sb.toString();
    }

    /**

     */
    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**


     */
    private String extractCurrentOperationMode(RailcarMessage message) {
        try {
            int modeValue = Integer.parseInt(message.getOperationMode(), 16);
            switch (modeValue) {
                case 1:
                case 4:
                    return "cleaning";
                case 5:
                    return "manual_go";
                case 0:
                case 2:
                case 3:
                default:
                    return "emergency_stop";
            }
        } catch (Exception e) {
            log.error("extract current operation mode failed - operationMode={}", message.getOperationMode(), e);
            return "emergency_stop";
        }
    }
    private String getOperationModeDescription(String operationMode) {
        try {
            int modeValue = Integer.parseInt(operationMode, 16);
            switch (modeValue) {
                case 0:
                    return "INVALID";
                case 1:
                    return "AUTO_STARTING";
                case 2:
                    return "STOPPING";
                case 3:
                    return "RESETTING";
                case 4:
                    return "CONTINUOUS";
                case 5:
                    return "MANUAL";
                default:
                    return "UNKNOWN";
            }
        } catch (Exception e) {
            log.error("parse operation mode description failed - operationMode={}", operationMode, e);
            return "UNKNOWN";
        }
    }
    private String getOperationEnableDescription(String operationEnable) {
        try {
            int enableValue = Integer.parseInt(operationEnable, 16);
            switch (enableValue) {
                case 0:
                    return "INVALID";
                case 1:
                    return "LEFT_SINGLE";
                case 2:
                    return "LEFT_DOUBLE";
                case 3:
                    return "RIGHT_SINGLE";
                case 4:
                    return "RIGHT_DOUBLE";
                default:
                    return "UNKNOWN";
            }
        } catch (Exception e) {
            log.error("parse operation enable description failed - operationEnable={}", operationEnable, e);
            return "UNKNOWN";
        }
    }
    /**

     */
    private void handleStatusChangeAndTask(RailcarMessage message) {
        try {
            String serialNumber = message.getSerialNumber();
            String currentMode = extractCurrentOperationMode(message);
            String currentModeDescription = getOperationModeDescription(message.getOperationMode());
            String workModeDescription = message.getWorkModeDescription();

            log.info("Railcar info event",
                    workModeDescription, currentMode, currentModeDescription);

            String lastMode = getLastOperationModeFromDB(serialNumber);
            log.info("Railcar info event", lastMode);

            boolean isModeChanged = lastMode == null || !currentMode.equals(lastMode);
            if (isModeChanged) {
                log.info("Railcar info event", lastMode, currentMode, currentModeDescription);
                saveVehicleLog(message, currentMode);
                handleTaskLogic(message, currentModeDescription, lastMode);
            } else {
                log.debug("Railcar debug event", currentMode);
            }
            saveLastOperationModeToDB(serialNumber, currentMode);
        } catch (Exception e) {
            log.error("Railcar error event", message.getSerialNumber(), e);
        }
    }

    private void saveVehicleLog(RailcarMessage message, String currentMode) {
        try {
            VehicleLog vehicleLog = new VehicleLog();
            vehicleLog.setId(UUID.randomUUID().toString());
            vehicleLog.setVehicleId(message.getSerialNumber());
            vehicleLog.setLat(message.getLatitude());
            vehicleLog.setLon(message.getLongitude());
            vehicleLog.setBattery(message.getBatteryLevel() != null ? message.getBatteryLevel().floatValue() : null);
            vehicleLog.setTimestamp(new Date());
            vehicleLog.setCommandType(currentMode);

            boolean saved = vehicleLogService.save(vehicleLog);
            if (saved) {
                String modeDescription = getOperationModeDescription(message.getOperationMode());
                log.info("Railcar info event",
                        message.getSerialNumber(), currentMode, modeDescription);
            } else {
                log.error("Railcar error event",
                        message.getSerialNumber(), currentMode);
            }
        } catch (Exception e) {
            log.error("Railcar error event", message.getSerialNumber(), e);
        }
    }

    private void handleTaskLogic(RailcarMessage message, String currentModeDescription, String lastMode) {
        try {
            String currentMode = extractCurrentOperationMode(message);
            boolean isAutoStart = "cleaning".equals(currentMode) && !"cleaning".equals(lastMode);
            boolean isAutoStop = !"cleaning".equals(currentMode) && "cleaning".equals(lastMode);

            log.debug("Railcar debug event",
                    currentMode, lastMode, isAutoStart, isAutoStop);

            if (isAutoStart) {
                log.info("Railcar info event", message.getSerialNumber());
                createTask(message);
            }
            if (isAutoStop) {
                log.info("Railcar info event", message.getSerialNumber());
                completeTask(message);
            }
        } catch (Exception e) {
            log.error("Railcar error event", message.getSerialNumber(), e);
        }
    }
    private void createTask(RailcarMessage message) {
        try {
            TaskLog taskLog = new TaskLog();


            taskLog.setId(UUID.randomUUID().toString());
            taskLog.setVehicleId(message.getSerialNumber());
            taskLog.setStartTime(new Date());
            taskLog.setStatus("working");
            taskLog.setTaskType("auto_mission");


            taskLog.setDroneId(null);
            taskLog.setAreaId(null);
            taskLog.setRouteId(null);
            taskLog.setEndTime(null);
            taskLog.setCleaningArea(null);
            taskLog.setEfficiency(null);
            taskLog.setPowerRestored(null);
            taskLog.setCleaningMode(null);


            boolean success = taskLogService.saveOrUpdateTaskLog(taskLog);
            if (success) {
                log.info("Railcar info event", message.getSerialNumber(), taskLog.getId());


                String taskKey = "railcar:" + message.getSerialNumber() + ":currentTask";
                redisUtil.set(taskKey, taskLog.getId(), REDIS_EXPIRE_TIME);
            } else {
                log.error("Railcar error event", message.getSerialNumber());
            }

        } catch (Exception e) {
            log.error("Railcar error event", message.getSerialNumber(), e);
        }
    }

    /**

     */
    private void completeTask(RailcarMessage message) {
        try {
            String serialNumber = message.getSerialNumber();


            String taskKey = "railcar:" + serialNumber + ":currentTask";
            String taskId = (String) redisUtil.get(taskKey);

            if (taskId == null) {

                TaskLog latestWorkingTask = taskLogService.getLatestWorkingTask(serialNumber, "auto_mission");
                if (latestWorkingTask != null) {
                    taskId = latestWorkingTask.getId();
                }
            }

            if (taskId != null) {

                TaskLog taskLog = new TaskLog();
                taskLog.setId(taskId);
                taskLog.setEndTime(new Date());
                taskLog.setStatus("success");


                if (message.getSingleRunDistance() != null) {

                    float cleaningArea = message.getSingleRunDistance().floatValue() * 1000;
                    taskLog.setCleaningArea(cleaningArea);
                }


                boolean success = taskLogService.saveOrUpdateTaskLog(taskLog);
                if (success) {
                    log.info("Railcar info event",
                            serialNumber, taskId, taskLog.getCleaningArea());


                    redisUtil.delete(taskKey);
                } else {
                    log.error("Railcar error event", serialNumber, taskId);
                }
            } else {
                log.warn("Railcar warning event", serialNumber);
            }

        } catch (Exception e) {
            log.error("Railcar error event", message.getSerialNumber(), e);
        }
    }

    /**


     */
    private void checkAndSaveVehicle(RailcarMessage message) {
        try {
            String serialNumber = message.getSerialNumber();
            if (serialNumber == null || serialNumber.trim().isEmpty()) {
                return;
            }
            if (shouldThrottleDeviceCheck(vehicleMetadataRefreshCache, serialNumber, VEHICLE_METADATA_REFRESH_INTERVAL_MS)) {
                return;
            }

            Vehicle existVehicle = vehicleService.getBySerialNumber(serialNumber);
            if (existVehicle == null) {

                log.warn("Railcar warning event", serialNumber);
                return;
            }


            boolean needUpdate = false;

            if (existVehicle.getCompanyCode() == null && message.getCompanyCode() != null) {
                existVehicle.setCompanyCode(message.getCompanyCode().trim());
                needUpdate = true;
            }

            if (needUpdate) {
                vehicleService.updateById(existVehicle);
                log.info("Railcar info event", serialNumber, existVehicle.getCompanyCode());
            }

        } catch (Exception e) {
            log.error("Railcar error event", message.getSerialNumber(), e);
        }
    }

    /**

     */
    private String getDeviceModelName(String productType) {
        if (productType == null) {
            return "UNKNOWN_MODEL";
        }
        switch (productType) {
            case "-D01":
                return "D01_BASIC";
            case "-D11":
                return "D11_TORQUE";
            case "-D12":
                return "D12_DOCK";
            case "-D21":
                return "D21_BRIDGE";
            case "-T01":
                return "T01_BASIC";
            case "-T11":
                return "T11_UNATTENDED";
            case "-T12":
                return "T12_CHARGER";
            case "-T21":
                return "T21_GUARDED";
            default:
                return "UNKNOWN_MODEL";
        }
    }
    /**

     */
    private String parseWorkMode(String hex) {
        try {
            int value = Integer.parseInt(hex, 16);
            switch (value) {
                case 0:
                    return "INVALID";
                case 1:
                    return "DAILY";
                case 2:
                    return "MONTHLY";
                case 3:
                    return "YEARLY";
                case 4:
                    return "WEEKLY";
                default:
                    return "UNKNOWN_WORK_MODE_" + value;
            }
        } catch (Exception e) {
            return "PARSE_FAILED_" + hex;
        }
    }
    private String parseOperationMode(String hex) {
        try {
            int value = Integer.parseInt(hex, 16);
            switch (value) {
                case 0:
                    return "INVALID";
                case 1:
                    return "AUTO_STARTING";
                case 2:
                    return "STOPPING";
                case 3:
                    return "RESETTING";
                case 4:
                    return "CONTINUOUS";
                case 5:
                    return "MANUAL";
                default:
                    return "UNKNOWN_OPERATION_MODE_" + value;
            }
        } catch (Exception e) {
            return "PARSE_FAILED_" + hex;
        }
    }

    private String parseD12EnableMode(String hex) {
        try {
            int value = Integer.parseInt(hex, 16);
            switch (value) {
                case 0:
                    return "INVALID";
                case 1:
                    return "LEFT";
                case 2:
                    return "RIGHT";
                case 3:
                    return "LEFT_RIGHT";
                default:
                    return "UNKNOWN_D12_ENABLE_" + value;
            }
        } catch (Exception e) {
            return "PARSE_FAILED_" + hex;
        }
    }
    private String parseOperationEnable(String hex) {
        try {
            int value = Integer.parseInt(hex, 16);
            switch (value) {
                case 0:
                    return "INVALID";
                case 1:
                    return "LEFT_SINGLE";
                case 2:
                    return "LEFT_DOUBLE";
                case 3:
                    return "RIGHT_SINGLE";
                case 4:
                    return "RIGHT_DOUBLE";
                default:
                    return "UNKNOWN_ENABLE_MODE_" + value;
            }
        } catch (Exception e) {
            return "PARSE_FAILED_" + hex;
        }
    }
    private RailcarMessage parseRailcarStatus(String hexString) {
        try {
            if (hexString == null || hexString.length() != 140) {
                log.error("invalid D-frame length - actual={}, expected=140",
                        hexString != null ? hexString.length() : "null");
                return null;
            }

            String productModelHex = hexString.substring(16, 24);
            String productModel = hexToString(productModelHex);
            log.info("select railcar parser by model: {}", productModel);

            if ("-D12".equals(productModel) || "-T12".equals(productModel)) {
                log.info("use D12 status parser");
                return parseRailcarStatusD12(hexString);
            }

            log.info("use standard D status parser");
            return parseRailcarStatusStandard(hexString);
        } catch (Exception e) {
            log.error("parse D-frame failed - hexString={}", hexString, e);
            return null;
        }
    }
    /**

     */
    private RailcarMessage parseRailcarStatusStandard(String hexString) {
        try {
            RailcarMessage message = new RailcarMessage();
            message.setTimestamp(new Date());
            message.setRawData(hexString);

            int index = 0;
            log.info("parse standard D status frame, len={}", hexString.length());

            String companyCodeHex = hexString.substring(index, index + 16);
            message.setCompanyCode(hexToString(companyCodeHex));
            index += 16;

            String productModelHex = hexString.substring(index, index + 8);
            message.setProductModel(hexToString(productModelHex));
            index += 8;

            String productNumberHex = hexString.substring(index, index + 12);
            message.setProductNumber(hexToString(productNumberHex));
            message.setDeviceId(message.getProductNumber());
            index += 12;

            String singleRunTimeHex = hexString.substring(index, index + 4);
            message.setSingleRunTime(parseTwoByteBCDStandard(singleRunTimeHex, 1.0));
            index += 4;

            String totalRunTimeHex = hexString.substring(index, index + 8);
            message.setTotalRunTime(parseTwoByteBCDStandard(totalRunTimeHex.substring(0, 4), 1.0));
            index += 8;

            String singleRunDistanceHex = hexString.substring(index, index + 4);
            message.setSingleRunDistance(parseBcdDistance2Bytes(singleRunDistanceHex));
            index += 4;

            String totalRunDistanceHex = hexString.substring(index, index + 8);
            message.setTotalRunDistance(parseBcdDistance4Bytes(totalRunDistanceHex));
            index += 8;

            String longitudeHex = hexString.substring(index, index + 8);
            message.setLongitude(parseGpsCoordinate(longitudeHex));
            index += 8;

            String latitudeHex = hexString.substring(index, index + 8);
            message.setLatitude(parseGpsCoordinate(latitudeHex));
            index += 8;

            String bindSendEnableHex = hexString.substring(index, index + 4);
            message.setBindSendEnable(parseTwoByteBCDStandard(bindSendEnableHex, 1.0).intValue());
            index += 4;

            String interactionCommandHex = hexString.substring(index, index + 4);
            message.setInteractionCommand(parseTwoByteBCDStandard(interactionCommandHex, 1.0).intValue());
            StringBuilder interactionPayload = new StringBuilder(interactionCommandHex);
            index += 4;

            String backup1Hex = hexString.substring(index, index + 4);
            message.setBackup1(backup1Hex);
            interactionPayload.append(backup1Hex);
            index += 4;

            String backup2Hex = hexString.substring(index, index + 4);
            message.setBackup2(backup2Hex);
            interactionPayload.append(backup2Hex);
            index += 4;

            String operationModeHex = hexString.substring(index, index + 4);
            message.setOperationMode(operationModeHex);
            message.setOperationModeDescription(parseOperationMode(operationModeHex));
            interactionPayload.append(operationModeHex);
            index += 4;

            String operationEnableHex = hexString.substring(index, index + 4);
            message.setOperationEnable(operationEnableHex);
            message.setOperationEnableDescription(parseOperationEnable(operationEnableHex));
            interactionPayload.append(operationEnableHex);
            index += 4;

            String faultCodeHex = hexString.substring(index, index + 4);
            message.setFaultCode(parseTwoByteBCDStandard(faultCodeHex, 1.0).intValue());
            interactionPayload.append(faultCodeHex);
            index += 4;

            String currentRowPositionHex = hexString.substring(index, index + 4);
            message.setCurrentRowPosition(parseTwoByteBCDStandard(currentRowPositionHex, 1.0).intValue());
            interactionPayload.append(currentRowPositionHex);
            index += 4;

            String workCycleCountHex = hexString.substring(index, index + 4);
            Integer workCycleCount = parseTwoByteBCDStandard(workCycleCountHex, 1.0).intValue();
            message.setWorkCycleCount(workCycleCount);
            message.setWorkDataComplete(workCycleCount);
            interactionPayload.append(workCycleCountHex);
            message.setInteractionPayloadHex(interactionPayload.toString());
            index += 4;

            String speedHex = hexString.substring(index, index + 4);
            message.setCurrentSpeed(parseTwoByteBCDStandard(speedHex, 1.0));
            index += 4;

            String brushSpeedHex = hexString.substring(index, index + 4);
            message.setBrushSpeed(parseTwoByteBCDStandard(brushSpeedHex, 1.0));
            index += 4;

            String bridgeSpeedHex = hexString.substring(index, index + 4);
            message.setBridgeSpeed(parseTwoByteBCDStandard(bridgeSpeedHex, 1.0));
            index += 4;

            String heartbeatHex = hexString.substring(index, index + 4);
            message.setHeartbeat(parseTwoByteBCDStandard(heartbeatHex, 100.0));
            index += 4;

            String batteryHex = hexString.substring(index, index + 4);
            message.setBatteryLevel(parseTwoByteBCDStandard(batteryHex, 10.0));
            index += 4;

            message.setBackup3(hexString.substring(index, index + 4));
            index += 4;
            message.setBackup4(hexString.substring(index, index + 4));

            log.info("standard D status parsed");
            return message;
        } catch (Exception e) {
            log.error("parse standard D status failed - hexString={}", hexString, e);
            return null;
        }
    }
    private RailcarMessage parseRailcarStatusD12(String hexString) {
        try {
            RailcarMessage message = new RailcarMessage();
            message.setTimestamp(new Date());
            message.setRawData(hexString);

            int index = 0;
            log.info("parse D12 status frame, len={}", hexString.length());

            String companyCodeHex = hexString.substring(index, index + 16);
            message.setCompanyCode(hexToString(companyCodeHex));
            index += 16;

            String productModelHex = hexString.substring(index, index + 8);
            message.setProductModel(hexToString(productModelHex));
            index += 8;

            String productNumberHex = hexString.substring(index, index + 12);
            message.setProductNumber(hexToString(productNumberHex));
            message.setDeviceId(message.getProductNumber());
            index += 12;

            String singleRunTimeHex = hexString.substring(index, index + 4);
            message.setSingleRunTime(parseTwoByteBCDStandard(singleRunTimeHex, 1.0));
            index += 4;

            String totalRunTimeHex = hexString.substring(index, index + 8);
            message.setTotalRunTime(parseTwoByteBCDStandard(totalRunTimeHex.substring(0, 4), 1.0));
            index += 8;

            String singleRunDistanceHex = hexString.substring(index, index + 4);
            message.setSingleRunDistance(parseBcdDistance2Bytes(singleRunDistanceHex));
            index += 4;

            String totalRunDistanceHex = hexString.substring(index, index + 8);
            message.setTotalRunDistance(parseBcdDistance4Bytes(totalRunDistanceHex));
            index += 8;

            String longitudeHex = hexString.substring(index, index + 8);
            message.setLongitude(parseGpsCoordinate(longitudeHex));
            index += 8;

            String latitudeHex = hexString.substring(index, index + 8);
            message.setLatitude(parseGpsCoordinate(latitudeHex));
            index += 8;

            String bindSendEnableHex = hexString.substring(index, index + 4);
            message.setBindSendEnable(parseTwoByteBCDStandard(bindSendEnableHex, 1.0).intValue());
            index += 4;

            String interactionCommandHex = hexString.substring(index, index + 4);
            message.setInteractionCommand(parseTwoByteBCDStandard(interactionCommandHex, 1.0).intValue());
            StringBuilder interactionPayload = new StringBuilder(interactionCommandHex);
            index += 4;

            String backup1Hex = hexString.substring(index, index + 4);
            message.setBackup1(backup1Hex);
            interactionPayload.append(backup1Hex);
            index += 4;

            String backup2Hex = hexString.substring(index, index + 4);
            message.setBackup2(backup2Hex);
            interactionPayload.append(backup2Hex);
            index += 4;

            String operationModeHex = hexString.substring(index, index + 4);
            message.setOperationMode(operationModeHex);
            message.setOperationModeDescription(parseOperationMode(operationModeHex));
            interactionPayload.append(operationModeHex);
            index += 4;

            String operationEnableHex = hexString.substring(index, index + 4);
            message.setOperationEnable(operationEnableHex);
            message.setOperationEnableDescription(parseD12EnableMode(operationEnableHex));
            message.setD12WorkWay(operationEnableHex);
            message.setD12WorkWayDescription(parseD12EnableMode(operationEnableHex));
            interactionPayload.append(operationEnableHex);
            index += 4;

            String faultCodeHex = hexString.substring(index, index + 4);
            message.setFaultCode(parseTwoByteBCDStandard(faultCodeHex, 1.0).intValue());
            interactionPayload.append(faultCodeHex);
            index += 4;

            String currentRowPositionHex = hexString.substring(index, index + 4);
            message.setCurrentRowPosition(parseTwoByteBCDStandard(currentRowPositionHex, 1.0).intValue());
            interactionPayload.append(currentRowPositionHex);
            index += 4;

            String workCycleCountHex = hexString.substring(index, index + 4);
            Integer workCycleCount = parseTwoByteBCDStandard(workCycleCountHex, 1.0).intValue();
            message.setWorkCycleCount(workCycleCount);
            message.setWorkDataComplete(workCycleCount);
            interactionPayload.append(workCycleCountHex);
            message.setInteractionPayloadHex(interactionPayload.toString());
            index += 4;

            String robotInPositionTimeHex = hexString.substring(index, index + 4);
            message.setRobotInPositionTime(parseTwoByteBCDStandard(robotInPositionTimeHex, 1.0).intValue());
            index += 4;

            String walkPositionCheckTimeHex = hexString.substring(index, index + 4);
            message.setWalkPositionCheckTime(parseTwoByteBCDStandard(walkPositionCheckTimeHex, 1.0).intValue());
            index += 4;

            String currentSpeedHex = hexString.substring(index, index + 4);
            double currentSpeed = parseTwoByteBCDStandard(currentSpeedHex, 1.0);
            message.setCurrentSpeed(currentSpeed);
            message.setWalkFastSpeed(currentSpeed);
            index += 4;

            String heartbeatHex = hexString.substring(index, index + 4);
            message.setHeartbeat(parseTwoByteBCDStandard(heartbeatHex, 100.0));
            index += 4;

            String batteryHex = hexString.substring(index, index + 4);
            message.setBatteryLevel(parseTwoByteBCDStandard(batteryHex, 10.0));
            index += 4;

            message.setBackup3(hexString.substring(index, index + 4));
            index += 4;
            message.setBackup4(hexString.substring(index, index + 4));

            log.info("D12 status parsed");
            return message;
        } catch (Exception e) {
            log.error("parse D12 status failed - hexString={}", hexString, e);
            return null;
        }
    }
    private Double parseTwoByteBCDStandard(String hex, Double divisor) {
        try {


            int byte1 = Integer.parseInt(hex.substring(0, 2), 16);
            int byte2 = Integer.parseInt(hex.substring(2, 4), 16);


            int value = ((byte1 >> 4) & 0x0F) * 1000 + (byte1 & 0x0F) * 100 +
                    ((byte2 >> 4) & 0x0F) * 10 + (byte2 & 0x0F);

            return divisor > 0 ? value / divisor : value * 1.0;
        } catch (Exception e) {
            log.error("Railcar error event", hex, e);
            return 0.0;
        }
    }

    /**



     */

    private Double parseGpsCoordinate(String hex) {
        try {

            String byte1 = hex.substring(0, 2); // 01 / 23
            String byte2 = hex.substring(2, 4); // 21 / 11
            String byte3 = hex.substring(4, 6); // 10 / 02
            String byte4 = hex.substring(6, 8); // 38 / 55


            String reorderedHex = byte3 + byte4 + byte1 + byte2;


            long value = Long.parseLong(reorderedHex, 10);


            return value / 100000.0;
        } catch (Exception e) {
            log.error("Railcar error event", hex, e);
            return 0.0;
        }
    }

    /**


     */
    private String parseTimeBCD(String hex, String separator) {
        try {
            if (hex.length() == 4) {

                int digit1 = Integer.parseInt(hex.substring(0, 1), 16); // 0
                int digit2 = Integer.parseInt(hex.substring(1, 2), 16); // 7
                int digit3 = Integer.parseInt(hex.substring(2, 3), 16); // 2
                int digit4 = Integer.parseInt(hex.substring(3, 4), 16); // 5


                int value1 = digit1 * 10 + digit2; // 0*10 + 7 = 7 -> 07
                int value2 = digit3 * 10 + digit4; // 2*10 + 5 = 25

                return String.format("%02d%s%02d", value1, separator, value2);
            }
            return hex;
        } catch (Exception e) {
            log.error("Railcar error event", hex, e);
            return hex;
        }
    }

    /**

     */
    private Double parseBatteryLevel(String hex) {
        try {
            int value = Integer.parseInt(hex, 16);
            return value / 10.0;
        } catch (Exception e) {
            log.error("Railcar error event", hex, e);
            return 0.0;
        }
    }

    /**

     */
    private Double parseSpeed(String hex) {
        try {
            int value = Integer.parseInt(hex, 16);
            return (value / 100.0) * 0.15;
        } catch (Exception e) {
            log.error("Railcar error event", hex, e);
            return 0.0;
        }
    }

    /**

     */
    private Double parseBrushSpeed(String hex) {
        try {
            int value = Integer.parseInt(hex, 16);
            return (value / 100.0) * 0.5;
        } catch (Exception e) {
            log.error("Railcar error event", hex, e);
            return 0.0;
        }
    }

    /**

     */
    private Double parseFourByteCoordinate(String hex) {
        try {

            long value = Long.parseLong(hex, 16);
            return value / 100000.0;
        } catch (Exception e) {
            log.error("Railcar error event", hex, e);
            return 0.0;
        }
    }

    private Double parseBcdDistance2Bytes(String hex) {
        return parseTwoByteBCDStandard(hex, 10.0);
    }

    private Double parseBcdDistance4Bytes(String hex) {

        if (hex != null && hex.length() >= 4) {
            String bcdPart = hex.substring(0, 4);
            return parseTwoByteBCDStandard(bcdPart, 10.0);
        }
        return 0.0;
    }

    private Double parseFourByteLittleEndian(String hex, Double divisor) {
        try {
            if (hex.length() == 8) {
                String reorderedHex = hex.substring(6, 8) + hex.substring(4, 6) + hex.substring(2, 4)
                        + hex.substring(0, 2);
                long value = Long.parseLong(reorderedHex, 16);
                return divisor > 0 ? value / divisor : value * 1.0;
            }
            return 0.0;
        } catch (Exception e) {
            log.error("Railcar error event", hex, e);
            return 0.0;
        }
    }

    private Double parseFourByteValue(String hex, Double divisor) {
        try {
            long value = Long.parseLong(hex, 16);
            return divisor > 0 ? value / divisor : value * 1.0;
        } catch (Exception e) {
            log.error("Railcar error event", hex, e);
            return 0.0;
        }
    }

    /**



     */
    private String hexToString(String hex) {
        if (hex == null || hex.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i + 1 < hex.length(); i += 2) {
            int value = Integer.parseInt(hex.substring(i, i + 2), 16);
            if (value != 0) {
                builder.append((char) value);
            }
        }
        return builder.toString().trim();
    }

    private String getLastOperationModeFromDB(String serialNumber) {
        if (serialNumber == null || serialNumber.trim().isEmpty()) {
            return null;
        }
        return redisUtil.getString("railcar:last-operation-mode:" + serialNumber);
    }

    private void saveLastOperationModeToDB(String serialNumber, String currentMode) {
        if (serialNumber == null || serialNumber.trim().isEmpty() || currentMode == null || currentMode.trim().isEmpty()) {
            return;
        }
        redisUtil.set("railcar:last-operation-mode:" + serialNumber, currentMode, LAST_OPERATION_MODE_EXPIRE_SECONDS);
    }
    private void relayInteractionIfNeeded(String sourceSerialNumber, RailcarMessage message) {
        try {
            if (sourceSerialNumber == null || sourceSerialNumber.length() < 4 || message == null) {
                return;
            }
            if (!shouldRelayInteractionStatus(message)) {
                clearRelayStateForSource(sourceSerialNumber);
                return;
            }

            String payloadHex = normalizeInteractionPayloadHex(message.getInteractionPayloadHex());
            if (payloadHex == null) {
                clearRelayStateForSource(sourceSerialNumber);
                return;
            }
            Integer workDataComplete = message.getWorkDataComplete();
            long now = System.currentTimeMillis();
            pendingInteractionRelayCache.compute(sourceSerialNumber, (key, existing) -> {
                if (existing == null) {
                    return new PendingInteractionRelay(
                            sourceSerialNumber,
                            payloadHex,
                            workDataComplete,
                            payloadHex,
                            message.getCompanyCode(),
                            message.getBindSendEnable(),
                            now);
                }

                boolean changed = isInteractionStateChanged(existing, payloadHex, workDataComplete);
                String nextPendingPayloadHex = changed ? payloadHex : existing.getPendingPayloadHex();
                String nextLastSeenPayloadHex = changed ? payloadHex : existing.getLastSeenPayloadHex();
                Integer nextLastSeenWorkDataComplete = changed ? workDataComplete : existing.getLastSeenWorkDataComplete();

                return existing.next(
                        nextLastSeenPayloadHex,
                        nextLastSeenWorkDataComplete,
                        nextPendingPayloadHex,
                        message.getCompanyCode(),
                        message.getBindSendEnable(),
                        now);
            });
        } catch (Exception e) {
            log.error("Railcar error event", sourceSerialNumber, e);
        }
    }

    @Scheduled(fixedDelay = INTERACTION_RELAY_DISPATCH_INTERVAL_MS)
    public void dispatchPendingInteractionRelay() {
        long now = System.currentTimeMillis();
        cleanupExpiredRelayState(now);
        for (PendingInteractionRelay pending : pendingInteractionRelayCache.values()) {
            dispatchInteractionRelay(pending, now);
        }
    }

    private void dispatchInteractionRelay(PendingInteractionRelay pending, long now) {
        if (pending == null) {
            return;
        }
        String payloadHex = pending.getPendingPayloadHex();
        if (payloadHex == null) {
            return;
        }
        String sourceSerialNumber = pending.getSourceSerialNumber();
        if (sourceSerialNumber == null || sourceSerialNumber.length() < 4) {
            return;
        }
        if (now - pending.getUpdatedAtMs() > INTERACTION_RELAY_NO_INPUT_EXPIRE_MS) {
            clearRelayStateForSource(sourceSerialNumber);
            return;
        }

        List<String> targets = resolveRelayTargetsBySource(sourceSerialNumber);
        if (targets.isEmpty()) {
            return;
        }

        RailcarMessage relayMessage = new RailcarMessage();
        relayMessage.setCompanyCode(pending.getCompanyCode());
        relayMessage.setBindSendEnable(pending.getBindSendEnable());

        for (String targetSerialNumber : targets) {
            List<String> bindDeviceIds = resolveBindingDeviceIdsForRelay(targetSerialNumber);
            sendInteractionRelay(
                    relayMessage,
                    sourceSerialNumber,
                    targetSerialNumber,
                    payloadHex,
                    bindDeviceIds);
        }
    }
    private List<String> resolveRelayTargetsBySource(String sourceSerialNumber) {
        if (sourceSerialNumber == null || sourceSerialNumber.length() < 4) {
            return new ArrayList<String>();
        }
        String sourceModel = sourceSerialNumber.substring(0, 4);
        if ("-D12".equals(sourceModel)) {
            return normalizeBoundD01Serials(vehicleService.getBoundD01Serials(sourceSerialNumber));
        }
        if ("-D01".equals(sourceModel)) {
            Vehicle d12 = vehicleService.findD12ByBoundD01Serial(sourceSerialNumber);
            if (d12 != null && d12.getSerialNumber() != null && !d12.getSerialNumber().trim().isEmpty()) {
                return Arrays.asList(d12.getSerialNumber().trim());
            }
        }
        return new ArrayList<String>();
    }

    private void clearRelayStateForSource(String sourceSerialNumber) {
        pendingInteractionRelayCache.remove(sourceSerialNumber);
    }

    private boolean isInteractionStateChanged(
            PendingInteractionRelay existing,
            String payloadHex,
            Integer workDataComplete) {
        if (existing == null) {
            return true;
        }
        if (!Objects.equals(payloadHex, existing.getLastSeenPayloadHex())) {
            return true;
        }
        return workDataComplete != null
                && !Objects.equals(workDataComplete, existing.getLastSeenWorkDataComplete());
    }

    private void cleanupExpiredRelayState(long now) {
        pendingInteractionRelayCache.entrySet().removeIf(entry -> {
            PendingInteractionRelay pending = entry.getValue();
            return pending == null || now - pending.getUpdatedAtMs() > INTERACTION_RELAY_STATE_EXPIRE_MS;
        });
    }

    private static final class PendingInteractionRelay {
        private final String sourceSerialNumber;
        private final String lastSeenPayloadHex;
        private final Integer lastSeenWorkDataComplete;
        private final String pendingPayloadHex;
        private final String companyCode;
        private final Integer bindSendEnable;
        private final long updatedAtMs;

        private PendingInteractionRelay(
                String sourceSerialNumber,
                String lastSeenPayloadHex,
                Integer lastSeenWorkDataComplete,
                String pendingPayloadHex,
                String companyCode,
                Integer bindSendEnable,
                long updatedAtMs) {
            this.sourceSerialNumber = sourceSerialNumber;
            this.lastSeenPayloadHex = lastSeenPayloadHex;
            this.lastSeenWorkDataComplete = lastSeenWorkDataComplete;
            this.pendingPayloadHex = pendingPayloadHex;
            this.companyCode = companyCode;
            this.bindSendEnable = bindSendEnable;
            this.updatedAtMs = updatedAtMs;
        }

        private PendingInteractionRelay next(
                String lastSeenPayloadHex,
                Integer lastSeenWorkDataComplete,
                String pendingPayloadHex,
                String companyCode,
                Integer bindSendEnable,
                long updatedAtMs) {
            return new PendingInteractionRelay(
                    sourceSerialNumber,
                    lastSeenPayloadHex,
                    lastSeenWorkDataComplete,
                    pendingPayloadHex,
                    companyCode,
                    bindSendEnable,
                    updatedAtMs);
        }

        private String getSourceSerialNumber() {
            return sourceSerialNumber;
        }

        private String getLastSeenPayloadHex() {
            return lastSeenPayloadHex;
        }

        private Integer getLastSeenWorkDataComplete() {
            return lastSeenWorkDataComplete;
        }

        private String getPendingPayloadHex() {
            return pendingPayloadHex;
        }

        private String getCompanyCode() {
            return companyCode;
        }

        private Integer getBindSendEnable() {
            return bindSendEnable;
        }

        private long getUpdatedAtMs() {
            return updatedAtMs;
        }
    }

    private boolean sendInteractionRelay(
            RailcarMessage message,
            String sourceSerialNumber,
            String targetSerialNumber,
            String payloadHex,
            List<String> bindDeviceIds) {
        if (targetSerialNumber == null || targetSerialNumber.length() < 4) {
            return false;
        }

        DeviceConfigRequest request = new DeviceConfigRequest();
        request.setDeviceId(targetSerialNumber);
        request.setModel(targetSerialNumber.substring(0, 4));
        request.setCompanyCode(resolveCompanyCode(targetSerialNumber, message.getCompanyCode()));
        request.setInfoCommandType(2);
        request.setBindStatus(resolveBindEnabledForRelay(targetSerialNumber));
        request.setBindDeviceIds(bindDeviceIds);
        request.setBindDeviceId((bindDeviceIds != null && !bindDeviceIds.isEmpty()) ? bindDeviceIds.get(0) : "");
        request.setInteractionPayloadHex(payloadHex);
        fillRelayRequestFromConfig(request);

        boolean sent = railcarControlService.sendControlCommand(request);
        if (sent) {
            log.info("Railcar info event",
                    sourceSerialNumber, targetSerialNumber, message.getBindSendEnable(), payloadHex);
        } else {
            log.error("Railcar error event", sourceSerialNumber, targetSerialNumber);
        }
        return sent;
    }

    private boolean shouldRelayInteractionStatus(RailcarMessage message) {
        return message.getBindSendEnable() != null
                && message.getBindSendEnable() > 0
                && normalizeInteractionPayloadHex(message.getInteractionPayloadHex()) != null;
    }

    private void fillRelayRequestFromConfig(DeviceConfigRequest request) {
        RailcarConfig config = railcarConfigService.getConfigByDeviceId(request.getDeviceId());
        if (config == null) {
            log.warn("Railcar warning event", request.getDeviceId());
            return;
        }

        if (config.getCompanyCode() != null && !config.getCompanyCode().trim().isEmpty()) {
            request.setCompanyCode(config.getCompanyCode().trim());
        }
        request.setControlMode(config.getOperationMode());
        request.setEnableMode(config.getOperationEnable());
        request.setHeartbeatSet(config.getHeartbeatPulse());
        request.setBatteryLowLimit(config.getBatteryLowLimit());
        request.setReserved(config.getBackup());
        request.setReserved2(0);

        if (isD12Railcar(request.getModel())) {
            request.setRobotInPositionTime(config.getRobotInPositionTime());
            request.setLimitPositionCheckTime(config.getLimitPositionCheckTime());
            request.setWalkPositionCheckTime(config.getWalkPositionCheckTime());
            request.setWalkFastSpeed(config.getWalkFastSpeed());
            request.setWalkSlowSpeed(config.getWalkSlowSpeed());
            request.setMaxRowCount(config.getMaxRowCount());
            return;
        }

        request.setEdgeDelay(config.getEdgeDetectionDelay());
        request.setBridgeTime(config.getBridgeDetectionTime());
        request.setErrorReturnTime(config.getErrorReturnTime());
        request.setWalkSpeed(config.getWalkingSpeed());
        request.setBrushSpeed(config.getBrushSpeed());
        request.setBridgeSpeed(config.getBridgeSpeed());
    }

    private List<String> resolveBindingDeviceIdsForRelay(String targetSerialNumber) {
        if (targetSerialNumber == null || targetSerialNumber.length() < 4) {
            return new ArrayList<String>();
        }
        String targetModel = targetSerialNumber.substring(0, 4);
        if ("-D12".equals(targetModel)) {
            return normalizeBoundD01Serials(vehicleService.getBoundD01Serials(targetSerialNumber));
        }
        if ("-D01".equals(targetModel)) {
            List<String> deviceIds = new ArrayList<String>();
            Vehicle d12 = vehicleService.findD12ByBoundD01Serial(targetSerialNumber);
            if (d12 != null && d12.getSerialNumber() != null && !d12.getSerialNumber().trim().isEmpty()) {
                deviceIds.add(d12.getSerialNumber().trim());
            }
            return deviceIds;
        }
        return new ArrayList<String>();
    }

    private int resolveBindEnabledForRelay(String targetSerialNumber) {
        return resolveBindingDeviceIdsForRelay(targetSerialNumber).isEmpty() ? 0 : 1;
    }

    private List<String> normalizeBoundD01Serials(List<String> rawBindings) {
        List<String> normalized = new ArrayList<String>();
        if (rawBindings == null) {
            return normalized;
        }
        for (String serial : rawBindings) {
            if (serial == null) {
                continue;
            }
            String trimmed = serial.trim();
            if (trimmed.startsWith("-D01") && !normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private String normalizeInteractionPayloadHex(String payloadHex) {
        if (payloadHex == null) {
            return null;
        }
        String normalized = payloadHex.trim().toUpperCase();
        if (normalized.length() == 32 && normalized.matches("[0-9A-F]+")) {
            return normalized;
        }
        return null;
    }

    /**
     * 判断是否为绑定回读帧（infoCommandType=2，起始地址0000，长度0035）。
     */
    private boolean isBindingRelayEchoFrame(String hexString) {
        if (hexString == null || hexString.length() < 48) {
            return false;
        }
        String identifierCode = safeWord(hexString, 10);
        String startAddress = safeWord(hexString, 11);
        String dataLength = safeWord(hexString, 12);
        return "0002".equals(identifierCode)
                && "0000".equals(startAddress)
                && "0035".equals(dataLength);
    }

    private String safeWord(String hexString, int wordIndex) {
        int start = (wordIndex - 1) * 4;
        int end = start + 4;
        if (start < 0 || end > hexString.length()) {
            return "";
        }
        return hexString.substring(start, end);
    }

    private boolean isD12Railcar(String model) {
        return "-D12".equals(model) || "-T12".equals(model);
    }
    private int parseHexWordToInt(String hex, int defaultValue) {
        if (hex == null || hex.trim().isEmpty()) {
            return defaultValue;
        }
        String normalized = hex.trim();
        try {
            return Integer.parseInt(normalized, 16);
        } catch (Exception e) {
            log.warn("Railcar warning event", hex, defaultValue);
            return defaultValue;
        }
    }

    private String resolveCompanyCode(String serialNumber, String fallbackCompanyCode) {
        try {
            Vehicle target = vehicleService.getBySerialNumber(serialNumber);
            if (target != null && target.getCompanyCode() != null && !target.getCompanyCode().trim().isEmpty()) {
                return target.getCompanyCode().trim();
            }
        } catch (Exception e) {
            log.warn("Railcar warning event", serialNumber, e);
        }
        if (fallbackCompanyCode != null && !fallbackCompanyCode.trim().isEmpty()) {
            return fallbackCompanyCode.trim();
        }
        return "ZTZN-PVC";
    }

    /**

     */
    private void logRailcarStatusDetails(RailcarMessage message) {
        log.info("Railcar info event");
        log.info("Railcar info event", message.getDeviceId());
        log.info("Railcar info event", message.getCompanyCode());
        log.info("Railcar info event", message.getProductModel());
        log.info("Railcar info event", message.getProductNumber());
        log.info("Railcar info event", message.getWorkMode(), message.getWorkModeDescription());
        log.info("Railcar info event",
                message.getWeekYear(), message.getMonthDay(), message.getHourMinute());
        log.info("Railcar info event",
                String.format("%.5f", message.getLongitude()),
                String.format("%.5f", message.getLatitude()));
        log.info("Railcar info event",
                String.format("%.2f", message.getSingleRunTime()),
                String.format("%.0f", message.getTotalRunTime()));
        log.info("Railcar info event",
                String.format("%.3f", message.getSingleRunDistance()),
                String.format("%.0f", message.getTotalRunDistance()));
        log.info("Railcar info event", message.getOperationMode(), message.getOperationModeDescription());
        log.info("Railcar info event", message.getOperationEnable(), message.getOperationEnableDescription());
        log.info("Railcar info event", String.format("%.1f", message.getBatteryLevel()));
        log.info("Railcar info event", String.format("%.1f", message.getCurrentSpeed()));
        log.info("Railcar info event", String.format("%.1f", message.getBrushSpeed()));
        log.info("Railcar info event", String.format("%.1f", message.getBridgeSpeed()));
        log.info("Railcar info event", String.format("%.2f", message.getHeartbeat()));
        log.info("Railcar info event",
                message.getBackup1(), message.getBackup2());
        log.info("=================");
    }

    /**

     */
    private String parseStatusForRedis(RailcarMessage message) {
        String operationMode = message.getOperationModeDescription();
        if (operationMode == null) {
            operationMode = "";
        }

        if (operationMode.contains("AUTO") || operationMode.contains("CONTINUOUS")) {
            return "working";
        }
        if (operationMode.contains("MANUAL")) {
            return "active";
        }
        return "idle";
    }
    /**


     */
    private long parseLastUpdateTime(RailcarMessage message) {
        try {
            String monthDay = message.getMonthDay();
            String hourMinute = message.getHourMinute();
            if (monthDay == null || hourMinute == null) {
                return System.currentTimeMillis();
            }

            String[] monthDayParts = monthDay.split("/");
            String[] hourMinuteParts = hourMinute.split(":");
            if (monthDayParts.length != 2 || hourMinuteParts.length != 2) {
                return System.currentTimeMillis();
            }

            int month = Integer.parseInt(monthDayParts[0]);
            int day = Integer.parseInt(monthDayParts[1]);
            int hour = Integer.parseInt(hourMinuteParts[0]);
            int minute = Integer.parseInt(hourMinuteParts[1]);

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.MONTH, month - 1);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            Map<String, Object> redisData = new HashMap<>();
            calendar.set(Calendar.MILLISECOND, 0);
            if (calendar.getTime().after(new Date())) {
                calendar.add(Calendar.YEAR, -1);
            }
            return calendar.getTimeInMillis();
        } catch (Exception e) {
            log.error("parse last update time failed, fallback current time", e);
            return System.currentTimeMillis();
        }
    }
    /**


     */
    private void updateRailcarStatusToRedis(RailcarMessage message) {
        try {

            String redisKey = message.getSerialNumber();

            Map<String, Object> redisData = new HashMap<>();


            redisData.put("deviceId", message.getSerialNumber());
            redisData.put("battery", message.getBatteryLevel());
            redisData.put("status", parseStatusForRedis(message));
            redisData.put("lastUpdateTime", parseLastUpdateTime(message));


            Map<String, Object> locationMap = new HashMap<>();
            locationMap.put("lon", message.getLongitude());
            locationMap.put("lat", message.getLatitude());
            redisData.put("location", locationMap);




            if (message.getOperationMode() != null) {
                redisData.put("runControl", Integer.parseInt(message.getOperationMode(), 16));
            }
            if (message.getOperationEnable() != null) {
                redisData.put("runEnable", Integer.parseInt(message.getOperationEnable(), 16));
            }
            if (message.getWorkMode() != null) {
                redisData.put("workMode", Integer.parseInt(message.getWorkMode(), 16));
            }
            if (message.getFaultCode() != null) {
                redisData.put("faultCode", message.getFaultCode());
            }
            if (message.getBindSendEnable() != null) {
                redisData.put("bindSendEnable", message.getBindSendEnable());
            }
            if (message.getInteractionCommand() != null) {
                redisData.put("interactionCommand", message.getInteractionCommand());
            }
            if (message.getInteractionPayloadHex() != null) {
                redisData.put("interactionPayloadHex", message.getInteractionPayloadHex());
            }
            if (message.getWorkDataComplete() != null) {
                redisData.put("workDataComplete", message.getWorkDataComplete());
            }
            if (message.getWorkCycleCount() != null) {
                redisData.put("workCycleCount", message.getWorkCycleCount());
            }
            if (message.getCurrentRowPosition() != null) {
                redisData.put("currentRowPosition", message.getCurrentRowPosition());
            }


            if (message.getCurrentSpeed() != null) {
                redisData.put("walkSpeed", message.getCurrentSpeed().intValue());
            }
            if (message.getBrushSpeed() != null) {
                redisData.put("brushSpeed", message.getBrushSpeed().intValue());
            }
            if (message.getBridgeSpeed() != null) {
                redisData.put("bridgeSpeed", message.getBridgeSpeed().intValue());
            }


            if (message.getSingleRunTime() != null) {
                redisData.put("runTimeSingle", message.getSingleRunTime());
            }
            if (message.getTotalRunTime() != null) {
                redisData.put("runTimeTotal", message.getTotalRunTime());
            }
            if (message.getSingleRunDistance() != null) {
                redisData.put("mileageSingle", message.getSingleRunDistance());
            }
            if (message.getTotalRunDistance() != null) {
                redisData.put("mileageTotal", message.getTotalRunDistance());
            }


            if (message.getHeartbeat() != null) {
                redisData.put("heartbeat", message.getHeartbeat());
            }


            if (message.getD12WorkWay() != null) {
                redisData.put("d12WorkWay", Integer.parseInt(message.getD12WorkWay(), 16));
            }
            if (message.getLeftRowStart() != null) {
                redisData.put("leftRowStart", message.getLeftRowStart());
            }
            if (message.getLeftRowEnd() != null) {
                redisData.put("leftRowEnd", message.getLeftRowEnd());
            }
            if (message.getRightRowStart() != null) {
                redisData.put("rightRowStart", message.getRightRowStart());
            }
            if (message.getRightRowEnd() != null) {
                redisData.put("rightRowEnd", message.getRightRowEnd());
            }
            if (message.getWalkFastSpeed() != null) {
                redisData.put("walkFastSpeed", message.getWalkFastSpeed().intValue());
            }
            if (message.getWalkSlowSpeed() != null) {
                redisData.put("walkSlowSpeed", message.getWalkSlowSpeed().intValue());
            }
            if (message.getBatteryLowLimit() != null) {
                redisData.put("batteryLowLimit", message.getBatteryLowLimit().intValue());
            }
            if (message.getRobotInPositionTime() != null) {
                redisData.put("robotInPositionTime", message.getRobotInPositionTime());
            }
            if (message.getLimitPositionCheckTime() != null) {
                redisData.put("limitPositionCheckTime", message.getLimitPositionCheckTime());
            }
            if (message.getWalkPositionCheckTime() != null) {
                redisData.put("walkPositionCheckTime", message.getWalkPositionCheckTime());
            }


            boolean success = redisUtil.setVehicle(redisKey, redisData, REDIS_EXPIRE_TIME, TimeUnit.SECONDS);

            if (success) {
                log.info("Railcar info event",
                        message.getSerialNumber(), message.getBatteryLevel(), redisData.get("status"));


                deviceStatusPublisher.publishDeviceStatus(message.getSerialNumber(), message);
            } else {
                log.error("Railcar error event", message.getSerialNumber());
            }

        } catch (Exception e) {
            log.error("Railcar error event", message.getSerialNumber(), e);
        }
    }
}
