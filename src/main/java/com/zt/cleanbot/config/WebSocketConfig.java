package com.zt.cleanbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket 配置
 * 使用 STOMP 协议进行消息推送
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 WebSocket 端点（带 SockJS 支持，用于 Web 端）
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // 允许跨域
                .withSockJS(); // 启用 SockJS 降级支持

        // 注册原生 WebSocket 端点（不带 SockJS，用于小程序/App）
        registry.addEndpoint("/ws/native")
                .setAllowedOriginPatterns("*"); // 允许跨域，不使用 SockJS
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 启用简单消息代理，用于向客户端推送消息
        registry.enableSimpleBroker("/topic", "/queue");

        // 设置客户端发送消息的前缀
        registry.setApplicationDestinationPrefixes("/app");
    }
}
