package com.zt.cleanbot.service;

import com.zt.cleanbot.dto.DeviceConfigRequest;
import com.zt.cleanbot.dto.DeviceControlResponse;
import com.zt.cleanbot.model.DeviceControlAudit;
import com.zt.cleanbot.model.RailcarConfig;
import com.zt.cleanbot.model.Vehicle;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 设备控制服务
 * 封装设备控制逻辑，包括编码、发送和审计
 */
@Slf4j
@Service
public class DeviceControlService {

    private static final long DEFAULT_COMMAND_TIMEOUT_MS = 30_000L;

    @Autowired
    private RailcarControlService railcarControlService;

    @Autowired
    private DeviceControlAuditService auditService;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private RailcarConfigService railcarConfigService;

    @Autowired
    private CommandStatusService commandStatusService;

    /**
     * 发送设备控制命令（安全模式）
     *
     * 注意：此方法不加 @Transactional。
     * 原因：MQTT 发送是非 DB 操作，混入同一事务会导致：
     *   saveOrUpdateConfig 内部抛异常时，即使被 try-catch 捕获，
     *   外层事务已被标记 rollback-only，最终触发 UnexpectedRollbackException。
     * 各个 DB 操作（saveOrUpdateConfig、saveAuditLog）各自携带 @Transactional，
     * 独立管理自己的事务即可。
     *
     * @param request 设备配置请求（业务参数）
     * @return 控制响应
     */
    public DeviceControlResponse sendControlCommand(DeviceConfigRequest request) {
        long totalStartNs = System.nanoTime();
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }
        String commandId = request.getCommandId();
        if (commandId == null || commandId.trim().isEmpty()) {
            commandId = "cmd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            request.setCommandId(commandId);
        }
        request.setTraceId(traceId);
        commandStatusService.initializeCommand(
                commandId,
                traceId,
                request.getDeviceId(),
                resolveDeviceType(request),
                resolveActionName(request),
                request.getUsername(),
                DEFAULT_COMMAND_TIMEOUT_MS,
                buildCommandDetail(request));
        log.info("[{}] 开始处理设备控制请求 - 设备: {}, 用户: {}",
                traceId, request.getDeviceId(), request.getUsername());

        try {
            // 1. 参数验证
            long validateStartNs = System.nanoTime();
            validateRequest(request);
            log.info("[latency][{}] 参数验证完成 - 设备: {}, 耗时: {} ms",
                    traceId, request.getDeviceId(), elapsedMs(validateStartNs));

            // 2. 查库补全缺失参数（前端传了的字段优先，null 字段才用库里的值兜底）
            long fillStartNs = System.nanoTime();
            fillMissingParamsFromDb(request);
            log.info("[latency][{}] 缺失参数补全完成 - 设备: {}, 耗时: {} ms",
                    traceId, request.getDeviceId(), elapsedMs(fillStartNs));

            // 3. 发送 MQTT 命令
            // D12 设置参数时：自动按绑定关系同步下发到绑定 D01
            long mqttSendStartNs = System.nanoTime();
            boolean success = sendWithBindingFlow(request);
            if (success) {
                request.setMqttSentTimestamp(System.currentTimeMillis());
            }
            log.info("[latency][{}] MQTT 发送流程完成 - 设备: {}, success: {}, 耗时: {} ms",
                    traceId, request.getDeviceId(), success, elapsedMs(mqttSendStartNs));

            // 3. 构建响应
            DeviceControlResponse response;
            String mqttTopic = "RAILCAR/R/" + request.getDeviceId();

            if (success) {
                // 成功：仅设置帧持久化，先 MySQL 再 Redis
                if (!Boolean.TRUE.equals(request.getQuickAction()) && resolveCommandType(request) != 2) {
                    try {
                        long persistStartNs = System.nanoTime();
                        boolean saved = railcarConfigService.saveOrUpdateConfig(request);
                        log.info("[latency][{}] MySQL 配置保存完成 - 设备: {}, saved: {}, 耗时: {} ms",
                                traceId, request.getDeviceId(), saved, elapsedMs(persistStartNs));
                        if (saved) {
                            long cacheStartNs = System.nanoTime();
                            String settingFrameHex = railcarControlService.encodeControlCommand(request);
                            if (settingFrameHex != null && !settingFrameHex.isEmpty()) {
                                railcarConfigService.cacheLatestConfig(request, settingFrameHex);
                                log.info("[latency][{}] Redis 配置缓存完成 - 设备: {}, 耗时: {} ms",
                                        traceId, request.getDeviceId(), elapsedMs(cacheStartNs));
                            } else {
                                log.warn("[{}] 设置帧编码为空，跳过 Redis 配置缓存 - 设备: {}", traceId, request.getDeviceId());
                            }
                        } else {
                            log.warn("[{}] 设置帧写入 MySQL 未成功，跳过 Redis 配置缓存 - 设备: {}", traceId, request.getDeviceId());
                        }
                    } catch (Exception e) {
                        log.error("[{}] 保存设备配置失败 - 设备: {}", traceId, request.getDeviceId(), e);
                    }
                }

                // 成功：记录审计日志
                long auditStartNs = System.nanoTime();
                DeviceControlAudit audit = buildAuditLog(
                        request, "CONTROL", "SUCCESS", mqttTopic, traceId, null);
                auditService.saveAuditLog(audit);
                log.info("[latency][{}] 成功审计日志保存完成 - 设备: {}, 耗时: {} ms",
                        traceId, request.getDeviceId(), elapsedMs(auditStartNs));

                log.info("[{}] 设备控制命令发送成功 - 设备: {}, MQTT主题: {}",
                        traceId, request.getDeviceId(), mqttTopic);

                commandStatusService.markDispatched(
                        commandId,
                        "设备控制命令已发出",
                        buildStatusDetail(request, "DISPATCHED"));

                response = DeviceControlResponse.success(
                        request.getDeviceId(),
                        mqttTopic,
                        audit.getId());
            } else {
                // 失败：记录审计日志
                long auditStartNs = System.nanoTime();
                DeviceControlAudit audit = buildAuditLog(
                        request, "CONTROL", "FAILURE", mqttTopic, traceId,
                        "MQTT 发送失败");
                auditService.saveAuditLog(audit);
                log.info("[latency][{}] 失败审计日志保存完成 - 设备: {}, 耗时: {} ms",
                        traceId, request.getDeviceId(), elapsedMs(auditStartNs));

                log.error("[{}] 设备控制命令发送失败 - 设备: {}", traceId, request.getDeviceId());

                commandStatusService.markFailed(
                        commandId,
                        "MQTT 发送失败，请检查网络连接",
                        buildStatusDetail(request, "FAILED"));

                response = DeviceControlResponse.failure(
                        request.getDeviceId(),
                        "MQTT 发送失败，请检查网络连接");
            }

            populateLatencyMetrics(response, request, traceId);

            log.info("[latency][{}] sendControlCommand 完成 - 设备: {}, success: {}, 总耗时: {} ms",
                    traceId, request.getDeviceId(), response.getSuccess(), elapsedMs(totalStartNs));

            return response;

        } catch (

        IllegalArgumentException e) {
            log.error("[latency][{}] sendControlCommand 参数异常 - 设备: {}, 总耗时: {} ms",
                    traceId, request.getDeviceId(), elapsedMs(totalStartNs));
            log.error("[{}] 参数验证失败 - 设备: {}, 错误: {}",
                    traceId, request.getDeviceId(), e.getMessage());

            commandStatusService.markRejected(
                    commandId,
                    e.getMessage(),
                    buildStatusDetail(request, "REJECTED"));

            // 记录审计日志
            DeviceControlAudit audit = buildAuditLog(
                    request, "CONTROL", "FAILURE", null, traceId, e.getMessage());
            auditService.saveAuditLog(audit);
            DeviceControlResponse response = DeviceControlResponse.failure(request.getDeviceId(), e.getMessage());
            populateLatencyMetrics(response, request, traceId);
            return response;

        } catch (Exception e) {
            log.error("[latency][{}] sendControlCommand 系统异常 - 设备: {}, 总耗时: {} ms",
                    traceId, request.getDeviceId(), elapsedMs(totalStartNs));
            log.error("[{}] 设备控制异常 - 设备: {}", traceId, request.getDeviceId(), e);

            commandStatusService.markFailed(
                    commandId,
                    "服务器内部错误",
                    buildStatusDetail(request, "FAILED"));

            // 记录审计日志
            DeviceControlAudit audit = buildAuditLog(
                    request, "CONTROL", "FAILURE", null, traceId, "服务器内部错误");
            auditService.saveAuditLog(audit);
            DeviceControlResponse response = DeviceControlResponse.failure(request.getDeviceId(), "服务器内部错误");
            populateLatencyMetrics(response, request, traceId);
            return response;
        }
    }

    private long elapsedMs(long startNs) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
    }

    /**
     * 查库补全缺失参数：前端已传的字段保持不变，null 字段用库里保存的配置值兜底
     */
    private void fillMissingParamsFromDb(DeviceConfigRequest request) {
        String deviceId = request.getDeviceId();
        if (deviceId == null || deviceId.length() < 4) {
            return;
        }

        // 优先用 request 中的 model，否则从 deviceId 前4位推断
        String productModel = (request.getModel() != null && !request.getModel().trim().isEmpty())
                ? request.getModel().trim()
                : deviceId.substring(0, 4);
        String productNumber = deviceId.length() > 4 ? deviceId.substring(4) : deviceId;

        RailcarConfig saved = railcarConfigService.getConfig(productModel, productNumber);
        if (saved == null) {
            log.debug("未找到设备历史配置，跳过补全 - deviceId: {}", deviceId);
            return;
        }

        log.debug("从 Redis/数据库补全缺失参数 - deviceId: {}", deviceId);

        // 通用参数：只补 null
        if (request.getWorkWay()          == null) request.setWorkWay(saved.getWorkMode());
        if (request.getControlMode()      == null) request.setControlMode(saved.getOperationMode());
        if (request.getEnableMode()       == null) request.setEnableMode(saved.getOperationEnable());
        if (request.getEdgeDelay()        == null) request.setEdgeDelay(saved.getEdgeDetectionDelay());
        if (request.getBridgeTime()       == null) request.setBridgeTime(saved.getBridgeDetectionTime());
        if (request.getErrorReturnTime()  == null) request.setErrorReturnTime(saved.getErrorReturnTime());
        if (request.getWalkSpeed()        == null) request.setWalkSpeed(saved.getWalkingSpeed());
        if (request.getBrushSpeed()       == null) request.setBrushSpeed(saved.getBrushSpeed());
        if (request.getBridgeSpeed()      == null) request.setBridgeSpeed(saved.getBridgeSpeed());
        if (request.getHeartbeatSet()     == null) request.setHeartbeatSet(saved.getHeartbeatPulse());
        if (request.getBatteryLowLimit()  == null) request.setBatteryLowLimit(saved.getBatteryLowLimit());

        // D12 接驳车专属参数：只补 null
        if (request.getRobotInPositionTime()    == null) request.setRobotInPositionTime(saved.getRobotInPositionTime());
        if (request.getLimitPositionCheckTime() == null) request.setLimitPositionCheckTime(saved.getLimitPositionCheckTime());
        if (request.getWalkPositionCheckTime()  == null) request.setWalkPositionCheckTime(saved.getWalkPositionCheckTime());
        if (request.getWalkFastSpeed()          == null) request.setWalkFastSpeed(saved.getWalkFastSpeed());
        if (request.getWalkSlowSpeed()          == null) request.setWalkSlowSpeed(saved.getWalkSlowSpeed());
        if (request.getMaxRowCount()            == null) request.setMaxRowCount(saved.getMaxRowCount());
    }

    /**
     * 验证请求参数
     */
    private void validateRequest(DeviceConfigRequest request) {
        if (request.getDeviceId() == null || request.getDeviceId().trim().isEmpty()) {
            throw new IllegalArgumentException("设备 ID 不能为空");
        }

        // 验证速度参数范围（0-1000，表示 0-100%）
        if (request.getWalkSpeed() != null &&
                (request.getWalkSpeed() < 0 || request.getWalkSpeed() > 1000)) {
            throw new IllegalArgumentException("行走速度必须在 0-1000 范围内");
        }

        if (request.getBrushSpeed() != null &&
                (request.getBrushSpeed() < 0 || request.getBrushSpeed() > 1000)) {
            throw new IllegalArgumentException("滚刷速度必须在 0-1000 范围内");
        }

        if (request.getBridgeSpeed() != null &&
                (request.getBridgeSpeed() < 0 || request.getBridgeSpeed() > 1000)) {
            throw new IllegalArgumentException("垮桥速度必须在 0-1000 范围内");
        }

        // 注意：不再严格验证 companyCode 和 model，因为它们可能在后端补充或不重要
        // 但建议至少检查 deviceId 的格式
    }

    private boolean sendWithBindingFlow(DeviceConfigRequest request) {
        String model = resolveModel(request);
        if (!shouldSyncBoundConfig(request, model)) {
            request.setBindStatus(resolveBoundPeerDeviceIds(model, request.getDeviceId()).isEmpty() ? 0 : 1);
            return railcarControlService.sendControlCommand(request);
        }

        List<String> peerDeviceIds = resolveBoundPeerDeviceIds(model, request.getDeviceId());
        if (peerDeviceIds.isEmpty()) {
            request.setBindStatus(0);
            return railcarControlService.sendControlCommand(request);
        }

        List<DeviceConfigRequest> peerRequests = new ArrayList<DeviceConfigRequest>();
        for (String peerDeviceId : peerDeviceIds) {
            DeviceConfigRequest peerRequest = buildBoundPeerRequest(peerDeviceId, peerDeviceIds);
            if (peerRequest == null) {
                log.error("绑定参数同步下发失败，缺少目标设备配置 - source={}, peer={}",
                        request.getDeviceId(), peerDeviceId);
                return false;
            }
            peerRequests.add(peerRequest);
        }

        request.setInfoCommandType(0);
        request.setBindStatus(1);
        request.setBindDeviceIds(peerDeviceIds);
        request.setBindDeviceId(peerDeviceIds.get(0));
        for (DeviceConfigRequest peerRequest : peerRequests) {
            if (!railcarControlService.sendControlCommand(peerRequest)) {
                log.error("绑定参数同步下发失败 - source={}, peer={}",
                        request.getDeviceId(), peerRequest.getDeviceId());
                return false;
            }
        }
        log.info("绑定参数同步下发完成 - source={}, peers={}", request.getDeviceId(), peerDeviceIds);
        String d12Serial = request.getDeviceId();
        int bindCount = peerDeviceIds.size();

        log.info("D12 设置帧仅下发到自身，不再同步下发绑定 D01 - d12Serial: {}, bindCount: {}",
                d12Serial, bindCount);
        return railcarControlService.sendControlCommand(request);
    }

    private int resolveCommandType(DeviceConfigRequest request) {
        Integer infoCommandType = request.getInfoCommandType();
        if (infoCommandType == null) {
            return 0;
        }
        if (infoCommandType < 0) {
            return 0;
        }
        return Math.min(infoCommandType, 2);
    }

    private String resolveModel(DeviceConfigRequest request) {
        if (request.getModel() != null && !request.getModel().trim().isEmpty()) {
            return request.getModel().trim();
        }
        String deviceId = request.getDeviceId();
        if (deviceId != null && deviceId.length() >= 4) {
            return deviceId.substring(0, 4);
        }
        return "";
    }

    private boolean shouldSyncBoundConfig(DeviceConfigRequest request, String model) {
        if (Boolean.TRUE.equals(request.getQuickAction())) {
            return false;
        }
        if (!"-D12".equals(model) && !"-D01".equals(model)) {
            return false;
        }
        return resolveCommandType(request) == 0;
    }

    private List<String> resolveBoundPeerDeviceIds(String model, String deviceId) {
        List<String> peers = new ArrayList<String>();
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return peers;
        }
        if ("-D12".equals(model)) {
            return normalizeBoundD01Serials(vehicleService.getBoundD01Serials(deviceId));
        }
        if ("-D01".equals(model)) {
            Vehicle boundD12 = vehicleService.findD12ByBoundD01Serial(deviceId);
            if (boundD12 != null && boundD12.getSerialNumber() != null && !boundD12.getSerialNumber().trim().isEmpty()) {
                peers.add(boundD12.getSerialNumber().trim());
            }
        }
        return peers;
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

    private DeviceConfigRequest buildBoundPeerRequest(String deviceId, List<String> peerDeviceIds) {
        RailcarConfig savedConfig = railcarConfigService.getConfigByDeviceId(deviceId);
        if (savedConfig == null) {
            return null;
        }

        DeviceConfigRequest peerRequest = new DeviceConfigRequest();
        peerRequest.setDeviceId(deviceId);
        peerRequest.setModel(resolveConfigModel(savedConfig, deviceId));
        peerRequest.setCompanyCode(resolvePeerCompanyCode(deviceId, savedConfig.getCompanyCode()));
        peerRequest.setInfoCommandType(0);
        peerRequest.setBindStatus(1);
        peerRequest.setBindDeviceIds(peerDeviceIds);
        peerRequest.setBindDeviceId(peerDeviceIds != null && !peerDeviceIds.isEmpty() ? peerDeviceIds.get(0) : "");
        applySavedConfig(peerRequest, savedConfig);
        return peerRequest;
    }

    private void applySavedConfig(DeviceConfigRequest request, RailcarConfig savedConfig) {
        request.setControlMode(savedConfig.getOperationMode());
        request.setEnableMode(savedConfig.getOperationEnable());
        request.setHeartbeatSet(savedConfig.getHeartbeatPulse());
        request.setBatteryLowLimit(savedConfig.getBatteryLowLimit());
        request.setReserved(savedConfig.getBackup());
        request.setReserved2(0);

        if (isD12Model(request.getModel())) {
            request.setRobotInPositionTime(savedConfig.getRobotInPositionTime());
            request.setLimitPositionCheckTime(savedConfig.getLimitPositionCheckTime());
            request.setWalkPositionCheckTime(savedConfig.getWalkPositionCheckTime());
            request.setWalkFastSpeed(savedConfig.getWalkFastSpeed());
            request.setWalkSlowSpeed(savedConfig.getWalkSlowSpeed());
            request.setMaxRowCount(savedConfig.getMaxRowCount());
            return;
        }

        request.setEdgeDelay(savedConfig.getEdgeDetectionDelay());
        request.setBridgeTime(savedConfig.getBridgeDetectionTime());
        request.setErrorReturnTime(savedConfig.getErrorReturnTime());
        request.setWalkSpeed(savedConfig.getWalkingSpeed());
        request.setBrushSpeed(savedConfig.getBrushSpeed());
        request.setBridgeSpeed(savedConfig.getBridgeSpeed());
    }

    private String resolveConfigModel(RailcarConfig savedConfig, String deviceId) {
        if (savedConfig.getProductModel() != null && !savedConfig.getProductModel().trim().isEmpty()) {
            return savedConfig.getProductModel().trim();
        }
        return deviceId != null && deviceId.length() >= 4 ? deviceId.substring(0, 4) : "";
    }

    private String resolvePeerCompanyCode(String deviceId, String fallbackCompanyCode) {
        if (fallbackCompanyCode != null && !fallbackCompanyCode.trim().isEmpty()) {
            return fallbackCompanyCode.trim();
        }
        Vehicle vehicle = vehicleService.getBySerialNumber(deviceId);
        if (vehicle != null && vehicle.getCompanyCode() != null && !vehicle.getCompanyCode().trim().isEmpty()) {
            return vehicle.getCompanyCode().trim();
        }
        return "ZTZN-PVC";
    }

    private boolean isD12Model(String model) {
        return "-D12".equals(model) || "-T12".equals(model);
    }

    private void populateLatencyMetrics(DeviceControlResponse response, DeviceConfigRequest request, String traceId) {
        response.setTraceId(traceId);
        response.setCommandId(request.getCommandId());
        if (response.getCommandStatus() == null || response.getCommandStatus().trim().isEmpty()) {
            response.setCommandStatus(Boolean.TRUE.equals(response.getSuccess()) ? "DISPATCHED" : "FAILED");
        }

        Long clientClickTimestamp = request.getClientClickTimestamp();
        Long serverReceivedTimestamp = request.getServerReceivedTimestamp();
        Long mqttSentTimestamp = request.getMqttSentTimestamp();

        if (clientClickTimestamp != null && serverReceivedTimestamp != null) {
            response.setClientToServerMs(Math.max(0L, serverReceivedTimestamp - clientClickTimestamp));
        }
        if (serverReceivedTimestamp != null && mqttSentTimestamp != null) {
            response.setServerToMqttMs(Math.max(0L, mqttSentTimestamp - serverReceivedTimestamp));
        }
        if (clientClickTimestamp != null && mqttSentTimestamp != null) {
            response.setClientToMqttMs(Math.max(0L, mqttSentTimestamp - clientClickTimestamp));
        }

        log.info("[latency][{}] 耗时汇总 - 设备: {}, clientToServer: {} ms, serverToMqtt: {} ms, clientToMqtt: {} ms",
                traceId,
                request.getDeviceId(),
                response.getClientToServerMs(),
                response.getServerToMqttMs(),
                response.getClientToMqttMs());
    }

    /**
     * 构建审计日志对象
     */
    private DeviceControlAudit buildAuditLog(
            DeviceConfigRequest request,
            String operationType,
            String result,
            String mqttTopic,
            String traceId,
            String errorMessage) {

        DeviceControlAudit audit = new DeviceControlAudit();
        audit.setDeviceId(request.getDeviceId());
        audit.setUserId(request.getUserId());
        audit.setUsername(request.getUsername());
        audit.setOperationType(operationType);
        audit.setMqttTopic(mqttTopic);
        audit.setOperationResult(result);
        audit.setMessage(errorMessage != null ? errorMessage : "操作成功");
        audit.setOperationTime(LocalDateTime.now());
        audit.setIsEmergencyMode(false); // 正常模式
        audit.setTraceId(traceId);
        audit.setExtraInfo(buildAuditExtraInfo(request));

        return audit;
    }

    private String buildAuditExtraInfo(DeviceConfigRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"commandId\":\"").append(safeJson(request.getCommandId())).append("\"");
        if (request.getTraceId() != null && !request.getTraceId().trim().isEmpty()) {
            builder.append(",\"traceId\":\"").append(safeJson(request.getTraceId())).append("\"");
        }
        if (request.getInfoCommandType() != null) {
            builder.append(",\"infoCommandType\":").append(request.getInfoCommandType());
        }
        if (request.getQuickAction() != null) {
            builder.append(",\"quickAction\":").append(request.getQuickAction());
        }
        builder.append("}");
        return builder.toString();
    }

    private String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String resolveDeviceType(DeviceConfigRequest request) {
        String model = resolveModel(request);
        if (model.startsWith("-T")) {
            return "T_PYTHON";
        }
        if (model.startsWith("-D")) {
            return "D_IOT";
        }
        return "UNKNOWN";
    }

    private String resolveActionName(DeviceConfigRequest request) {
        if (Boolean.TRUE.equals(request.getQuickAction())) {
            return "QUICK_ACTION";
        }
        if (resolveCommandType(request) == 2) {
            return "BINDING_RELAY";
        }
        if (resolveCommandType(request) == 1) {
            return "SPECIAL_CONFIG";
        }
        return "APPLY_CONFIG";
    }

    private Map<String, Object> buildCommandDetail(DeviceConfigRequest request) {
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("deviceId", request.getDeviceId());
        detail.put("deviceType", resolveDeviceType(request));
        detail.put("action", resolveActionName(request));
        detail.put("traceId", request.getTraceId());
        if (request.getInfoCommandType() != null) {
            detail.put("infoCommandType", request.getInfoCommandType());
        }
        if (request.getQuickAction() != null) {
            detail.put("quickAction", request.getQuickAction());
        }
        return detail;
    }

    private Map<String, Object> buildStatusDetail(DeviceConfigRequest request, String status) {
        Map<String, Object> detail = buildCommandDetail(request);
        detail.put("status", status);
        return detail;
    }
}
