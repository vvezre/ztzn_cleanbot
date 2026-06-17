package com.zt.cleanbot.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DroneMessageService {
    private static final Logger log = LoggerFactory.getLogger(com.zt.cleanbot.CleanbotApplication.class);


    @Autowired
    private ObjectMapper objectMapper;

    public void handleRegister(String deviceId, String payload) {
        try {
//            DroneRegisterMessage message = objectMapper.readValue(payload, DroneRegisterMessage.class);
            log.info("处理无人机注册 - - 设备ID: {}, 消息内容: {}", deviceId, payload);
            // 注册业务逻辑
        } catch (Exception e) {
            log.error("处理无人机注册消息失败: deviceId={}", deviceId, e);
        }
    }

    public void handleHeartbeat(String deviceId, String payload) {
        try {
//            DroneHeartbeatMessage message = objectMapper.readValue(payload, DroneHeartbeatMessage.class);
            log.debug("处理无人机心跳 - - 设备ID: {}, 消息内容: {}", deviceId, payload);
            // 心跳业务逻辑 - 更新最后在线时间等
        } catch (Exception e) {
            log.error("处理无人机心跳消息失败: deviceId={}", deviceId, e);
        }
    }

    public void handleStatus(String deviceId, String payload) {
        try {
//            DroneStatusMessage message = objectMapper.readValue(payload, DroneStatusMessage.class);
            log.info("处理无人机状态 - - 设备ID: {}, 消息内容: {}", deviceId, payload);
            // 状态更新业务逻辑
        } catch (Exception e) {
            log.error("处理无人机状态消息失败: deviceId={}", deviceId, e);
        }
    }

    public void handleLocation(String deviceId, String payload) {
        try {
//            DroneLocationMessage message = objectMapper.readValue(payload, DroneLocationMessage.class);
//            log.debug("处理无人机位置 - 设备ID: {}, 坐标: [{}, {}]",
//                    deviceId, message.getLatitude(), message.getLongitude());
            log.debug("处理无人机位置 -设备ID: {}, 消息内容: {}", deviceId, payload);
            // 位置更新业务逻辑
        } catch (Exception e) {
            log.error("处理无人机位置消息失败: deviceId={}", deviceId, e);
        }
    }
}



//
//@Service
//public class DroneMessageService {
//
//    private static final Logger log = LoggerFactory.getLogger(DroneMessageService.class);
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    public void handleMessage(String deviceId, String payload) {
//        try {
//            // 这里可以定义DroneMessage类来解析JSON
//            // DroneMessage message = objectMapper.readValue(payload, DroneMessage.class);
//
//            log.info("处理无人机消息 - 设备ID: {}, 消息内容: {}", deviceId, payload);
//
//            // 业务处理逻辑
//            processDroneBusiness(deviceId, payload);
//
//        } catch (Exception e) {
//            log.error("处理无人机消息失败: deviceId={}", deviceId, e);
//        }
//    }
//
//    private void processDroneBusiness(String deviceId, String payload) {
//        // 具体的无人机业务逻辑
//    }
//}
