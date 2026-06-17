package com.zt.cleanbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dto.CommandStatusSnapshot;
import com.zt.cleanbot.dto.DeviceShadowStatus;
import com.zt.cleanbot.dto.VehicleRedisData;
import com.zt.cleanbot.model.Vehicle;
import com.zt.cleanbot.utils.RedisUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一设备状态构建服务。
 * 第一阶段先作为只读标准状态出口，不接管现有业务控制逻辑。
 */
@Service
public class DeviceShadowService {

    private static final String DEVICE_TYPE_T_PYTHON = "T_PYTHON";
    private static final String DEVICE_TYPE_D_IOT = "D_IOT";
    private static final String DEVICE_TYPE_UNKNOWN = "UNKNOWN";
    private static final long DEVICE_OFFLINE_GRACE_MS = 60_000L;

    private final ObjectMapper objectMapper;
    private final VehicleService vehicleService;
    private final RedisUtil redisUtil;
    private final CommandStatusService commandStatusService;

    public DeviceShadowService(
            ObjectMapper objectMapper,
            VehicleService vehicleService,
            RedisUtil redisUtil,
            CommandStatusService commandStatusService) {
        this.objectMapper = objectMapper;
        this.vehicleService = vehicleService;
        this.redisUtil = redisUtil;
        this.commandStatusService = commandStatusService;
    }

    public DeviceShadowStatus getDeviceShadow(String serialNumber) {
        Vehicle vehicle = vehicleService.getBySerialNumber(serialNumber);
        VehicleRedisData redisData = readRedisData(serialNumber);
        return buildDeviceShadow(serialNumber, vehicle, redisData);
    }

    public DeviceShadowStatus buildDeviceShadow(String serialNumber, Vehicle vehicle, VehicleRedisData redisData) {
        DeviceShadowStatus shadow = new DeviceShadowStatus();
        shadow.setDeviceId(serialNumber);
        shadow.setSerialNumber(serialNumber);

        if (vehicle == null && redisData == null) {
            shadow.setExists(false);
            shadow.setOnlineState("OFFLINE");
            shadow.setMissionState("IDLE");
            shadow.setControlState("STOPPED");
            shadow.setHealthState("UNKNOWN");
            shadow.setFaultState("COMM_ERROR");
            shadow.setSupportedActions(Collections.<String>emptyList());
            shadow.setSupportedParams(Collections.<String>emptyList());
            shadow.setSupportedStatusFields(Collections.<String>emptyList());
            shadow.setDetail(Collections.<String, Object>emptyMap());
            return shadow;
        }

        shadow.setExists(true);
        if (vehicle != null) {
            shadow.setVehicleId(vehicle.getId());
            shadow.setProductType(vehicle.getProductType());
            shadow.setProductId(vehicle.getProductId());
            shadow.setCompanyCode(vehicle.getCompanyCode());
            shadow.setName(vehicle.getName());
            shadow.setVehicleType(vehicle.getVehicleType());
        }

        if (redisData != null) {
            if (shadow.getProductType() == null && redisData.getDeviceId() != null && redisData.getDeviceId().length() >= 4) {
                shadow.setProductType(redisData.getDeviceId().substring(0, 4));
            }
            shadow.setRawStatus(redisData.getStatus());
            shadow.setMqttMessageType(redisData.getMqttMessageType());
            shadow.setBattery(redisData.getBattery());
            shadow.setVoltage(redisData.getVoltage());
            shadow.setAngle(redisData.getAngle());
            shadow.setUpdatedAt(redisData.getLastUpdateTime());
            shadow.setLocation(buildLocation(redisData));
            shadow.setTaskOrigin(buildTaskOrigin(redisData));
            shadow.setCurrentLocation(buildCurrentLocation(redisData));
            shadow.setDistanceToTaskOriginM(redisData.getDistanceToTaskOriginM());
            shadow.setTaskOriginToleranceM(redisData.getTaskOriginToleranceM());
            shadow.setIsAtTaskOrigin(redisData.getIsAtTaskOrigin());
        }

        shadow.setDeviceType(resolveDeviceType(shadow.getProductType()));
        shadow.setOnlineState(resolveOnlineState(shadow.getProductType(), redisData));
        shadow.setMissionState(resolveMissionState(redisData, shadow.getOnlineState()));
        shadow.setControlState(resolveControlState(redisData, shadow.getOnlineState(), shadow.getMissionState()));
        shadow.setHealthState(resolveHealthState(redisData, shadow.getOnlineState()));
        shadow.setFaultState(resolveFaultState(redisData, shadow.getOnlineState()));
        CommandStatusSnapshot latestCommand = commandStatusService.getLatestCommandStatusByDevice(serialNumber);
        if (redisData != null) {
            shadow.setLastCommandId(redisData.getLastCommandId());
            shadow.setLastCommandStatus(redisData.getLastCommandStatus());
        }
        if ((shadow.getLastCommandId() == null || shadow.getLastCommandStatus() == null)
                && Boolean.TRUE.equals(latestCommand.getExists())) {
            shadow.setLastCommandId(latestCommand.getCommandId());
            shadow.setLastCommandStatus(latestCommand.getStatus());
        }
        shadow.setSupportedActions(resolveSupportedActions(shadow.getProductType(), redisData));
        shadow.setSupportedParams(resolveSupportedParams(shadow.getProductType(), redisData));
        shadow.setSupportedStatusFields(resolveSupportedStatusFields(shadow.getProductType(), redisData));
        shadow.setDetail(buildDetail(shadow.getProductType(), redisData, latestCommand));
        return shadow;
    }

    private VehicleRedisData readRedisData(String serialNumber) {
        Object redisObject = redisUtil.getVehicle(serialNumber);
        if (redisObject == null) {
            return null;
        }
        if (redisObject instanceof VehicleRedisData) {
            return (VehicleRedisData) redisObject;
        }
        return objectMapper.convertValue(redisObject, VehicleRedisData.class);
    }

    private DeviceShadowStatus.LocationData buildLocation(VehicleRedisData redisData) {
        if (redisData == null || redisData.getLocation() == null) {
            return null;
        }
        DeviceShadowStatus.LocationData location = new DeviceShadowStatus.LocationData();
        location.setLon(redisData.getLocation().getLon());
        location.setLat(redisData.getLocation().getLat());
        return location;
    }

    private DeviceShadowStatus.LocationData buildTaskOrigin(VehicleRedisData redisData) {
        if (redisData == null || redisData.getTaskOrigin() == null) {
            return null;
        }
        DeviceShadowStatus.LocationData location = new DeviceShadowStatus.LocationData();
        location.setLon(redisData.getTaskOrigin().getLon());
        location.setLat(redisData.getTaskOrigin().getLat());
        return location;
    }

    private DeviceShadowStatus.CurrentLocationData buildCurrentLocation(VehicleRedisData redisData) {
        if (redisData == null || redisData.getCurrentLocation() == null) {
            return null;
        }
        DeviceShadowStatus.CurrentLocationData location = new DeviceShadowStatus.CurrentLocationData();
        location.setLon(redisData.getCurrentLocation().getLon());
        location.setLat(redisData.getCurrentLocation().getLat());
        location.setHeading(redisData.getCurrentLocation().getHeading());
        return location;
    }

    private String resolveDeviceType(String productType) {
        if (productType == null || productType.trim().isEmpty()) {
            return DEVICE_TYPE_UNKNOWN;
        }
        if (productType.startsWith("-T")) {
            return DEVICE_TYPE_T_PYTHON;
        }
        if (productType.startsWith("-D")) {
            return DEVICE_TYPE_D_IOT;
        }
        return DEVICE_TYPE_UNKNOWN;
    }

    private String resolveOnlineState(String productType, VehicleRedisData redisData) {
        String explicitOnlineState = redisData == null ? null : redisData.getOnlineState();
        if ("OFFLINE".equalsIgnoreCase(trimToEmpty(explicitOnlineState))) {
            return "OFFLINE";
        }
        if (usesLastUpdateTimeout(productType)) {
            if (redisData == null || redisData.getLastUpdateTime() == null) {
                return "OFFLINE";
            }
            long ageMs = System.currentTimeMillis() - redisData.getLastUpdateTime();
            long offlineTimeoutMs = resolveDeviceOfflineTimeoutMs(redisData);
            return ageMs <= offlineTimeoutMs ? "ONLINE" : "OFFLINE";
        }
        if (hasText(explicitOnlineState)) {
            return explicitOnlineState;
        }
        if (redisData == null || redisData.getStatus() == null) {
            return "OFFLINE";
        }
        String rawStatus = redisData.getStatus().trim().toLowerCase();
        if ("offline".equals(rawStatus)) {
            return "OFFLINE";
        }
        return "ONLINE";
    }

    private boolean usesLastUpdateTimeout(String productType) {
        return productType != null && (productType.startsWith("-D") || productType.startsWith("-T"));
    }

    private long resolveDeviceOfflineTimeoutMs(VehicleRedisData redisData) {
        if (redisData == null || redisData.getHeartbeat() == null || redisData.getHeartbeat() <= 0) {
            return DEVICE_OFFLINE_GRACE_MS;
        }
        return Math.round(redisData.getHeartbeat() * 1000) + DEVICE_OFFLINE_GRACE_MS;
    }

    private String resolveMissionState(VehicleRedisData redisData, String onlineState) {
        if (redisData != null
                && hasText(redisData.getMissionState())
                && !isIgnoredEnableState(redisData.getMissionState())) {
            return redisData.getMissionState();
        }
        if (!"ONLINE".equals(onlineState) || redisData == null) {
            return "IDLE";
        }
        if (Boolean.TRUE.equals(redisData.getEnterGarage()) || "charging".equalsIgnoreCase(redisData.getStatus())) {
            return "CHARGING";
        }
        if (Boolean.TRUE.equals(redisData.getMoveJudge()) || isRunningStatus(redisData.getStatus())) {
            return "RUNNING";
        }
        return "IDLE";
    }

    private String resolveControlState(VehicleRedisData redisData, String onlineState, String missionState) {
        if (redisData != null
                && hasText(redisData.getControlState())
                && !isIgnoredEnableState(redisData.getControlState())) {
            return redisData.getControlState();
        }
        if (!"ONLINE".equals(onlineState)) {
            return "STOPPED";
        }
        if ("RUNNING".equals(missionState) || (redisData != null && Boolean.TRUE.equals(redisData.getMoveJudge()))) {
            return "BUSY";
        }
        return "READY";
    }

    private String resolveHealthState(VehicleRedisData redisData, String onlineState) {
        if (redisData != null
                && hasText(redisData.getHealthState())
                && !isIgnoredEnableFault(redisData.getFaultState())) {
            return redisData.getHealthState();
        }
        if (!"ONLINE".equals(onlineState)) {
            return "UNKNOWN";
        }
        if (isLowBattery(redisData)) {
            return "WARNING";
        }
        return "NORMAL";
    }

    private String resolveFaultState(VehicleRedisData redisData, String onlineState) {
        if (redisData != null
                && hasText(redisData.getFaultState())
                && !isIgnoredEnableFault(redisData.getFaultState())) {
            return redisData.getFaultState();
        }
        if (!"ONLINE".equals(onlineState)) {
            return "COMM_ERROR";
        }
        if (isLowBattery(redisData)) {
            return "LOW_BATTERY";
        }
        return "NONE";
    }

    private boolean isIgnoredEnableState(String state) {
        if (state == null) {
            return false;
        }
        String normalized = state.trim().toUpperCase();
        return "DISABLED".equals(normalized) || "UNKNOWN".equals(normalized);
    }

    private boolean isIgnoredEnableFault(String faultState) {
        if (faultState == null) {
            return false;
        }
        String normalized = faultState.trim().toUpperCase();
        return "LOWER_MACHINE_DISABLED".equals(normalized)
                || "LOWER_MACHINE_STATUS_UNKNOWN".equals(normalized);
    }

    private boolean isRunningStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toLowerCase();
        return "working".equals(normalized)
                || "running".equals(normalized)
                || "cleaning".equals(normalized);
    }

    private boolean isLowBattery(VehicleRedisData redisData) {
        if (redisData == null || redisData.getBattery() == null) {
            return false;
        }
        double threshold = redisData.getBatteryLowLimit() != null ? redisData.getBatteryLowLimit() : 20;
        return redisData.getBattery() <= threshold;
    }

    private List<String> resolveSupportedActions(String productType) {
        if (productType == null) {
            return Collections.emptyList();
        }
        if (productType.startsWith("-T")) {
            return Arrays.asList(
                    "GET_STATUS",
                    "START_CLEAN",
                    "PAUSE_TASK",
                    "RESUME_TASK",
                    "STOP_TASK",
                    "EMERGENCY_STOP",
                    "RETURN_HOME",
                    "ENTER_DOCK",
                    "EXIT_DOCK",
                    "MOVE_FORWARD",
                    "MOVE_BACKWARD",
                    "TURN_LEFT",
                    "TURN_RIGHT",
                    "JOYSTICK_MOVE",
                    "SET_SPEED",
                    "SET_BRUSH_SPEED",
                    "ENABLE_TRACKING",
                    "SET_PATH_MODE",
                    "SET_GARAGE_ENTRY",
                    "CREATE_TASK",
                    "SELECT_TASK",
                    "SAVE_TASK",
                    "SAVE_PARAMS");
        }
        if ("-D12".equals(productType)) {
            return Arrays.asList("GET_STATUS", "APPLY_CONFIG", "SET_MODE", "BIND_DEVICE", "UNBIND_DEVICE");
        }
        if (productType.startsWith("-D")) {
            return Arrays.asList("GET_STATUS", "APPLY_CONFIG", "SET_MODE");
        }
        return Collections.emptyList();
    }

    private List<String> resolveSupportedActions(String productType, VehicleRedisData redisData) {
        if (redisData != null && redisData.getSupportedActions() != null && !redisData.getSupportedActions().isEmpty()) {
            return redisData.getSupportedActions();
        }
        return resolveSupportedActions(productType);
    }

    private List<String> resolveSupportedParams(String productType) {
        if (productType == null) {
            return Collections.emptyList();
        }
        if (productType.startsWith("-T")) {
            return Arrays.asList("speed", "brush_speed", "tracking", "path_planning", "task_params");
        }
        if ("-D12".equals(productType)) {
            return Arrays.asList(
                    "bindDeviceIds",
                    "workWay",
                    "time1",
                    "time2",
                    "time3",
                    "time4",
                    "controlMode",
                    "enableMode",
                    "walkSpeed",
                    "brushSpeed",
                    "bridgeSpeed",
                    "heartbeat",
                    "batteryLowLimit",
                    "robotInPositionTime",
                    "limitPositionCheckTime",
                    "walkPositionCheckTime",
                    "walkFastSpeed",
                    "walkSlowSpeed",
                    "maxRowCount",
                    "leftRowStart",
                    "leftRowEnd",
                    "rightRowStart",
                    "rightRowEnd");
        }
        if (productType.startsWith("-D")) {
            return Arrays.asList(
                    "workWay",
                    "time1",
                    "time2",
                    "time3",
                    "time4",
                    "controlMode",
                    "enableMode",
                    "walkSpeed",
                    "brushSpeed",
                    "bridgeSpeed",
                    "edgeDelay",
                    "bridgeTime",
                    "errorReturnTime",
                    "heartbeat",
                    "batteryLowLimit");
        }
        return Collections.emptyList();
    }

    private List<String> resolveSupportedParams(String productType, VehicleRedisData redisData) {
        if (redisData != null && redisData.getSupportedParams() != null && !redisData.getSupportedParams().isEmpty()) {
            return redisData.getSupportedParams();
        }
        return resolveSupportedParams(productType);
    }

    private List<String> resolveSupportedStatusFields(String productType) {
        List<String> fields = new ArrayList<String>(Arrays.asList(
                "online_state",
                "mission_state",
                "control_state",
                "health_state",
                "fault_state",
                "battery",
                "updated_at",
                "location"));
        if (productType == null) {
            return fields;
        }
        if (productType.startsWith("-T")) {
            fields.addAll(Arrays.asList(
                    "voltage",
                    "angle",
                    "tracking",
                    "path_planning",
                    "left_edge",
                    "right_edge",
                    "move_judge",
                    "detect_qrcode",
                    "enter_garage",
                    "task_origin",
                    "current_location",
                    "distance_to_task_origin_m",
                    "task_origin_tolerance_m",
                    "is_at_task_origin"));
        } else if ("-D12".equals(productType)) {
            fields.addAll(Arrays.asList(
                    "run_control",
                    "run_enable",
                    "work_mode",
                    "walk_speed",
                    "brush_speed",
                    "bridge_speed",
                    "run_time_single",
                    "run_time_total",
                    "mileage_single",
                    "mileage_total",
                    "heartbeat",
                    "current_row_position",
                    "left_row_start",
                    "left_row_end",
                    "right_row_start",
                    "right_row_end"));
        } else if (productType.startsWith("-D")) {
            fields.addAll(Arrays.asList(
                    "run_control",
                    "run_enable",
                    "work_mode",
                    "walk_speed",
                    "brush_speed",
                    "bridge_speed",
                    "run_time_single",
                    "run_time_total",
                    "mileage_single",
                    "mileage_total",
                    "heartbeat"));
        }
        return fields;
    }

    private List<String> resolveSupportedStatusFields(String productType, VehicleRedisData redisData) {
        List<String> defaults = resolveSupportedStatusFields(productType);
        if (redisData != null
                && redisData.getSupportedStatusFields() != null
                && !redisData.getSupportedStatusFields().isEmpty()) {
            List<String> merged = new ArrayList<String>(redisData.getSupportedStatusFields());
            for (String field : defaults) {
                if (!merged.contains(field)) {
                    merged.add(field);
                }
            }
            return merged;
        }
        return defaults;
    }

    private Map<String, Object> buildDetail(
            String productType,
            VehicleRedisData redisData,
            CommandStatusSnapshot latestCommand) {
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        if (redisData != null && redisData.getShadowDetail() != null) {
            detail.putAll(redisData.getShadowDetail());
            detail.remove("powerOnState");
            detail.remove("powerOnEnabled");
            detail.remove("lowerMachineStatusWarning");
            detail.remove("lowerMachineStartBypass");
        }
        if (redisData != null) {
            putIfNotNull(detail, "raw_status", redisData.getStatus());
            putIfNotNull(detail, "mqtt_message_type", redisData.getMqttMessageType());
        }

        if (redisData != null) {
            if (productType != null && productType.startsWith("-T")) {
                putIfNotNull(detail, "tracking", redisData.getTracking());
                putIfNotNull(detail, "path_planning", redisData.getPathPlanning());
                putIfNotNull(detail, "left_edge", redisData.getLeftEdge());
                putIfNotNull(detail, "right_edge", redisData.getRightEdge());
                putIfNotNull(detail, "move_judge", redisData.getMoveJudge());
                putIfNotNull(detail, "detect_qrcode", redisData.getDetectQrcode());
                putIfNotNull(detail, "enter_garage", redisData.getEnterGarage());
                putIfNotNull(detail, "voltage", redisData.getVoltage());
                putIfNotNull(detail, "angle", redisData.getAngle());
                putIfNotNull(detail, "task_origin", redisData.getTaskOrigin());
                putIfNotNull(detail, "current_location", redisData.getCurrentLocation());
                putIfNotNull(detail, "distance_to_task_origin_m", redisData.getDistanceToTaskOriginM());
                putIfNotNull(detail, "task_origin_tolerance_m", redisData.getTaskOriginToleranceM());
                putIfNotNull(detail, "is_at_task_origin", redisData.getIsAtTaskOrigin());
                putIfNotNull(detail, "last_command_id", redisData.getLastCommandId());
                putIfNotNull(detail, "last_trace_id", redisData.getLastTraceId());
                putIfNotNull(detail, "last_command", redisData.getLastCommand());
                putIfNotNull(detail, "last_command_status", redisData.getLastCommandStatus());
                putIfNotNull(detail, "last_command_message", redisData.getLastCommandMessage());
            } else {
                putIfNotNull(detail, "run_control", redisData.getRunControl());
                putIfNotNull(detail, "run_enable", redisData.getRunEnable());
                putIfNotNull(detail, "work_mode", redisData.getWorkMode());
                putIfNotNull(detail, "walk_speed", redisData.getWalkSpeed());
                putIfNotNull(detail, "brush_speed", redisData.getBrushSpeed());
                putIfNotNull(detail, "bridge_speed", redisData.getBridgeSpeed());
                putIfNotNull(detail, "run_time_single", redisData.getRunTimeSingle());
                putIfNotNull(detail, "run_time_total", redisData.getRunTimeTotal());
                putIfNotNull(detail, "mileage_single", redisData.getMileageSingle());
                putIfNotNull(detail, "mileage_total", redisData.getMileageTotal());
                putIfNotNull(detail, "heartbeat", redisData.getHeartbeat());
                putIfNotNull(detail, "d12_work_way", redisData.getD12WorkWay());
                putIfNotNull(detail, "left_row_start", redisData.getLeftRowStart());
                putIfNotNull(detail, "left_row_end", redisData.getLeftRowEnd());
                putIfNotNull(detail, "right_row_start", redisData.getRightRowStart());
                putIfNotNull(detail, "right_row_end", redisData.getRightRowEnd());
                putIfNotNull(detail, "walk_fast_speed", redisData.getWalkFastSpeed());
                putIfNotNull(detail, "walk_slow_speed", redisData.getWalkSlowSpeed());
                putIfNotNull(detail, "current_row_position", redisData.getCurrentRowPosition());
                putIfNotNull(detail, "battery_low_limit", redisData.getBatteryLowLimit());
                putIfNotNull(detail, "robot_in_position_time", redisData.getRobotInPositionTime());
                putIfNotNull(detail, "limit_position_check_time", redisData.getLimitPositionCheckTime());
                putIfNotNull(detail, "walk_position_check_time", redisData.getWalkPositionCheckTime());
                putIfNotNull(detail, "last_command_id", redisData.getLastCommandId());
                putIfNotNull(detail, "last_trace_id", redisData.getLastTraceId());
                putIfNotNull(detail, "last_command", redisData.getLastCommand());
                putIfNotNull(detail, "last_command_status", redisData.getLastCommandStatus());
                putIfNotNull(detail, "last_command_message", redisData.getLastCommandMessage());
            }
        }

        if (Boolean.TRUE.equals(latestCommand.getExists())) {
            putIfNotNull(detail, "command_status_snapshot", latestCommand);
        }
        return detail.isEmpty() ? Collections.<String, Object>emptyMap() : detail;
    }

    private void putIfNotNull(Map<String, Object> detail, String key, Object value) {
        if (value != null) {
            detail.put(key, value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
