package com.zt.cleanbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 消息监听器
 * 监听 Redis Pub/Sub 消息，并转发到 WebSocket
 */
@Slf4j
@Component
public class RedisWebSocketForwarder implements MessageListener {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 获取 Redis Channel 名称
            String channel = new String(message.getChannel());

            // 解析设备ID（从 channel 中提取）
            // channel 格式: device:status:update:{deviceId}
            String deviceId = channel.substring(channel.lastIndexOf(':') + 1);

            // 获取消息内容
            String json = new String(message.getBody());

            log.info("收到 Redis 消息 - Channel: {}, 设备: {}, 消息: {}",
                    channel, deviceId, json);

            // 转发到 WebSocket（广播给所有订阅该设备状态的用户）
            // 使用 /topic/device/status/{deviceId} 主题
            String websocketTopic = "/topic/device/status/" + deviceId;
            messagingTemplate.convertAndSend(websocketTopic, json);

            log.info("设备状态已转发到 WebSocket - 设备: {}, WebSocket Topic: {}",
                    deviceId, websocketTopic);

        } catch (Exception e) {
            log.error("处理 Redis 消息失败", e);
        }
    }
}
