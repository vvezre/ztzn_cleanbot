package com.zt.cleanbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dto.CommandStatusSnapshot;
import com.zt.cleanbot.dto.DeviceShadowStatus;
import com.zt.cleanbot.model.Vehicle;
import com.zt.cleanbot.utils.RedisUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceShadowServiceTest {

    @Test
    void shouldBuildTPythonShadowWithNormalizedStates() {
        ObjectMapper objectMapper = new ObjectMapper();
        VehicleService vehicleService = mock(VehicleService.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        CommandStatusService commandStatusService = mock(CommandStatusService.class);
        DeviceShadowService service = new DeviceShadowService(objectMapper, vehicleService, redisUtil, commandStatusService);

        Vehicle vehicle = new Vehicle();
        vehicle.setId(1);
        vehicle.setSerialNumber("-T01250001");
        vehicle.setProductType("-T01");
        vehicle.setProductId("250001");
        vehicle.setCompanyCode("ZTZN-PVC");
        vehicle.setName("T01-001");

        Map<String, Object> redisData = new HashMap<String, Object>();
        redisData.put("deviceId", "-T01250001");
        redisData.put("status", "working");
        redisData.put("battery", 55.0);
        redisData.put("moveJudge", true);
        redisData.put("tracking", true);
        redisData.put("pathPlanning", "left");
        redisData.put("lastUpdateTime", System.currentTimeMillis());

        when(vehicleService.getBySerialNumber("-T01250001")).thenReturn(vehicle);
        when(redisUtil.getVehicle("-T01250001")).thenReturn(redisData);
        when(commandStatusService.getLatestCommandStatusByDevice("-T01250001")).thenReturn(missingCommandSnapshot());

        DeviceShadowStatus shadow = service.getDeviceShadow("-T01250001");

        assertTrue(Boolean.TRUE.equals(shadow.getExists()));
        assertEquals("T_PYTHON", shadow.getDeviceType());
        assertEquals("ONLINE", shadow.getOnlineState());
        assertEquals("RUNNING", shadow.getMissionState());
        assertEquals("BUSY", shadow.getControlState());
        assertTrue(shadow.getSupportedActions().contains("START_CLEAN"));
        assertTrue(shadow.getSupportedStatusFields().contains("tracking"));
        assertEquals(Boolean.TRUE, shadow.getDetail().get("tracking"));
    }

    @Test
    void shouldIgnoreDisabledEnableStateForTPythonShadow() {
        ObjectMapper objectMapper = new ObjectMapper();
        VehicleService vehicleService = mock(VehicleService.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        CommandStatusService commandStatusService = mock(CommandStatusService.class);
        DeviceShadowService service = new DeviceShadowService(objectMapper, vehicleService, redisUtil, commandStatusService);

        Vehicle vehicle = new Vehicle();
        vehicle.setId(11);
        vehicle.setSerialNumber("-T01250011");
        vehicle.setProductType("-T01");
        vehicle.setProductId("250011");

        Map<String, Object> redisData = new HashMap<String, Object>();
        redisData.put("deviceId", "-T01250011");
        redisData.put("status", "disabled");
        redisData.put("missionState", "DISABLED");
        redisData.put("controlState", "DISABLED");
        redisData.put("healthState", "WARN");
        redisData.put("faultState", "LOWER_MACHINE_DISABLED");
        redisData.put("lastUpdateTime", System.currentTimeMillis());

        when(vehicleService.getBySerialNumber("-T01250011")).thenReturn(vehicle);
        when(redisUtil.getVehicle("-T01250011")).thenReturn(redisData);
        when(commandStatusService.getLatestCommandStatusByDevice("-T01250011")).thenReturn(missingCommandSnapshot());

        DeviceShadowStatus shadow = service.getDeviceShadow("-T01250011");

        assertEquals("ONLINE", shadow.getOnlineState());
        assertEquals("IDLE", shadow.getMissionState());
        assertEquals("READY", shadow.getControlState());
        assertEquals("NORMAL", shadow.getHealthState());
        assertEquals("NONE", shadow.getFaultState());
    }

    @Test
    void shouldMarkTDeviceOfflineWhenLastUpdateExpiresEvenIfOnlineStateIsOnline() {
        ObjectMapper objectMapper = new ObjectMapper();
        VehicleService vehicleService = mock(VehicleService.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        CommandStatusService commandStatusService = mock(CommandStatusService.class);
        DeviceShadowService service = new DeviceShadowService(objectMapper, vehicleService, redisUtil, commandStatusService);

        Vehicle vehicle = new Vehicle();
        vehicle.setId(12);
        vehicle.setSerialNumber("-T01250012");
        vehicle.setProductType("-T01");
        vehicle.setProductId("250012");

        Map<String, Object> redisData = new HashMap<String, Object>();
        redisData.put("deviceId", "-T01250012");
        redisData.put("status", "active");
        redisData.put("onlineState", "ONLINE");
        redisData.put("lastUpdateTime", System.currentTimeMillis() - 70_000L);

        when(vehicleService.getBySerialNumber("-T01250012")).thenReturn(vehicle);
        when(redisUtil.getVehicle("-T01250012")).thenReturn(redisData);
        when(commandStatusService.getLatestCommandStatusByDevice("-T01250012")).thenReturn(missingCommandSnapshot());

        DeviceShadowStatus shadow = service.getDeviceShadow("-T01250012");

        assertEquals("OFFLINE", shadow.getOnlineState());
        assertEquals("IDLE", shadow.getMissionState());
        assertEquals("STOPPED", shadow.getControlState());
        assertEquals("COMM_ERROR", shadow.getFaultState());
    }

    @Test
    void shouldBuildD12ShadowWithBindingCapabilities() {
        ObjectMapper objectMapper = new ObjectMapper();
        VehicleService vehicleService = mock(VehicleService.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        CommandStatusService commandStatusService = mock(CommandStatusService.class);
        DeviceShadowService service = new DeviceShadowService(objectMapper, vehicleService, redisUtil, commandStatusService);

        Vehicle vehicle = new Vehicle();
        vehicle.setId(2);
        vehicle.setSerialNumber("-D12250001");
        vehicle.setProductType("-D12");
        vehicle.setProductId("250001");
        vehicle.setCompanyCode("ZTZN-PVC");
        vehicle.setName("D12-001");

        Map<String, Object> redisData = new HashMap<String, Object>();
        redisData.put("deviceId", "-D12250001");
        redisData.put("status", "active");
        redisData.put("battery", 88.0);
        redisData.put("lastUpdateTime", System.currentTimeMillis());
        redisData.put("currentRowPosition", 6);

        when(vehicleService.getBySerialNumber("-D12250001")).thenReturn(vehicle);
        when(redisUtil.getVehicle("-D12250001")).thenReturn(redisData);
        when(commandStatusService.getLatestCommandStatusByDevice("-D12250001")).thenReturn(missingCommandSnapshot());

        DeviceShadowStatus shadow = service.getDeviceShadow("-D12250001");

        assertEquals("D_IOT", shadow.getDeviceType());
        assertEquals("ONLINE", shadow.getOnlineState());
        assertEquals("READY", shadow.getControlState());
        assertTrue(shadow.getSupportedActions().contains("BIND_DEVICE"));
        assertTrue(shadow.getSupportedActions().contains("UNBIND_DEVICE"));
        assertEquals(6, shadow.getDetail().get("current_row_position"));
    }

    @Test
    void shouldKeepDDeviceOnlineUntilHeartbeatIntervalPlusOneMinuteExpires() {
        ObjectMapper objectMapper = new ObjectMapper();
        VehicleService vehicleService = mock(VehicleService.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        CommandStatusService commandStatusService = mock(CommandStatusService.class);
        DeviceShadowService service = new DeviceShadowService(objectMapper, vehicleService, redisUtil, commandStatusService);

        Vehicle vehicle = new Vehicle();
        vehicle.setId(3);
        vehicle.setSerialNumber("-D01250001");
        vehicle.setProductType("-D01");
        vehicle.setProductId("250001");

        Map<String, Object> redisData = new HashMap<String, Object>();
        redisData.put("deviceId", "-D01250001");
        redisData.put("status", "active");
        redisData.put("heartbeat", 100.0);
        redisData.put("lastUpdateTime", System.currentTimeMillis() - 150_000L);

        when(vehicleService.getBySerialNumber("-D01250001")).thenReturn(vehicle);
        when(redisUtil.getVehicle("-D01250001")).thenReturn(redisData);
        when(commandStatusService.getLatestCommandStatusByDevice("-D01250001")).thenReturn(missingCommandSnapshot());

        DeviceShadowStatus shadow = service.getDeviceShadow("-D01250001");

        assertEquals("ONLINE", shadow.getOnlineState());

        redisData.put("lastUpdateTime", System.currentTimeMillis() - 170_000L);
        shadow = service.getDeviceShadow("-D01250001");

        assertEquals("OFFLINE", shadow.getOnlineState());
    }

    @Test
    void shouldReturnMissingShadowWhenDeviceDoesNotExist() {
        ObjectMapper objectMapper = new ObjectMapper();
        VehicleService vehicleService = mock(VehicleService.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        CommandStatusService commandStatusService = mock(CommandStatusService.class);
        DeviceShadowService service = new DeviceShadowService(objectMapper, vehicleService, redisUtil, commandStatusService);

        when(vehicleService.getBySerialNumber("-D01259999")).thenReturn(null);
        when(redisUtil.getVehicle("-D01259999")).thenReturn(null);
        when(commandStatusService.getLatestCommandStatusByDevice("-D01259999")).thenReturn(missingCommandSnapshot());

        DeviceShadowStatus shadow = service.getDeviceShadow("-D01259999");

        assertFalse(Boolean.TRUE.equals(shadow.getExists()));
        assertEquals("OFFLINE", shadow.getOnlineState());
        assertEquals("COMM_ERROR", shadow.getFaultState());
        assertTrue(shadow.getSupportedActions().isEmpty());
    }

    private CommandStatusSnapshot missingCommandSnapshot() {
        CommandStatusSnapshot snapshot = new CommandStatusSnapshot();
        snapshot.setExists(false);
        snapshot.setStatus("UNKNOWN");
        return snapshot;
    }
}
