package com.zt.cleanbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dao.RailcarStartupGateMapper;
import com.zt.cleanbot.model.RailcarStartupGate;
import com.zt.cleanbot.service.RailcarStartupGateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 启动门禁服务实现
 * 控制设备是否允许启动，通过 MQTT retained 消息下发给上位机
 */
@Slf4j
@Service
public class RailcarStartupGateServiceImpl
        extends ServiceImpl<RailcarStartupGateMapper, RailcarStartupGate>
        implements RailcarStartupGateService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageChannel mqttOutboundChannel;

    @Override
    public RailcarStartupGate getBySerialNumber(String serialNumber) {
        if (serialNumber == null || serialNumber.trim().isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<RailcarStartupGate> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RailcarStartupGate::getSerialNumber, serialNumber.trim());
        return this.getOne(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDisabled(String serialNumber, boolean disabled, String username) {
        if (serialNumber == null || serialNumber.trim().isEmpty()) {
            log.warn("设置启动门禁失败 - 设备序列号为空");
            return false;
        }

        String normalizedSerial = serialNumber.trim();

        try {
            // 1. 持久化到 MySQL
            LambdaQueryWrapper<RailcarStartupGate> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(RailcarStartupGate::getSerialNumber, normalizedSerial);

            RailcarStartupGate existing = this.getOne(queryWrapper);
            RailcarStartupGate entity;

            if (existing != null) {
                existing.setDisabled(disabled);
                existing.setCreatedBy(username);
                this.updateById(existing);
                entity = existing;
                log.info("启动门禁更新成功 - 设备: {}, disabled: {}", normalizedSerial, disabled);
            } else {
                entity = new RailcarStartupGate();
                entity.setSerialNumber(normalizedSerial);
                entity.setDisabled(disabled);
                entity.setCreatedBy(username);
                this.save(entity);
                log.info("启动门禁新增成功 - 设备: {}, disabled: {}", normalizedSerial, disabled);
            }

            // 2. 发布 MQTT retained 消息到 RAILCAR/S/{serialNumber}/startup
            publishRetainedMessage(normalizedSerial, disabled);

            return true;
        } catch (Exception e) {
            log.error("设置启动门禁异常 - 设备: {}, disabled: {}", normalizedSerial, disabled, e);
            return false;
        }
    }

    /**
     * 发布 MQTT retained 消息到设备启动门禁主题
     * 上位机 startup_gate.py 在启动时订阅此主题并读取 retained 消息
     *
     * @param serialNumber 设备序列号
     * @param disabled     是否禁用启动
     */
    private void publishRetainedMessage(String serialNumber, boolean disabled) {
        try {
            String topic = "RAILCAR/S/" + serialNumber + "/startup";

            Map<String, Object> payload = new HashMap<>();
            payload.put("disabled", disabled);

            byte[] payloadBytes = objectMapper.writeValueAsString(payload)
                    .getBytes(StandardCharsets.UTF_8);

            org.springframework.messaging.Message<byte[]> message = MessageBuilder
                    .withPayload(payloadBytes)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, 1)
                    .setHeader(MqttHeaders.RETAINED, true)
                    .build();

            boolean sent = mqttOutboundChannel.send(message);

            if (sent) {
                log.info("启动门禁 retained 消息发布成功 - topic: {}, disabled: {}", topic, disabled);
            } else {
                log.error("启动门禁 retained 消息发布失败 - topic: {}", topic);
            }
        } catch (Exception e) {
            log.error("发布启动门禁 retained 消息异常 - 设备: {}", serialNumber, e);
        }
    }
}
