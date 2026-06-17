package com.zt.cleanbot.config;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Properties;

@Configuration
@ConditionalOnProperty(prefix = "mqtt.embedded-broker", name = "enabled", havingValue = "true")
public class EmbeddedMqttBrokerConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedMqttBrokerConfig.class);

    @Bean
    public Server embeddedMqttBroker(
            @org.springframework.beans.factory.annotation.Value("${mqtt.embedded-broker.host:127.0.0.1}") String host,
            @org.springframework.beans.factory.annotation.Value("${mqtt.embedded-broker.port:1883}") int port
    ) throws IOException {
        Properties props = new Properties();
        props.setProperty("host", host);
        props.setProperty("port", String.valueOf(port));
        props.setProperty("allow_anonymous", "true");
        props.setProperty("persistence_enabled", "false");
        props.setProperty("autosave_interval", "0");

        Server server = new Server();
        server.startServer(new MemoryConfig(props));
        log.info("本地内置 MQTT Broker 已启动: {}:{}", host, port);
        return server;
    }

    @Bean
    public DisposableBean embeddedMqttBrokerShutdownHook(Server embeddedMqttBroker) {
        return () -> {
            log.info("正在关闭本地内置 MQTT Broker");
            embeddedMqttBroker.stopServer();
        };
    }
}
