package com.zt.cleanbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dto.CommandStatusSnapshot;
import com.zt.cleanbot.utils.RedisUtil;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 命令状态快照服务。
 * 第一阶段以 Redis 快照方式提供统一命令生命周期查询，不影响现有控制流程。
 */
@Service
public class CommandStatusService {

    private static final long COMMAND_STATUS_EXPIRE_SECONDS = 7L * 24 * 60 * 60;
    private static final String COMMAND_STATUS_KEY_PREFIX = "command:status:";
    private static final String DEVICE_LATEST_COMMAND_KEY_PREFIX = "device:last_command:";

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    public CommandStatusService(RedisUtil redisUtil, ObjectMapper objectMapper) {
        this.redisUtil = redisUtil;
        this.objectMapper = objectMapper;
    }

    public CommandStatusSnapshot initializeCommand(
            String commandId,
            String traceId,
            String deviceId,
            String deviceType,
            String action,
            String operator,
            Long timeoutMs,
            Map<String, Object> detail) {
        CommandStatusSnapshot snapshot = new CommandStatusSnapshot();
        snapshot.setExists(true);
        snapshot.setCommandId(commandId);
        snapshot.setTraceId(traceId);
        snapshot.setDeviceId(deviceId);
        snapshot.setDeviceType(deviceType);
        snapshot.setAction(action);
        snapshot.setOperator(operator);
        snapshot.setTimeoutMs(timeoutMs);
        snapshot.setStatus("CREATED");
        snapshot.setMessage("命令已创建");
        snapshot.setCreatedAt(System.currentTimeMillis());
        snapshot.setUpdatedAt(snapshot.getCreatedAt());
        snapshot.setTerminal(false);
        snapshot.setDetail(detail != null ? new LinkedHashMap<String, Object>(detail) : Collections.<String, Object>emptyMap());
        saveSnapshot(snapshot);
        return snapshot;
    }

    public CommandStatusSnapshot markDispatched(String commandId, String message, Map<String, Object> detail) {
        return updateStatus(commandId, "DISPATCHED", message, false, detail);
    }

    public CommandStatusSnapshot markAccepted(String commandId, String message, Map<String, Object> detail) {
        return updateStatus(commandId, "ACCEPTED", message, false, detail);
    }

    public CommandStatusSnapshot markRunning(String commandId, String message, Map<String, Object> detail) {
        return updateStatus(commandId, "RUNNING", message, false, detail);
    }

    public CommandStatusSnapshot markSucceeded(String commandId, String message, Map<String, Object> detail) {
        return updateStatus(commandId, "SUCCEEDED", message, true, detail);
    }

    public CommandStatusSnapshot markFailed(String commandId, String message, Map<String, Object> detail) {
        return updateStatus(commandId, "FAILED", message, true, detail);
    }

    public CommandStatusSnapshot markRejected(String commandId, String message, Map<String, Object> detail) {
        return updateStatus(commandId, "REJECTED", message, true, detail);
    }

    public CommandStatusSnapshot markTimeout(String commandId, String message, Map<String, Object> detail) {
        return updateStatus(commandId, "TIMEOUT", message, true, detail);
    }

    public CommandStatusSnapshot getCommandStatus(String commandId) {
        if (commandId == null || commandId.trim().isEmpty()) {
            return buildMissingSnapshot(null);
        }
        Object cached = redisUtil.get(buildCommandStatusKey(commandId));
        if (cached == null) {
            return buildMissingSnapshot(commandId);
        }
        if (cached instanceof CommandStatusSnapshot) {
            CommandStatusSnapshot snapshot = (CommandStatusSnapshot) cached;
            snapshot.setExists(true);
            return snapshot;
        }
        CommandStatusSnapshot snapshot = objectMapper.convertValue(cached, CommandStatusSnapshot.class);
        snapshot.setExists(true);
        return snapshot;
    }

    public CommandStatusSnapshot getLatestCommandStatusByDevice(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return buildMissingSnapshot(null);
        }
        Object latestCommandId = redisUtil.get(buildDeviceLatestCommandKey(deviceId));
        if (latestCommandId == null) {
            return buildMissingSnapshot(null);
        }
        return getCommandStatus(String.valueOf(latestCommandId));
    }

    private CommandStatusSnapshot updateStatus(
            String commandId,
            String status,
            String message,
            boolean terminal,
            Map<String, Object> detail) {
        CommandStatusSnapshot snapshot = getCommandStatus(commandId);
        if (Boolean.FALSE.equals(snapshot.getExists())) {
            snapshot.setExists(true);
            snapshot.setCommandId(commandId);
            snapshot.setCreatedAt(System.currentTimeMillis());
        }
        snapshot.setStatus(status);
        snapshot.setMessage(message);
        snapshot.setTerminal(terminal);
        snapshot.setUpdatedAt(System.currentTimeMillis());
        if (detail != null && !detail.isEmpty()) {
            Map<String, Object> mergedDetail = snapshot.getDetail() != null
                    ? new LinkedHashMap<String, Object>(snapshot.getDetail())
                    : new LinkedHashMap<String, Object>();
            mergedDetail.putAll(detail);
            snapshot.setDetail(mergedDetail);
            if (snapshot.getTraceId() == null && detail.get("traceId") != null) {
                snapshot.setTraceId(String.valueOf(detail.get("traceId")));
            }
            if (snapshot.getAction() == null && detail.get("action") != null) {
                snapshot.setAction(String.valueOf(detail.get("action")));
            }
            if (snapshot.getDeviceId() == null && detail.get("deviceId") != null) {
                snapshot.setDeviceId(String.valueOf(detail.get("deviceId")));
            }
            if (snapshot.getDeviceType() == null && detail.get("deviceType") != null) {
                snapshot.setDeviceType(String.valueOf(detail.get("deviceType")));
            }
        }
        saveSnapshot(snapshot);
        return snapshot;
    }

    private void saveSnapshot(CommandStatusSnapshot snapshot) {
        redisUtil.set(buildCommandStatusKey(snapshot.getCommandId()), snapshot, COMMAND_STATUS_EXPIRE_SECONDS, TimeUnit.SECONDS);
        if (snapshot.getDeviceId() != null && !snapshot.getDeviceId().trim().isEmpty()) {
            redisUtil.set(buildDeviceLatestCommandKey(snapshot.getDeviceId()), snapshot.getCommandId(), COMMAND_STATUS_EXPIRE_SECONDS, TimeUnit.SECONDS);
        }
    }

    private CommandStatusSnapshot buildMissingSnapshot(String commandId) {
        CommandStatusSnapshot snapshot = new CommandStatusSnapshot();
        snapshot.setExists(false);
        snapshot.setCommandId(commandId);
        snapshot.setStatus("UNKNOWN");
        snapshot.setMessage("命令状态不存在");
        snapshot.setTerminal(false);
        snapshot.setDetail(Collections.<String, Object>emptyMap());
        return snapshot;
    }

    private String buildCommandStatusKey(String commandId) {
        return COMMAND_STATUS_KEY_PREFIX + commandId;
    }

    private String buildDeviceLatestCommandKey(String deviceId) {
        return DEVICE_LATEST_COMMAND_KEY_PREFIX + deviceId;
    }
}
