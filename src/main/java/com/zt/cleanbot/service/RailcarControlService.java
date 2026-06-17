package com.zt.cleanbot.service;

import com.zt.cleanbot.dto.DeviceConfigRequest;
import com.zt.cleanbot.service.strategy.DeviceControlStrategy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 轨道车控制服务
 * 负责策略分发和MQTT消息发送
 */
@Slf4j
@Service
public class RailcarControlService {

    @Autowired
    private MessageChannel mqttOutboundChannel;

    @Autowired
    private List<DeviceControlStrategy> strategies;

    /**
     * 发送控制命令到轨道车
     */
    public boolean sendControlCommand(DeviceConfigRequest request) {
        long totalStartNs = System.nanoTime();
        String traceId = MDC.get("traceId");
        try {
            String deviceId = request.getDeviceId();
            String model = ensureModel(request);

            log.info("准备发送控制命令 - 设备: {}, 型号: {}", deviceId, model);
            log.info("[latency][{}] RailcarControlService 开始 - 设备: {}, 型号: {}",
                    traceId, deviceId, model);

            long encodeStartNs = System.nanoTime();
            String hexData = encodeControlCommand(request);
            log.info("[latency][{}] 控制帧编码完成 - 设备: {}, 耗时: {} ms",
                    traceId, deviceId, elapsedMs(encodeStartNs));
            if (hexData == null || hexData.isEmpty()) {
                log.error("控制数据编码失败 - 设备: {}, 型号: {}", deviceId, model);
                return false;
            }

            // 3. 发送 MQTT 消息
            // 完整设备ID作为Topic后缀
            String topic = "RAILCAR/R/" + deviceId;
            byte[] payload = hexStringToByteArray(hexData);

            Message<byte[]> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, 1) // QoS 1 确保到达
                    .build();

            long channelSendStartNs = System.nanoTime();
            boolean sent = mqttOutboundChannel.send(message);
            log.info("[latency][{}] MQTT Channel send 完成 - 设备: {}, topic: {}, sent: {}, 耗时: {} ms",
                    traceId, deviceId, topic, sent, elapsedMs(channelSendStartNs));

            if (sent) {
                log.info("轨道车控制命令发送成功 - 主题: {}, 长度: {} 字节", topic, payload.length);
                log.info("[latency][{}] RailcarControlService 完成 - 设备: {}, 总耗时: {} ms",
                        traceId, deviceId, elapsedMs(totalStartNs));
                return true;
            } else {
                log.error("轨道车控制命令发送失败 - 主题: {}", topic);
                log.info("[latency][{}] RailcarControlService 完成 - 设备: {}, 总耗时: {} ms",
                        traceId, deviceId, elapsedMs(totalStartNs));
                return false;
            }

        } catch (Exception e) {
            log.error("[latency][{}] RailcarControlService 异常 - 设备: {}, 总耗时: {} ms",
                    traceId, request.getDeviceId(), elapsedMs(totalStartNs), e);
            log.error("发送轨道车控制命令异常", e);
            return false;
        }
    }

    /**
     * 仅编码控制命令，不发送 MQTT
     */
    public String encodeControlCommand(DeviceConfigRequest request) {
        String model = ensureModel(request);

        DeviceControlStrategy strategy = strategies.stream()
                .filter(s -> s.supports(model))
                .findFirst()
                .orElse(null);

        if (strategy == null) {
            log.error("未找到支持该设备型号的控制策略: {}", model);
            return null;
        }

        String hexData = strategy.encode(request);
        if (hexData == null || hexData.isEmpty()) {
            log.error("控制数据编码失败 - 策略: {}", strategy.getClass().getSimpleName());
            return null;
        }

        log.info("控制数据编码成功 (策略: {}) - Hex: {}", strategy.getClass().getSimpleName(), hexData);
        return hexData;
    }

    private String ensureModel(DeviceConfigRequest request) {
        String deviceId = request.getDeviceId();
        String model = request.getModel();
        if (model == null && deviceId != null && deviceId.length() >= 4) {
            model = deviceId.substring(0, 4);
            request.setModel(model);
        }
        return model;
    }

    /**
     * 十六进制字符串转字节数组
     */
    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private long elapsedMs(long startNs) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
    }
}
