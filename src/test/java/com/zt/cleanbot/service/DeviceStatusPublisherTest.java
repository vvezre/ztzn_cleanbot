package com.zt.cleanbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.model.RailcarMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeviceStatusPublisherTest {

    @Test
    void shouldPublishRunningStatusForDSeriesAutoStartFrame() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        DeviceStatusPublisher publisher = new DeviceStatusPublisher();

        ReflectionTestUtils.setField(publisher, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(publisher, "redisTemplate", redisTemplate);

        RailcarMessage message = new RailcarMessage();
        message.setOperationModeDescription("AUTO_STARTING");
        message.setBatteryLevel(85.0);

        publisher.publishDeviceStatus("-D01250001", message);

        ArgumentCaptor<String> channel = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(channel.capture(), body.capture());

        JsonNode notification = objectMapper.readTree(body.getValue());
        assertEquals("device:status:update:-D01250001", channel.getValue());
        assertEquals("-D01250001", notification.path("deviceId").asText());
        assertEquals("running", notification.path("status").asText());
    }

    @Test
    void shouldNormalizeDisabledRedisStatusAsIdleForMiniApp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        DeviceStatusPublisher publisher = new DeviceStatusPublisher();

        ReflectionTestUtils.setField(publisher, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(publisher, "redisTemplate", redisTemplate);

        Map<String, Object> redisData = new java.util.HashMap<>();
        redisData.put("status", "disabled");
        redisData.put("onlineState", "ONLINE");

        publisher.publishDeviceStatus("-T01250001", redisData);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(anyString(), body.capture());

        JsonNode notification = objectMapper.readTree(body.getValue());
        assertEquals("idle", notification.path("statusNormalized").asText());
    }
}
