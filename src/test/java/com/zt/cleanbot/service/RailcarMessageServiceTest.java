package com.zt.cleanbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.model.RailcarMessage;
import com.zt.cleanbot.model.Vehicle;
import com.zt.cleanbot.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RailcarMessageServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldPersistAndPublishStandardDStatusFrame() {
        ObjectMapper objectMapper = new ObjectMapper();
        RailcarMessageService service = new RailcarMessageService(objectMapper);

        VehicleService vehicleService = mock(VehicleService.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        DeviceStatusPublisher deviceStatusPublisher = mock(DeviceStatusPublisher.class);

        Vehicle vehicle = new Vehicle();
        vehicle.setSerialNumber("-D01250001");
        vehicle.setCompanyCode("ZTZN-PVC");
        vehicle.setProductType("-D01");
        vehicle.setProductId("250001");
        vehicle.setVehicleType("railcar");

        when(vehicleService.getBySerialNumber("-D01250001")).thenReturn(vehicle);
        when(redisUtil.getString("railcar:last-operation-mode:-D01250001")).thenReturn("cleaning");
        when(redisUtil.setVehicle(eq("-D01250001"), any(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);

        ReflectionTestUtils.setField(service, "vehicleService", vehicleService);
        ReflectionTestUtils.setField(service, "redisUtil", redisUtil);
        ReflectionTestUtils.setField(service, "deviceStatusPublisher", deviceStatusPublisher);
        ReflectionTestUtils.setField(service, "taskLogService", mock(TaskLogService.class));
        ReflectionTestUtils.setField(service, "vehicleLogService", mock(VehicleLogService.class));
        ReflectionTestUtils.setField(service, "mqttOutboundChannel", mock(MessageChannel.class));
        ReflectionTestUtils.setField(service, "railcarControlService", mock(RailcarControlService.class));
        ReflectionTestUtils.setField(service, "railcarConfigService", mock(RailcarConfigService.class));
        ReflectionTestUtils.setField(service, "commandStatusService", mock(CommandStatusService.class));

        service.handleRailcarStatus("-D01250001", standardD01StatusFrame());

        ArgumentCaptor<Object> redisPayload = ArgumentCaptor.forClass(Object.class);
        verify(redisUtil).setVehicle(eq("-D01250001"), redisPayload.capture(), eq(86400L), eq(TimeUnit.SECONDS));
        verify(deviceStatusPublisher).publishDeviceStatus(eq("-D01250001"), any(RailcarMessage.class));

        Map<String, Object> status = (Map<String, Object>) redisPayload.getValue();
        assertEquals("-D01250001", status.get("deviceId"));
        assertEquals("working", status.get("status"));
        assertEquals(85.0, (Double) status.get("battery"), 0.001);
        assertEquals(800, status.get("walkSpeed"));
        assertTrue(status.containsKey("lastUpdateTime"));
    }

    @Test
    void shouldNotAutoCreateVehicleFromRailcarStatusFrame() {
        ObjectMapper objectMapper = new ObjectMapper();
        RailcarMessageService service = new RailcarMessageService(objectMapper);

        VehicleService vehicleService = mock(VehicleService.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        DeviceStatusPublisher deviceStatusPublisher = mock(DeviceStatusPublisher.class);

        when(vehicleService.getBySerialNumber("-D12250003")).thenReturn(null);
        when(redisUtil.setVehicle(eq("-D12250003"), any(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);

        ReflectionTestUtils.setField(service, "vehicleService", vehicleService);
        ReflectionTestUtils.setField(service, "redisUtil", redisUtil);
        ReflectionTestUtils.setField(service, "deviceStatusPublisher", deviceStatusPublisher);
        ReflectionTestUtils.setField(service, "taskLogService", mock(TaskLogService.class));
        ReflectionTestUtils.setField(service, "vehicleLogService", mock(VehicleLogService.class));
        ReflectionTestUtils.setField(service, "mqttOutboundChannel", mock(MessageChannel.class));
        ReflectionTestUtils.setField(service, "railcarControlService", mock(RailcarControlService.class));
        ReflectionTestUtils.setField(service, "railcarConfigService", mock(RailcarConfigService.class));
        ReflectionTestUtils.setField(service, "commandStatusService", mock(CommandStatusService.class));

        service.handleRailcarStatus("-D12250003", d12StatusFrame());

        verify(vehicleService, never()).save(any(Vehicle.class));
    }

    private String standardD01StatusFrame() {
        return ""
                + "5A545A4E2D505643" // ZTZN-PVC
                + "2D443031"         // -D01
                + "323530303031"     // 250001
                + "0000"             // single run time
                + "00000000"         // total run time
                + "0000"             // single distance
                + "00000000"         // total distance
                + "00000000"         // longitude
                + "00000000"         // latitude
                + "0000"             // bind send disabled
                + "0000"             // interaction command
                + "0000"             // backup1
                + "0000"             // backup2
                + "0001"             // AUTO start
                + "0001"             // left single
                + "0000"             // fault code
                + "0000"             // current row
                + "0000"             // work cycle count
                + "0800"             // walk speed 800
                + "1000"             // brush speed 1000
                + "0800"             // bridge speed 800
                + "0100"             // heartbeat 1.00s
                + "0850"             // battery 85.0%
                + "0000"             // backup3
                + "0000";            // backup4
    }

    private String d12StatusFrame() {
        return ""
                + "5A545A4E2D505643" // ZTZN-PVC
                + "2D443132"         // -D12
                + "323530303033"     // 250003
                + "0002"
                + "0051"
                + "0000"
                + "0000"
                + "0000"
                + "0000"
                + "2926"
                + "1189"
                + "3346"
                + "0320"
                + "0001"             // bind send enabled
                + "0001"             // interaction command
                + "0000"             // backup1
                + "0000"             // backup2
                + "0006"             // control
                + "0001"             // work data complete
                + "0003"             // d12 work way
                + "0001"             // backup
                + "0000"             // current row
                + "0000"             // work cycle count
                + "0000"             // walk speed
                + "0500"             // fast speed 500
                + "0000"             // slow speed
                + "0000"
                + "0000"             // backup3
                + "0000";            // backup4
    }
}
