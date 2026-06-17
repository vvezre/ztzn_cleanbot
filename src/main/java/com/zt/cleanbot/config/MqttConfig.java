package com.zt.cleanbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.service.VehicleMessageService;
import com.zt.cleanbot.service.RailcarMessageService;
import io.moquette.broker.Server;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
public class MqttConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.client-id}")
    private String clientId;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.beans.factory.ObjectProvider<Server> embeddedMqttBrokerProvider;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        if (embeddedMqttBrokerProvider != null) {
            embeddedMqttBrokerProvider.getIfAvailable();
        }
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();

        options.setServerURIs(new String[] { brokerUrl });
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);
        options.setAutomaticReconnect(true);

        factory.setConnectionOptions(options);
        return factory;
    }

    // ==================== 车辆消息通道 ====================
    @Bean
    public MessageChannel vehicleRegisterChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel vehicleHeartbeatChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel vehicleVehicleLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel vehicleRouteLogChannel() {
        return new DirectChannel();
    }

    // ==================== 车辆消息适配器 ====================
    @Bean
    public MessageProducer vehicleRegisterInbound() {
        return createAdapter("vehicle-register", "vehicle/+/register", vehicleRegisterChannel());
    }

    @Bean
    public MessageProducer vehicleHeartbeatInbound() {
        return createAdapter("vehicle-heartbeat", "vehicle/+/heartbeat", vehicleHeartbeatChannel());
    }

    @Bean
    public MessageProducer vehicleVehicleLogInbound() {
        return createAdapter("vehicle-vehicle-log", "vehicle/+/vehicle_log", vehicleVehicleLogChannel());
    }

    @Bean
    public MessageProducer vehicleRouteLogInbound() {
        return createAdapter("vehicle-route-log", "vehicle/+/route_log", vehicleRouteLogChannel());
    }

    // ==================== 通用适配器创建方法 ====================
    private MessageProducer createAdapter(String type, String topic, MessageChannel channel) {
        String inboundClientId = clientId + "-" + type;
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                inboundClientId,
                this.mqttClientFactory(),
                topic);

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(channel);

        log.info("初始化MQTT订阅 - 类型: {}, 客户端ID: {}, 主题: {}", type, inboundClientId, topic);
        return adapter;
    }

    // ==================== 消息处理器配置 ====================

    // 车辆注册处理器
    @Bean
    @org.springframework.integration.annotation.ServiceActivator(inputChannel = "vehicleRegisterChannel")
    public MessageHandler vehicleRegisterHandler(VehicleMessageService vehicleMessageService) {
        return message -> {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            String payload = (String) message.getPayload();
            String deviceId = extractDeviceId(topic);
            log.info("收到车辆注册消息 - 设备ID: {}, 主题: {}", deviceId, topic);
            vehicleMessageService.handleRegister(deviceId, payload);
        };
    }

    // 车辆心跳处理器
    @Bean
    @org.springframework.integration.annotation.ServiceActivator(inputChannel = "vehicleHeartbeatChannel")
    public MessageHandler vehicleHeartbeatHandler(VehicleMessageService vehicleMessageService) {
        return message -> {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            String payload = (String) message.getPayload();
            String deviceId = extractDeviceId(topic);
            log.info("收到车辆心跳消息 - 设备ID: {}, 主题: {}", deviceId, topic);
            vehicleMessageService.handleHeartbeat(deviceId, payload);
        };
    }

    // 车辆日志处理器
    @Bean
    @org.springframework.integration.annotation.ServiceActivator(inputChannel = "vehicleVehicleLogChannel")
    public MessageHandler vehicleVehicleLogHandler(VehicleMessageService vehicleMessageService) {
        return message -> {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            String payload = (String) message.getPayload();
            String deviceId = extractDeviceId(topic);
            log.info("收到车辆日志消息 - 设备ID: {}, 主题: {}", deviceId, topic);
            vehicleMessageService.handleVehicleLog(deviceId, payload);
        };
    }

    // 路径日志处理器
    @Bean
    @org.springframework.integration.annotation.ServiceActivator(inputChannel = "vehicleRouteLogChannel")
    public MessageHandler vehicleRouteLogHandler(VehicleMessageService vehicleMessageService) {
        return message -> {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            String payload = (String) message.getPayload();
            String deviceId = extractDeviceId(topic);
            log.info("收到路径日志消息 - 设备ID: {}, 主题: {}", deviceId, topic);
            vehicleMessageService.handleRouteLog(deviceId, payload);
        };
    }

    // 提取设备ID的辅助方法
    private String extractDeviceId(String topic) {
        String[] parts = topic.split("/");
        return parts.length >= 2 ? parts[1] : "unknown";
    }

    // ==================== 任务日志消息通道 ====================
    @Bean
    public MessageChannel vehicleTaskLogChannel() {
        return new DirectChannel();
    }

    // ==================== 任务日志消息适配器 ====================
    @Bean
    public MessageProducer vehicleTaskLogInbound() {
        return createAdapter("vehicle-task-log", "vehicle/+/task_log", vehicleTaskLogChannel());
    }

    // ==================== 任务日志消息处理器 ====================
    @Bean
    @org.springframework.integration.annotation.ServiceActivator(inputChannel = "vehicleTaskLogChannel")
    public MessageHandler vehicleTaskLogHandler(VehicleMessageService vehicleMessageService) {
        return message -> {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            String payload = (String) message.getPayload();
            String deviceId = extractDeviceId(topic);
            log.info("收到任务日志消息 - 设备ID: {}, 主题: {}", deviceId, topic);
            vehicleMessageService.handleTaskLog(deviceId, payload);
        };
    }

    // ==================== 轨道车状态消息通道 ====================
    @Bean
    public MessageChannel vehicleRailcarStatusChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel vehicleTRailcarStatusChannel() {
        return new DirectChannel();
    }

    // ==================== 轨道车状态消息适配器 ====================
    // @Bean
    // public MessageProducer vehicleRailcarStatusInbound() {
    // // 修改主题为 RAILCAR/S/+
    // return createAdapter("vehicle-railcar-status", "RAILCAR/S/+",
    // vehicleRailcarStatusChannel());
    // }

    @Bean
    public MessageProducer vehicleRailcarStatusInbound() {
        String inboundClientId = clientId + "-vehicle-railcar-status";
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                inboundClientId,
                this.mqttClientFactory(),
                "RAILCAR/S/+");

        adapter.setCompletionTimeout(5000);

        // 关键修改：使用保留二进制数据的转换器
        DefaultPahoMessageConverter converter = new DefaultPahoMessageConverter();
        converter.setPayloadAsBytes(true); // 保持payload为字节数组
        adapter.setConverter(converter);

        adapter.setQos(1);
        adapter.setOutputChannel(vehicleRailcarStatusChannel());

        log.info("初始化MQTT订阅 - 类型: 轨道车状态, 客户端ID: {}, 主题: {}", inboundClientId, "RAILCAR/S/+");
        return adapter;
    }

    /**
     * T 型设备状态上报（JSON）:
     * Topic: RAILCAR/R/{serialNumber}
     */
    @Bean
    public MessageProducer vehicleTRailcarStatusInbound() {
        String inboundClientId = clientId + "-vehicle-t-railcar-status";
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                inboundClientId,
                this.mqttClientFactory(),
                "RAILCAR/R/+");

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(vehicleTRailcarStatusChannel());

        log.info("初始化MQTT订阅 - 类型: T轨道车状态, 客户端ID: {}, 主题: {}", inboundClientId, "RAILCAR/R/+");
        return adapter;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutboundHandler() {
        String outboundClientId = clientId + "-outbound";
        MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler(
                outboundClientId,
                mqttClientFactory());
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic("default/topic");
        messageHandler.setDefaultQos(1); // 添加这行，设置默认 QOS
        return messageHandler;
    }

    /**
     * 从轨道车主题中提取设备ID - 修改为新的主题格式
     */
    private String extractDeviceIdFromRailcarTopic(String topic) {
        // 新的主题格式: RAILCAR/S/250001
        String[] parts = topic.split("/");
        if (parts.length == 3) {
            return parts[2]; // 第三部分为ID
        }
        return null;
    }

    // ==================== 轨道车状态消息处理器 ====================
    @Bean
    @org.springframework.integration.annotation.ServiceActivator(inputChannel = "vehicleRailcarStatusChannel")
    public MessageHandler vehicleRailcarStatusHandler(RailcarMessageService railcarMessageService) {
        return message -> {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            Object payload = message.getPayload();

            // 从主题中提取设备ID
            String serialNumber = extractDeviceIdFromRailcarTopic(topic);
            if (serialNumber == null) {
                log.error("无法从主题中提取序列号: {}", topic);
                return;
            }

            if (serialNumber.startsWith("-T")) {
                String payloadStr = convertPayloadToString(payload);
                if (payloadStr == null) {
                    log.error("T 型轨道车状态消息的 payload 无法转换为字符串 - serialNumber={}", serialNumber);
                    return;
                }
                log.warn("D 型轨道车通道收到 T 型设备消息，已按 T 型处理 - serialNumber={}, topic={}", serialNumber, topic);
                railcarMessageService.handleTRailcarStatus(serialNumber, payloadStr);
                return;
            }

            if (serialNumber.startsWith("-D")) {
                String hexPayload = convertPayloadToHex(payload);
                if (hexPayload == null) {
                    log.error("D 型轨道车状态消息的 payload 无法转换为十六进制 - serialNumber={}", serialNumber);
                    return;
                }
                log.info("收到 D 型轨道车状态消息 - 序列号: {}, 主题: {}, 十六进制长度: {}",
                        serialNumber, topic, hexPayload.length());
                railcarMessageService.handleRailcarStatus(serialNumber, hexPayload);
                return;
            }

            log.error("未知设备前缀，无法分流状态消息 - serialNumber={}, topic={}", serialNumber, topic);
        };
    }

    @Bean
    @org.springframework.integration.annotation.ServiceActivator(inputChannel = "vehicleTRailcarStatusChannel")
    public MessageHandler vehicleTRailcarStatusHandler(RailcarMessageService railcarMessageService) {
        return message -> {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            Object payload = message.getPayload();

            String serialNumber = extractDeviceIdFromRailcarTopic(topic);
            if (serialNumber == null) {
                log.error("无法从 T 轨道车主题中提取序列号: {}", topic);
                return;
            }

            if (serialNumber.startsWith("-D")) {
                String hexPayload = convertPayloadToHex(payload);
                if (hexPayload == null) {
                    log.error("D 型轨道车状态消息的 payload 无法转换为十六进制 - serialNumber={}", serialNumber);
                    return;
                }
                log.warn("T 型轨道车通道收到 D 型设备消息，疑似服务器下发回读，已忽略 - serialNumber={}, topic={}, 十六进制长度={}",
                        serialNumber, topic, hexPayload.length());
                return;
            }

            if (!serialNumber.startsWith("-T")) {
                log.error("未知设备前缀，无法分流 T 通道状态消息 - serialNumber={}, topic={}", serialNumber, topic);
                return;
            }

            String payloadStr = convertPayloadToString(payload);
            if (payloadStr == null) {
                log.error("T 型轨道车状态消息的 payload 无法转换为字符串 - serialNumber={}", serialNumber);
                return;
            }

            log.info("收到 T 型轨道车状态消息 - 序列号: {}, 主题: {}", serialNumber, topic);
            railcarMessageService.handleTRailcarStatus(serialNumber, payloadStr);
        };
    }

    // public MessageHandler vehicleRailcarStatusHandler(RailcarMessageService
    // railcarMessageService) {
    // return message -> {
    // String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
    // Object payload = message.getPayload();
    //
    // // 从主题中提取设备ID - 使用新的提取方法
    // String serialNumber = extractDeviceIdFromRailcarTopic(topic);
    // if (serialNumber == null) {
    // log.error("无法从主题中提取序列号: {}", topic);
    // return;
    // }
    //
    // String hexPayload;
    //
    // if (payload instanceof byte[]) {
    // // 如果是字节数组，转换为十六进制字符串
    // byte[] bytes = (byte[]) payload;
    // hexPayload = bytesToHex(bytes);
    // log.info("收到轨道车状态消息(二进制) - 序列号: {}, 主题: {}, 字节长度: {}",
    // serialNumber, topic, bytes.length);
    // } else if (payload instanceof String) {
    // // 如果是字符串，尝试处理
    // hexPayload = (String) payload;
    // log.info("收到轨道车状态消息(字符串) - 序列号: {}, 主题: {}", serialNumber, topic);
    //
    // // 如果是带空格的十六进制格式，去除空格
    // if (hexPayload.contains(" ")) {
    // hexPayload = hexPayload.replaceAll(" ", "");
    // log.info("去除空格后的十六进制: {}", hexPayload);
    // }
    // } else {
    // log.error("未知的payload类型: {}", payload.getClass().getName());
    // return;
    // }
    //
    // // 调用处理方法，传入序列号和十六进制字符串
    // railcarMessageService.handleRailcarStatus(serialNumber, hexPayload);
    // };
    // }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString().toUpperCase();
    }

    private String convertPayloadToHex(Object payload) {
        if (payload instanceof byte[]) {
            return bytesToHex((byte[]) payload);
        }
        if (payload instanceof String) {
            String value = ((String) payload).trim();
            return value.isEmpty() ? null : value.replaceAll(" ", "").toUpperCase();
        }
        return null;
    }

    private String convertPayloadToString(Object payload) {
        if (payload instanceof byte[]) {
            return new String((byte[]) payload, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (payload instanceof String) {
            return (String) payload;
        }
        return null;
    }
}
