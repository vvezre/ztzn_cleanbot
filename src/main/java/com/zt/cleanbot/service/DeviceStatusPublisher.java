package com.zt.cleanbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.model.RailcarMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * 设备状态发布服务
 * 将设备状态统一发布到 Redis Pub/Sub，再由 RedisWebSocketForwarder 转发到 WebSocket。
 * 这样可以避免重复推送，并兼容后续多实例部署。
 */
@Slf4j
@Service
public class DeviceStatusPublisher {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 发布设备状态更新
     *
     * @param serialNumber 设备序列号（从 MQTT Topic 提取）
     * @param message      轨道车状态消息
     */
    public void publishDeviceStatus(String serialNumber, RailcarMessage message) {
        try {
            // 构建完整的设备状态通知（包含所有工作参数）
            DeviceStatusNotification notification = new DeviceStatusNotification();

            // 基本信息
            notification.setDeviceId(serialNumber);
            notification.setBattery(message.getBatteryLevel());
            notification.setStatus(parseStatus(message));
            notification.setTimestamp(System.currentTimeMillis());

            // GPS 位置
            if (message.getLongitude() != null || message.getLatitude() != null) {
                notification.setLocation(new Location(
                        message.getLongitude(),
                        message.getLatitude()));
            }

            // 工作模式参数
            if (message.getOperationMode() != null) {
                notification.setRunControl(Integer.parseInt(message.getOperationMode(), 16));
            }
            if (message.getOperationEnable() != null) {
                notification.setRunEnable(Integer.parseInt(message.getOperationEnable(), 16));
            }
            if (message.getWorkMode() != null) {
                notification.setWorkMode(Integer.parseInt(message.getWorkMode(), 16));
            }

            // 速度参数
            if (message.getCurrentSpeed() != null) {
                notification.setWalkSpeed(message.getCurrentSpeed().intValue());
            }
            if (message.getBrushSpeed() != null) {
                notification.setBrushSpeed(message.getBrushSpeed().intValue());
            }
            if (message.getBridgeSpeed() != null) {
                notification.setBridgeSpeed(message.getBridgeSpeed().intValue());
            }

            // 运行数据
            notification.setRunTimeSingle(message.getSingleRunTime());
            notification.setRunTimeTotal(message.getTotalRunTime());
            notification.setMileageSingle(message.getSingleRunDistance());
            notification.setMileageTotal(message.getTotalRunDistance());
            notification.setHeartbeat(message.getHeartbeat());

            // D12 接驳车特有字段
            if (message.getD12WorkWay() != null) {
                notification.setD12WorkWay(Integer.parseInt(message.getD12WorkWay(), 16));
            }
            notification.setLeftRowStart(message.getLeftRowStart());
            notification.setLeftRowEnd(message.getLeftRowEnd());
            notification.setRightRowStart(message.getRightRowStart());
            notification.setRightRowEnd(message.getRightRowEnd());
            if (message.getWalkFastSpeed() != null) {
                notification.setWalkFastSpeed(message.getWalkFastSpeed().intValue());
            }
            if (message.getWalkSlowSpeed() != null) {
                notification.setWalkSlowSpeed(message.getWalkSlowSpeed().intValue());
            }
            notification.setCurrentRowPosition(message.getCurrentRowPosition());
            if (message.getBatteryLowLimit() != null) {
                notification.setBatteryLowLimit(message.getBatteryLowLimit().intValue());
            }
            notification.setRobotInPositionTime(message.getRobotInPositionTime());
            notification.setLimitPositionCheckTime(message.getLimitPositionCheckTime());
            notification.setWalkPositionCheckTime(message.getWalkPositionCheckTime());

            publishNotification(serialNumber, notification);

        } catch (Exception e) {
            log.error("发布设备状态失败 - 设备: {}", serialNumber, e);
        }
    }

    /**
     * 从 Redis 状态对象发布到小程序/App（适配 T 型 JSON 上报）
     */
    @SuppressWarnings("unchecked")
    public void publishDeviceStatus(String serialNumber, Map<String, Object> redisData) {
        try {
            if (serialNumber != null) {
                Map<String, Object> notification = new java.util.LinkedHashMap<>();
                notification.put("deviceId", serialNumber);
                notification.put("deviceType", serialNumber.startsWith("-T") ? "T_PYTHON" : "D_IOT");
                notification.put("companyCode", toStringValue(redisData.get("companyCode")));
                notification.put("productModel", toStringValue(redisData.get("productModel")));
                notification.put("productId", toStringValue(redisData.get("productId")));
                notification.put("battery", toDouble(redisData.get("battery")));
                notification.put("batteryRaw", toDouble(redisData.get("batteryRaw")));
                notification.put("onlineState", toStringValue(redisData.get("onlineState")));
                notification.put("missionState", toStringValue(redisData.get("missionState")));
                notification.put("controlState", toStringValue(redisData.get("controlState")));
                notification.put("healthState", toStringValue(redisData.get("healthState")));
                notification.put("faultState", toStringValue(redisData.get("faultState")));
                notification.put("speed", firstInteger(redisData.get("speed"), redisData.get("walkSpeed")));
                notification.put("walkSpeed", toInteger(redisData.get("walkSpeed")));
                notification.put("brushSpeed", toInteger(redisData.get("brushSpeed")));
                notification.put("voltage", toDouble(redisData.get("voltage")));
                notification.put("lat", resolveLat(redisData));
                notification.put("lon", resolveLon(redisData));
                notification.put("heading", toDouble(redisData.get("heading")));
                notification.put("localX", toInteger(redisData.get("localX")));
                notification.put("localY", toInteger(redisData.get("localY")));
                notification.put("status", toStringValue(redisData.get("status")));
                notification.put("statusNormalized", normalizeStatus(toStringValue(redisData.get("status"))));
                notification.put("action", toStringValue(redisData.get("action")));
                notification.put("taskName", toStringValue(redisData.get("taskName")));
                notification.put("curTaskIndex", toInteger(redisData.get("curTaskIndex")));
                notification.put("taskCount", toInteger(redisData.get("taskCount")));
                notification.put("taskOrigin", redisData.get("taskOrigin"));
                notification.put("currentLocation", redisData.get("currentLocation"));
                notification.put("distanceToTaskOriginM", toDouble(redisData.get("distanceToTaskOriginM")));
                notification.put("taskOriginToleranceM", toDouble(redisData.get("taskOriginToleranceM")));
                notification.put("isAtTaskOrigin", toBoolean(redisData.get("isAtTaskOrigin")));
                notification.put("tracking", toBoolean(redisData.get("tracking")));
                notification.put("pathPlanning", toStringValue(redisData.get("pathPlanning")));
                notification.put("lastCommandId", toStringValue(redisData.get("lastCommandId")));
                notification.put("lastCommandStatus", toStringValue(redisData.get("lastCommandStatus")));
                notification.put("lastCommandMessage", toStringValue(redisData.get("lastCommandMessage")));
                notification.put("supportedActions", redisData.get("supportedActions"));
                notification.put("supportedParams", redisData.get("supportedParams"));
                notification.put("supportedStatusFields", redisData.get("supportedStatusFields"));
                notification.put("shadowDetail", redisData.get("shadowDetail"));
                notification.put("updatedAt", toLong(redisData.get("lastUpdateTime"), null));
                notification.put("timestamp", firstLong(redisData.get("timestamp"), redisData.get("lastUpdateTime"), System.currentTimeMillis()));
                publishSimpleNotification(serialNumber, notification);
                return;
            }

            DeviceStatusNotification notification = new DeviceStatusNotification();
            notification.setDeviceId(serialNumber);
            notification.setBattery(toDouble(redisData.get("battery")));
            notification.setStatus(normalizeStatus(toStringValue(redisData.get("status"))));
            notification.setTimestamp(toLong(redisData.get("lastUpdateTime"), System.currentTimeMillis()));

            Object locationRaw = redisData.get("location");
            if (locationRaw instanceof Map) {
                Map<String, Object> locationMap = (Map<String, Object>) locationRaw;
                notification.setLocation(new Location(
                        toDouble(locationMap.get("lon")),
                        toDouble(locationMap.get("lat"))));
            }
            boolean hasValidRealtimeLocation = hasValidRealtimeLocation(notification.getLocation());

            notification.setRunControl(toInteger(redisData.get("runControl")));
            notification.setRunEnable(toInteger(redisData.get("runEnable")));
            notification.setWorkMode(toInteger(redisData.get("workMode")));
            notification.setWalkSpeed(toInteger(redisData.get("walkSpeed")));
            notification.setBrushSpeed(toInteger(redisData.get("brushSpeed")));
            notification.setBridgeSpeed(toInteger(redisData.get("bridgeSpeed")));
            notification.setRunTimeSingle(toDouble(redisData.get("runTimeSingle")));
            notification.setRunTimeTotal(toDouble(redisData.get("runTimeTotal")));
            notification.setMileageSingle(toDouble(redisData.get("mileageSingle")));
            notification.setMileageTotal(toDouble(redisData.get("mileageTotal")));
            notification.setHeartbeat(toDouble(redisData.get("heartbeat")));

            notification.setD12WorkWay(toInteger(redisData.get("d12WorkWay")));
            notification.setLeftRowStart(toInteger(redisData.get("leftRowStart")));
            notification.setLeftRowEnd(toInteger(redisData.get("leftRowEnd")));
            notification.setRightRowStart(toInteger(redisData.get("rightRowStart")));
            notification.setRightRowEnd(toInteger(redisData.get("rightRowEnd")));
            notification.setWalkFastSpeed(toInteger(redisData.get("walkFastSpeed")));
            notification.setWalkSlowSpeed(toInteger(redisData.get("walkSlowSpeed")));
            notification.setCurrentRowPosition(toInteger(redisData.get("currentRowPosition")));
            notification.setBatteryLowLimit(toInteger(redisData.get("batteryLowLimit")));
            notification.setRobotInPositionTime(toInteger(redisData.get("robotInPositionTime")));
            notification.setLimitPositionCheckTime(toInteger(redisData.get("limitPositionCheckTime")));
            notification.setWalkPositionCheckTime(toInteger(redisData.get("walkPositionCheckTime")));

            // T 型状态字段
            notification.setVoltage(toDouble(redisData.get("voltage")));
            notification.setAngle(toDouble(redisData.get("angle")));
            notification.setTracking(toBoolean(redisData.get("tracking")));
            notification.setPathPlanning(toStringValue(redisData.get("pathPlanning")));
            notification.setLeftEdge(toInteger(redisData.get("leftEdge")));
            notification.setRightEdge(toInteger(redisData.get("rightEdge")));
            notification.setMoveJudge(toBoolean(redisData.get("moveJudge")));
            notification.setDetectQrcode(toBoolean(redisData.get("detectQrcode")));
            notification.setEnterGarage(toBoolean(redisData.get("enterGarage")));
            notification.setMqttMessageType(toStringValue(redisData.get("mqttMessageType")));
            notification.setLastCommandId(toStringValue(redisData.get("lastCommandId")));
            notification.setLastCommandStatus(toStringValue(redisData.get("lastCommandStatus")));
            notification.setHeading(hasValidRealtimeLocation ? toDouble(redisData.get("heading")) : null);
            notification.setLocalX(hasValidRealtimeLocation ? toInteger(redisData.get("localX")) : null);
            notification.setLocalY(hasValidRealtimeLocation ? toInteger(redisData.get("localY")) : null);
            notification.setCurTaskIndex(toInteger(redisData.get("curTaskIndex")));

            publishNotification(serialNumber, notification);
        } catch (Exception e) {
            log.error("发布 Redis 状态到 WebSocket 失败 - 设备: {}", serialNumber, e);
        }
    }

    private void publishSimpleNotification(String serialNumber, Map<String, Object> notification) throws Exception {
        String json = objectMapper.writeValueAsString(notification);

        String channel = "device:status:update:" + serialNumber;
        redisTemplate.convertAndSend(channel, json);
        log.info("设备状态已发布到 Redis Channel - 设备: {}, Channel: {}", serialNumber, channel);
    }

    private void publishNotification(String serialNumber, DeviceStatusNotification notification) throws Exception {
        String json = objectMapper.writeValueAsString(notification);

        String channel = "device:status:update:" + serialNumber;
        redisTemplate.convertAndSend(channel, json);
        log.info("设备状态已发布到 Redis Channel - 设备: {}, Channel: {}", serialNumber, channel);
    }

    /**
     * 解析设备状态
     */
    private String parseStatus(RailcarMessage message) {
        String operationMode = message.getOperationModeDescription();
        if (operationMode == null) {
            return "idle";
        }

        String normalizedOperationMode = operationMode.toUpperCase(Locale.ROOT);
        if (normalizedOperationMode.contains("AUTO")
                || normalizedOperationMode.contains("CONTINUOUS")
                || normalizedOperationMode.contains("START")) {
            return "running";
        }
        if (normalizedOperationMode.contains("INVALID")
                || normalizedOperationMode.contains("STOP")
                || normalizedOperationMode.contains("RESET")
                || normalizedOperationMode.contains("MANUAL")) {
            return "idle";
        }

        if (operationMode.contains("停止") || operationMode.contains("无效")) {
            return "idle";
        } else if (operationMode.contains("启动") || operationMode.contains("循环")) {
            return "running";
        } else if (operationMode.contains("手动")) {
            return "idle";
        } else if (operationMode.contains("复位")) {
            return "idle";
        } else {
            return "idle";
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isEmpty()) {
            return "offline";
        }

        switch (status) {
            case "working":
            case "cleaning":
            case "running":
                return "running";
            case "charging":
                return "charging";
            case "active":
            case "idle":
            case "stopped":
            case "manual":
            case "resetting":
            case "unknown":
            case "disabled":
                return "idle";
            case "offline":
                return "offline";
            default:
                return "idle";
        }
    }

    private Integer toInteger(Object value) {
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

    private Double toDouble(Object value) {
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

    @SuppressWarnings("unchecked")
    private Double resolveLat(Map<String, Object> redisData) {
        Double lat = toDouble(redisData.get("lat"));
        if (lat != null) {
            return lat;
        }
        Object locationRaw = redisData.get("location");
        if (locationRaw instanceof Map) {
            return toDouble(((Map<String, Object>) locationRaw).get("lat"));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Double resolveLon(Map<String, Object> redisData) {
        Double lon = toDouble(redisData.get("lon"));
        if (lon != null) {
            return lon;
        }
        Object locationRaw = redisData.get("location");
        if (locationRaw instanceof Map) {
            return toDouble(((Map<String, Object>) locationRaw).get("lon"));
        }
        return null;
    }

    private Integer firstInteger(Object primary, Object fallback) {
        Integer value = toInteger(primary);
        return value != null ? value : toInteger(fallback);
    }

    private Long firstLong(Object primary, Object fallback, Long defaultValue) {
        Long value = toLong(primary, null);
        if (value != null) {
            return value;
        }
        value = toLong(fallback, null);
        return value != null ? value : defaultValue;
    }

    private Long toLong(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String s = String.valueOf(value);
        if ("true".equalsIgnoreCase(s) || "1".equals(s)) {
            return true;
        }
        if ("false".equalsIgnoreCase(s) || "0".equals(s)) {
            return false;
        }
        return null;
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        return s.isEmpty() ? null : s;
    }

    private boolean hasValidRealtimeLocation(Location location) {
        if (location == null || location.getLat() == null || location.getLon() == null) {
            return false;
        }
        return !(Math.abs(location.getLat()) < 1e-9 && Math.abs(location.getLon()) < 1e-9);
    }

    /**
     * 设备状态通知（完整的设备状态数据）
     */
    public static class DeviceStatusNotification {
        // 基本信息
        private String deviceId;
        private Double battery;
        private String status;
        private Location location;
        private String operationMode;
        private Long timestamp;

        // 工作模式参数
        private Integer runControl;
        private Integer runEnable;
        private Integer workMode;

        // 速度参数
        private Integer walkSpeed;
        private Integer brushSpeed;
        private Integer bridgeSpeed;

        // 运行数据
        private Double runTimeSingle;
        private Double runTimeTotal;
        private Double mileageSingle;
        private Double mileageTotal;
        private Double heartbeat;

        // T 型设备状态字段
        private Double voltage;
        private Double angle;
        private Double heading;
        private Boolean tracking;
        private String pathPlanning;
        private Integer leftEdge;
        private Integer rightEdge;
        private Boolean moveJudge;
        private Boolean detectQrcode;
        private Boolean enterGarage;
        private String mqttMessageType;
        private String lastCommandId;
        private String lastCommandStatus;
        private Integer localX;
        private Integer localY;
        private Integer curTaskIndex;

        // D12 特有字段
        private Integer d12WorkWay;
        private Integer leftRowStart;
        private Integer leftRowEnd;
        private Integer rightRowStart;
        private Integer rightRowEnd;
        private Integer walkFastSpeed;
        private Integer walkSlowSpeed;
        private Integer currentRowPosition;
        private Integer batteryLowLimit;
        private Integer robotInPositionTime;
        private Integer limitPositionCheckTime;
        private Integer walkPositionCheckTime;

        // Getters and Setters
        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public Double getBattery() {
            return battery;
        }

        public void setBattery(Double battery) {
            this.battery = battery;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Location getLocation() {
            return location;
        }

        public void setLocation(Location location) {
            this.location = location;
        }

        public String getOperationMode() {
            return operationMode;
        }

        public void setOperationMode(String operationMode) {
            this.operationMode = operationMode;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }

        public Integer getRunControl() {
            return runControl;
        }

        public void setRunControl(Integer runControl) {
            this.runControl = runControl;
        }

        public Integer getRunEnable() {
            return runEnable;
        }

        public void setRunEnable(Integer runEnable) {
            this.runEnable = runEnable;
        }

        public Integer getWorkMode() {
            return workMode;
        }

        public void setWorkMode(Integer workMode) {
            this.workMode = workMode;
        }

        public Integer getWalkSpeed() {
            return walkSpeed;
        }

        public void setWalkSpeed(Integer walkSpeed) {
            this.walkSpeed = walkSpeed;
        }

        public Integer getBrushSpeed() {
            return brushSpeed;
        }

        public void setBrushSpeed(Integer brushSpeed) {
            this.brushSpeed = brushSpeed;
        }

        public Integer getBridgeSpeed() {
            return bridgeSpeed;
        }

        public void setBridgeSpeed(Integer bridgeSpeed) {
            this.bridgeSpeed = bridgeSpeed;
        }

        public Double getRunTimeSingle() {
            return runTimeSingle;
        }

        public void setRunTimeSingle(Double runTimeSingle) {
            this.runTimeSingle = runTimeSingle;
        }

        public Double getRunTimeTotal() {
            return runTimeTotal;
        }

        public void setRunTimeTotal(Double runTimeTotal) {
            this.runTimeTotal = runTimeTotal;
        }

        public Double getMileageSingle() {
            return mileageSingle;
        }

        public void setMileageSingle(Double mileageSingle) {
            this.mileageSingle = mileageSingle;
        }

        public Double getMileageTotal() {
            return mileageTotal;
        }

        public void setMileageTotal(Double mileageTotal) {
            this.mileageTotal = mileageTotal;
        }

        public Double getHeartbeat() {
            return heartbeat;
        }

        public void setHeartbeat(Double heartbeat) {
            this.heartbeat = heartbeat;
        }

        public Double getVoltage() {
            return voltage;
        }

        public void setVoltage(Double voltage) {
            this.voltage = voltage;
        }

        public Double getAngle() {
            return angle;
        }

        public void setAngle(Double angle) {
            this.angle = angle;
        }

        public Double getHeading() {
            return heading;
        }

        public void setHeading(Double heading) {
            this.heading = heading;
        }

        public Boolean getTracking() {
            return tracking;
        }

        public void setTracking(Boolean tracking) {
            this.tracking = tracking;
        }

        public String getPathPlanning() {
            return pathPlanning;
        }

        public void setPathPlanning(String pathPlanning) {
            this.pathPlanning = pathPlanning;
        }

        public Integer getLeftEdge() {
            return leftEdge;
        }

        public void setLeftEdge(Integer leftEdge) {
            this.leftEdge = leftEdge;
        }

        public Integer getRightEdge() {
            return rightEdge;
        }

        public void setRightEdge(Integer rightEdge) {
            this.rightEdge = rightEdge;
        }

        public Boolean getMoveJudge() {
            return moveJudge;
        }

        public void setMoveJudge(Boolean moveJudge) {
            this.moveJudge = moveJudge;
        }

        public Boolean getDetectQrcode() {
            return detectQrcode;
        }

        public void setDetectQrcode(Boolean detectQrcode) {
            this.detectQrcode = detectQrcode;
        }

        public Boolean getEnterGarage() {
            return enterGarage;
        }

        public void setEnterGarage(Boolean enterGarage) {
            this.enterGarage = enterGarage;
        }

        public String getMqttMessageType() {
            return mqttMessageType;
        }

        public void setMqttMessageType(String mqttMessageType) {
            this.mqttMessageType = mqttMessageType;
        }

        public String getLastCommandId() {
            return lastCommandId;
        }

        public void setLastCommandId(String lastCommandId) {
            this.lastCommandId = lastCommandId;
        }

        public String getLastCommandStatus() {
            return lastCommandStatus;
        }

        public void setLastCommandStatus(String lastCommandStatus) {
            this.lastCommandStatus = lastCommandStatus;
        }

        public Integer getLocalX() {
            return localX;
        }

        public void setLocalX(Integer localX) {
            this.localX = localX;
        }

        public Integer getLocalY() {
            return localY;
        }

        public void setLocalY(Integer localY) {
            this.localY = localY;
        }

        public Integer getCurTaskIndex() {
            return curTaskIndex;
        }

        public void setCurTaskIndex(Integer curTaskIndex) {
            this.curTaskIndex = curTaskIndex;
        }

        public Integer getD12WorkWay() {
            return d12WorkWay;
        }

        public void setD12WorkWay(Integer d12WorkWay) {
            this.d12WorkWay = d12WorkWay;
        }

        public Integer getLeftRowStart() {
            return leftRowStart;
        }

        public void setLeftRowStart(Integer leftRowStart) {
            this.leftRowStart = leftRowStart;
        }

        public Integer getLeftRowEnd() {
            return leftRowEnd;
        }

        public void setLeftRowEnd(Integer leftRowEnd) {
            this.leftRowEnd = leftRowEnd;
        }

        public Integer getRightRowStart() {
            return rightRowStart;
        }

        public void setRightRowStart(Integer rightRowStart) {
            this.rightRowStart = rightRowStart;
        }

        public Integer getRightRowEnd() {
            return rightRowEnd;
        }

        public void setRightRowEnd(Integer rightRowEnd) {
            this.rightRowEnd = rightRowEnd;
        }

        public Integer getWalkFastSpeed() {
            return walkFastSpeed;
        }

        public void setWalkFastSpeed(Integer walkFastSpeed) {
            this.walkFastSpeed = walkFastSpeed;
        }

        public Integer getWalkSlowSpeed() {
            return walkSlowSpeed;
        }

        public void setWalkSlowSpeed(Integer walkSlowSpeed) {
            this.walkSlowSpeed = walkSlowSpeed;
        }

        public Integer getCurrentRowPosition() {
            return currentRowPosition;
        }

        public void setCurrentRowPosition(Integer currentRowPosition) {
            this.currentRowPosition = currentRowPosition;
        }

        public Integer getBatteryLowLimit() {
            return batteryLowLimit;
        }

        public void setBatteryLowLimit(Integer batteryLowLimit) {
            this.batteryLowLimit = batteryLowLimit;
        }

        public Integer getRobotInPositionTime() {
            return robotInPositionTime;
        }

        public void setRobotInPositionTime(Integer robotInPositionTime) {
            this.robotInPositionTime = robotInPositionTime;
        }

        public Integer getLimitPositionCheckTime() {
            return limitPositionCheckTime;
        }

        public void setLimitPositionCheckTime(Integer limitPositionCheckTime) {
            this.limitPositionCheckTime = limitPositionCheckTime;
        }

        public Integer getWalkPositionCheckTime() {
            return walkPositionCheckTime;
        }

        public void setWalkPositionCheckTime(Integer walkPositionCheckTime) {
            this.walkPositionCheckTime = walkPositionCheckTime;
        }
    }

    /**
     * 位置信息（内部类）
     */
    public static class Location {
        private Double lon;
        private Double lat;

        public Location(Double lon, Double lat) {
            this.lon = lon;
            this.lat = lat;
        }

        public Double getLon() {
            return lon;
        }

        public void setLon(Double lon) {
            this.lon = lon;
        }

        public Double getLat() {
            return lat;
        }

        public void setLat(Double lat) {
            this.lat = lat;
        }
    }
}
