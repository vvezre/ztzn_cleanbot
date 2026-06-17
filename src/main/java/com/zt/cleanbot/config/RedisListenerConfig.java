package com.zt.cleanbot.config;

import com.zt.cleanbot.service.RedisWebSocketForwarder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis 消息监听器配置
 * 订阅设备状态更新的 Redis Channel
 */
@Configuration
public class RedisListenerConfig {

    @Autowired
    private RedisWebSocketForwarder redisWebSocketForwarder;

    /**
     * Redis 消息监听容器
     */
    @Bean
    RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 订阅所有设备状态更新 Channel
        // Pattern: device:status:update:*
        PatternTopic topic = new PatternTopic("device:status:update:*");

        // 注册监听器
        container.addMessageListener(redisWebSocketForwarder, topic);

        return container;
    }
}
