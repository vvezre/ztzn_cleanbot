package com.zt.cleanbot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dto.DeviceShadowStatus;
import com.zt.cleanbot.dto.MiniAppRobotResponse;
import com.zt.cleanbot.model.Vehicle;
import com.zt.cleanbot.service.DeviceShadowService;
import com.zt.cleanbot.service.RailcarConfigService;
import com.zt.cleanbot.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiniAppDeviceControllerTest {

    @Test
    void shouldKeepLegacyOnlineFieldsOfflineWhenShadowIsOffline() {
        MiniAppDeviceController controller = new MiniAppDeviceController();
        DeviceShadowService deviceShadowService = mock(DeviceShadowService.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        RailcarConfigService railcarConfigService = mock(RailcarConfigService.class);

        ReflectionTestUtils.setField(controller, "deviceShadowService", deviceShadowService);
        ReflectionTestUtils.setField(controller, "redisUtil", redisUtil);
        ReflectionTestUtils.setField(controller, "railcarConfigService", railcarConfigService);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());

        Vehicle vehicle = new Vehicle();
        vehicle.setId(1);
        vehicle.setSerialNumber("-T01250002");
        vehicle.setProductType("-T01");
        vehicle.setProductId("250002");

        DeviceShadowStatus shadow = new DeviceShadowStatus();
        shadow.setExists(true);
        shadow.setDeviceId("-T01250002");
        shadow.setDeviceType("T_PYTHON");
        shadow.setOnlineState("OFFLINE");
        shadow.setMissionState("IDLE");

        Map<String, Object> redisData = new HashMap<String, Object>();
        redisData.put("deviceId", "-T01250002");
        redisData.put("status", "active");
        redisData.put("battery", 49.8);

        when(deviceShadowService.getDeviceShadow("-T01250002")).thenReturn(shadow);
        when(redisUtil.getVehicle("-T01250002")).thenReturn(redisData);

        MiniAppRobotResponse response = ReflectionTestUtils.invokeMethod(
                controller,
                "convertToMiniAppRobot",
                vehicle);

        assertFalse(Boolean.TRUE.equals(response.getOnline()));
        assertEquals("offline", response.getStatus());
        assertEquals("OFFLINE", response.getOnlineState());
    }
}
