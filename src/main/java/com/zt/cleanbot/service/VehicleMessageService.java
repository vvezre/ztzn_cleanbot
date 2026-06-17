package com.zt.cleanbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dto.*;
import com.zt.cleanbot.model.*;
import com.zt.cleanbot.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class VehicleMessageService {

    private static final Logger log = LoggerFactory.getLogger(VehicleMessageService.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private TaskLogService taskLogService;

    @Autowired
    private  VehicleLogService vehicleLogService;


    @Autowired
    @Qualifier("mqttOutboundChannel")
    private MessageChannel mqttOutboundChannel;


    // Redis数据过期时间（24小时）
//    private static final long REDIS_EXPIRE_TIME = 0;
//    private static final long REDIS_EXPIRE_TIME = 240 * 60 * 60;
    private static final long REDIS_EXPIRE_TIME = 24 * 60 * 60;

    @Transactional
    public void handleRegister(String deviceId, String payload) {
        try {
            BaseMessageDTO baseMessage = objectMapper.readValue(payload, BaseMessageDTO.class);
            RegisterDataDTO registerData = objectMapper.convertValue(baseMessage.getData(), RegisterDataDTO.class);

            // 转换为模型类
            Register register = new Register();
            register.setDeviceId(deviceId);
            register.setTimestamp(baseMessage.getTimestamp());
            register.setName(registerData.getName());
            register.setBrand(registerData.getBrand());
            register.setModel(registerData.getModel());
            register.setVehicleType(registerData.getVehicleType());
            register.setBatteryCapacity(registerData.getBatteryCapacity());
            register.setWeight(registerData.getWeight());
            register.setCleaningWidth(registerData.getCleaningWidth());

            if (registerData.getLocation() != null) {
                register.setLat(registerData.getLocation().getLat());
                register.setLon(registerData.getLocation().getLon());
            }

            log.info("处理车辆注册成功 - 设备ID: {}, 名称: {}, 类型: {}",
                    deviceId, register.getName(), register.getVehicleType());

            // 输出模型内容
            log.info("注册模型内容: {}", register);

            // ========== 保存到数据库 ==========
            saveOrUpdateVehicle(register);

            // ========== 保存到Redis ==========
            saveVehicleToRedis(deviceId, registerData);

        } catch (Exception e) {
            log.error("处理车辆注册消息失败: deviceId={}, payload={}", deviceId, payload, e);
        }
    }

    public void handleHeartbeat(String deviceId, String payload) {
        try {
            BaseMessageDTO baseMessage = objectMapper.readValue(payload, BaseMessageDTO.class);
            HeartbeatDataDTO heartbeatData = objectMapper.convertValue(baseMessage.getData(), HeartbeatDataDTO.class);

            // 转换为模型类
            Heartbeat heartbeat = new Heartbeat();
            heartbeat.setDeviceId(deviceId);
            heartbeat.setTimestamp(baseMessage.getTimestamp());
            heartbeat.setStatus(heartbeatData.getStatus());
            heartbeat.setBattery(heartbeatData.getBattery());

            if (heartbeatData.getLocation() != null) {
                heartbeat.setLat(heartbeatData.getLocation().getLat());
                heartbeat.setLon(heartbeatData.getLocation().getLon());
            }

            log.info("处理车辆心跳成功 - 设备ID: {}, 状态: {}, 电量: {}%",
                    deviceId, heartbeat.getStatus(), heartbeat.getBattery());

            // 输出模型内容
            log.info("心跳模型内容: {}", heartbeat);

            // ========== 更新Redis中的心跳信息 ==========
            updateHeartbeatToRedis(deviceId, heartbeatData);

        } catch (Exception e) {
            log.error("处理车辆心跳消息失败: deviceId={}, payload={}", deviceId, payload, e);
        }
    }

    /**
     * 保存车辆信息到数据库
     */
    private void saveOrUpdateVehicle(Register register) {
        try {
            // 检查设备是否已存在
            boolean exists = vehicleService.existsBySerialNumber(register.getDeviceId());

            if (exists) {
                log.info("车辆已存在，跳过插入 - 设备ID: {}", register.getDeviceId());
                return;
            }
            log.warn("Vehicle not scanned; skip auto-create from register message - deviceId={}", register.getDeviceId());
        } catch (Exception e) {
            log.error("保存车辆信息到数据库失败 - 设备ID: {}", register.getDeviceId(), e);
        }
    }

    /**
     * 保存车辆信息到Redis（注册时调用）
     */
    private void saveVehicleToRedis(String deviceId, RegisterDataDTO registerData) {
        try {
            // 键名直接使用deviceId，无前缀
            String redisKey = deviceId;

            // 使用Map创建Redis存储对象，避免类信息
            Map<String, Object> redisData = new HashMap<>();
            redisData.put("deviceId", deviceId);
            redisData.put("status", "active"); // 注册时默认状态为active
            redisData.put("battery", null);    // 注册时没有电量信息

            if (registerData.getLocation() != null) {
                Map<String, Object> locationMap = new HashMap<>();
                locationMap.put("lat", registerData.getLocation().getLat());
                locationMap.put("lon", registerData.getLocation().getLon());
                redisData.put("location", locationMap);
            }

            redisData.put("lastUpdateTime", System.currentTimeMillis());

            // 保存到Redis，设置24小时过期，使用车辆专用的RedisTemplate
            boolean success = redisUtil.setVehicle(redisKey, redisData, REDIS_EXPIRE_TIME, TimeUnit.SECONDS);
//            boolean success = redisUtil.setVehicle(redisKey, redisData, REDIS_EXPIRE_TIME, TimeUnit.SECONDS);

            if (success) {
                log.info("车辆信息保存到Redis成功 - 设备ID: {}, Redis键: {}", deviceId, redisKey);
                // 打印Redis中的实际数据格式
                Object storedData = redisUtil.getVehicle(redisKey);
                log.info("Redis中存储的数据: {}", storedData);
            } else {
                log.error("车辆信息保存到Redis失败 - 设备ID: {}", deviceId);
            }

        } catch (Exception e) {
            log.error("保存车辆信息到Redis异常 - 设备ID: {}", deviceId, e);
        }
    }

    /**
     * 更新心跳信息到Redis（心跳时调用）
     */
    private void updateHeartbeatToRedis(String deviceId, HeartbeatDataDTO heartbeatData) {
        try {
            // 键名直接使用deviceId，无前缀
            String redisKey = deviceId;

            // 先检查Redis中是否存在该设备信息
            Object existingData = redisUtil.getVehicle(redisKey);
            Map<String, Object> redisData;

            if (existingData != null && existingData instanceof Map) {
                // 如果存在，更新现有数据
                redisData = (Map<String, Object>) existingData;
                log.debug("更新Redis中已存在的车辆心跳信息 - 设备ID: {}", deviceId);
            } else {
                // 如果不存在，创建新数据
                redisData = new HashMap<>();
                redisData.put("deviceId", deviceId);
                log.info("创建新的Redis车辆心跳信息 - 设备ID: {}", deviceId);
            }

            // 更新数据
            redisData.put("status", heartbeatData.getStatus());
            redisData.put("battery", heartbeatData.getBattery());
            redisData.put("lastUpdateTime", System.currentTimeMillis());

            if (heartbeatData.getLocation() != null) {
                Map<String, Object> locationMap = new HashMap<>();
                locationMap.put("lat", heartbeatData.getLocation().getLat());
                locationMap.put("lon", heartbeatData.getLocation().getLon());
                redisData.put("location", locationMap);
            }

            // 保存到Redis，重置过期时间，使用车辆专用的RedisTemplate
            boolean success = redisUtil.setVehicle(redisKey, redisData, REDIS_EXPIRE_TIME, TimeUnit.SECONDS);

            if (success) {
                log.debug("心跳信息更新到Redis成功 - 设备ID: {}, 状态: {}, 电量: {}%",
                        deviceId, heartbeatData.getStatus(), heartbeatData.getBattery());
            } else {
                log.error("心跳信息更新到Redis失败 - 设备ID: {}", deviceId);
            }

        } catch (Exception e) {
            log.error("更新心跳信息到Redis异常 - 设备ID: {}", deviceId, e);
        }
    }

    public void handleTaskLog(String deviceId, String payload) {
        try {
            log.info("开始处理任务日志 - 设备ID: {}, payload: {}", deviceId, payload);

            BaseMessageDTO baseMessage = objectMapper.readValue(payload, BaseMessageDTO.class);
            TaskLogDataDTO taskLogData = objectMapper.convertValue(baseMessage.getData(), TaskLogDataDTO.class);

            // 转换为模型类
            TaskLog taskLog = new TaskLog();
            taskLog.setId(taskLogData.getTaskId()); // 对应taskID
            taskLog.setVehicleId(deviceId); // 对应deviceId

            // 只设置非空的字段
            if (taskLogData.getAreaId() != null) {
                taskLog.setAreaId(taskLogData.getAreaId());
            }
            if (taskLogData.getRouteId() != null) {
                taskLog.setRouteId(taskLogData.getRouteId());
            }
            if (taskLogData.getStatus() != null) {
                taskLog.setStatus(taskLogData.getStatus());
            }
            if (taskLogData.getCleaningMode() != null) {
                taskLog.setCleaningMode(taskLogData.getCleaningMode());
            }
//            if (taskLogData.getCleaningArea() != null) {
//                taskLog.setCleaningArea(taskLogData.getCleaningArea());
//            }
            if (taskLogData.getTaskType() != null) {
                taskLog.setTaskType(taskLogData.getTaskType());
            }

            // 时间字段转换
            if (taskLogData.getStartTime() != null) {
                taskLog.setStartTime(new Date(taskLogData.getStartTime()));
            }
            if (taskLogData.getEndTime() != null) {
                taskLog.setEndTime(new Date(taskLogData.getEndTime()));
            }

            // droneId, efficiency, powerRestored 在MQTT消息中没有，保持为null

            log.info("处理任务日志成功 - 设备ID: {}, 任务ID: {}, 状态: {}",
                    deviceId, taskLog.getId(), taskLog.getStatus());

            // 输出模型内容
            log.info("任务日志模型内容: {}", taskLog);

            // ========== 新增：保存或更新到数据库 ==========
            saveOrUpdateTaskLog(taskLog);

        } catch (Exception e) {
            log.error("处理任务日志消息失败: deviceId={}, payload={}", deviceId, payload, e);
        }
    }


    /**
     * 保存或更新任务日志到数据库
     */
    private void saveOrUpdateTaskLog(TaskLog taskLog) {
        try {
            boolean success = taskLogService.saveOrUpdateTaskLog(taskLog);

            if (success) {
                // 检查是插入还是更新
                boolean existed = taskLogService.existsByTaskId(taskLog.getId());
                if (existed) {
                    log.info("任务日志更新成功 - 任务ID: {}, 设备ID: {}", taskLog.getId(), taskLog.getVehicleId());
                } else {
                    log.info("任务日志插入成功 - 任务ID: {}, 设备ID: {}", taskLog.getId(), taskLog.getVehicleId());
                }
            } else {
                log.error("任务日志保存失败 - 任务ID: {}, 设备ID: {}", taskLog.getId(), taskLog.getVehicleId());
            }

        } catch (Exception e) {
            log.error("保存任务日志到数据库异常 - 任务ID: {}, 设备ID: {}", taskLog.getId(), taskLog.getVehicleId(), e);
        }
    }

    public void handleVehicleLog(String deviceId, String payload) {
        try {
            log.info("开始处理车辆日志 - 设备ID: {}, payload: {}", deviceId, payload);

            BaseMessageDTO baseMessage = objectMapper.readValue(payload, BaseMessageDTO.class);
            VehicleLogDataDTO vehicleLogData = objectMapper.convertValue(baseMessage.getData(), VehicleLogDataDTO.class);

            // 转换为模型类
            VehicleLog vehicleLog = new VehicleLog();

            // 生成唯一ID
            vehicleLog.setId(UUID.randomUUID().toString());

            vehicleLog.setVehicleId(deviceId);
            vehicleLog.setBattery(vehicleLogData.getBattery() != null ? vehicleLogData.getBattery().floatValue() : null);
            vehicleLog.setCommandType(vehicleLogData.getCommandType());

            // 设置时间戳
            if (vehicleLogData.getTimestamp() != null) {
                vehicleLog.setTimestamp(new Date(vehicleLogData.getTimestamp()));
            } else {
                vehicleLog.setTimestamp(new Date());
            }

            // 直接设置经纬度到 lat 和 lon 字段
            if (vehicleLogData.getPoint() != null) {
                vehicleLog.setLat(vehicleLogData.getPoint().getLat());
                vehicleLog.setLon(vehicleLogData.getPoint().getLon());
            }

            log.info("处理车辆日志成功 - 设备ID: {}, 指令类型: {}, 电量: {}%, 坐标: [{}, {}]",
                    deviceId, vehicleLog.getCommandType(), vehicleLog.getBattery(),
                    vehicleLog.getLat(), vehicleLog.getLon());

            // 输出模型内容
            log.info("车辆日志模型内容: {}", vehicleLog);

            // 保存到数据库
            boolean saved = vehicleLogService.save(vehicleLog);
            if (saved) {
                log.info("车辆日志保存成功 - 设备ID: {}, 指令类型: {}", deviceId, vehicleLog.getCommandType());
            } else {
                log.error("车辆日志保存失败 - 设备ID: {}, 指令类型: {}", deviceId, vehicleLog.getCommandType());
            }

        } catch (Exception e) {
            log.error("处理车辆日志消息失败: deviceId={}, payload={}", deviceId, payload, e);
        }
    }

    public void handleRouteLog(String deviceId, String payload) {
        try {
            BaseMessageDTO baseMessage = objectMapper.readValue(payload, BaseMessageDTO.class);
            RouteLogDataDTO routeLogData = objectMapper.convertValue(baseMessage.getData(), RouteLogDataDTO.class);

            // 转换为模型类
            RouteLog routeLog = new RouteLog();
            routeLog.setVehicleId(deviceId); // 对应deviceId
            routeLog.setTaskId(routeLogData.getTaskId());
            routeLog.setTimestamp(new Date(baseMessage.getTimestamp()));
            routeLog.setType("vehicle"); // 固定为vehicle

            // id, droneId, gpsPath 在MQTT消息中没有，保持为null
            if (routeLogData.getPoint() != null) {
                routeLog.setLat(routeLogData.getPoint().getLat());
                routeLog.setLon(routeLogData.getPoint().getLon());
            }

            log.info("处理路径日志成功 - 设备ID: {}, 任务ID: {}, 坐标: [{}, {}]",
                    deviceId, routeLog.getTaskId(), routeLog.getLat(), routeLog.getLon());

            // 输出模型内容
            log.info("路径日志模型内容: {}", routeLog);

        } catch (Exception e) {
            log.error("处理路径日志消息失败: deviceId={}, payload={}", deviceId, payload, e);
        }
    }

    /**
     * 获取上一次模式的描述信息
     */
    private String getLastModeDescription(String lastMode) {
        if (lastMode == null) return null;

        // 根据数据库枚举值返回对应的描述
        switch (lastMode) {
            case "cleaning": return "自动";
            case "emergency_stop": return "停止";
            case "manual_go": return "手动";
            default: return lastMode;
        }
    }


    /**
     * 发送命令到指定车辆
     * @param deviceId 设备ID
     * @param command 命令内容
     * @return 发送是否成功
     */
    public boolean sendCommandToVehicle(String deviceId, String command) {
        try {
            // 构建命令消息
            VehicleCommandDTO commandMessage = new VehicleCommandDTO(deviceId, command);
            String payload = objectMapper.writeValueAsString(commandMessage);

            // 构建主题
            String topic = "vehicle/" + deviceId + "/controller";

            // 构建MQTT消息
            Message<String> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, 1) // 设置QOS为1
                    .build();

            // 发送消息
            boolean sent = mqttOutboundChannel.send(message);

            if (sent) {
                log.info("MQTT命令发送成功 - 主题: {}, 命令: {}, 设备ID: {}", topic, command, deviceId);
            } else {
                log.error("MQTT命令发送失败 - 主题: {}, 命令: {}, 设备ID: {}", topic, command, deviceId);
            }

            return sent;

        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败 - 设备ID: {}, 命令: {}", deviceId, command, e);
            return false;
        } catch (Exception e) {
            log.error("发送MQTT命令异常 - 设备ID: {}, 命令: {}", deviceId, command, e);
            return false;
        }
    }

    /**
     * 发送自定义消息到指定主题
     * @param topic 主题
     * @param payload 消息内容
     * @param qos 服务质量等级
     * @return 发送是否成功
     */
    public boolean sendMessage(String topic, String payload, int qos) {
        try {
            Message<String> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, qos)
                    .build();

            boolean sent = mqttOutboundChannel.send(message);

            if (sent) {
                log.info("MQTT消息发送成功 - 主题: {}, QOS: {}", topic, qos);
            } else {
                log.error("MQTT消息发送失败 - 主题: {}, QOS: {}", topic, qos);
            }

            return sent;

        } catch (Exception e) {
            log.error("发送MQTT消息异常 - 主题: {}", topic, e);
            return false;
        }
    }


}

