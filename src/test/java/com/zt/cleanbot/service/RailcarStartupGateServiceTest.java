package com.zt.cleanbot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dao.RailcarStartupGateMapper;
import com.zt.cleanbot.model.RailcarStartupGate;
import com.zt.cleanbot.service.impl.RailcarStartupGateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 启动门禁服务单元测试
 * 验证 MySQL 持久化 + MQTT retained 消息发布
 */
class RailcarStartupGateServiceTest {

    private RailcarStartupGateServiceImpl service;
    private RailcarStartupGateMapper mapper;
    private MessageChannel mqttOutboundChannel;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mapper = mock(RailcarStartupGateMapper.class);
        mqttOutboundChannel = mock(MessageChannel.class);

        service = new RailcarStartupGateServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "mqttOutboundChannel", mqttOutboundChannel);
    }

    @Test
    void getBySerialNumber_shouldReturnNull_whenNotFound() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        RailcarStartupGate result = service.getBySerialNumber("-T01250001");
        assertNull(result);
    }

    @Test
    void getBySerialNumber_shouldReturnEntity_whenFound() {
        RailcarStartupGate existing = new RailcarStartupGate();
        existing.setId(1L);
        existing.setSerialNumber("-T01250001");
        existing.setDisabled(true);

        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        RailcarStartupGate result = service.getBySerialNumber("-T01250001");
        assertNotNull(result);
        assertEquals("-T01250001", result.getSerialNumber());
        assertTrue(result.getDisabled());
    }

    @Test
    void setDisabled_shouldCreateNewRecordAndPublishRetained_whenNotExists() throws Exception {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(mapper.insert(any(RailcarStartupGate.class))).thenReturn(1);
        when(mqttOutboundChannel.send(any(Message.class))).thenReturn(true);

        boolean result = service.setDisabled("-T01250001", true, "admin");

        assertTrue(result);
        verify(mapper).insert(any(RailcarStartupGate.class));
        verify(mapper, never()).updateById(any());

        // 验证 MQTT retained 消息
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<byte[]>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mqttOutboundChannel).send(messageCaptor.capture());

        Message<byte[]> sent = messageCaptor.getValue();
        assertEquals("RAILCAR/S/-T01250001/startup", sent.getHeaders().get(MqttHeaders.TOPIC));
        assertEquals(1, sent.getHeaders().get(MqttHeaders.QOS));
        assertEquals(true, sent.getHeaders().get(MqttHeaders.RETAINED));

        String payload = new String(sent.getPayload(), StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"disabled\":true"));
    }

    @Test
    void setDisabled_shouldUpdateExistingRecordAndPublishRetained_whenExists() throws Exception {
        RailcarStartupGate existing = new RailcarStartupGate();
        existing.setId(1L);
        existing.setSerialNumber("-T01250001");
        existing.setDisabled(true);

        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(mapper.updateById(any(RailcarStartupGate.class))).thenReturn(1);
        when(mqttOutboundChannel.send(any(Message.class))).thenReturn(true);

        boolean result = service.setDisabled("-T01250001", false, "admin");

        assertTrue(result);
        verify(mapper).updateById(any(RailcarStartupGate.class));
        verify(mapper, never()).insert(any());

        // 验证 MQTT retained 消息中 disabled 已更新为 false
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<byte[]>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mqttOutboundChannel).send(messageCaptor.capture());

        Message<byte[]> sent = messageCaptor.getValue();
        String payload = new String(sent.getPayload(), StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"disabled\":false"));
    }

    @Test
    void setDisabled_shouldRejectEmptySerialNumber() {
        boolean result = service.setDisabled("", true, "admin");
        assertFalse(result);
        verify(mapper, never()).insert(any());
        verify(mqttOutboundChannel, never()).send(any());
    }
}
