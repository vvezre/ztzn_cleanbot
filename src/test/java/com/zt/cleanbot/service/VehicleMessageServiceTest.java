package com.zt.cleanbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.model.Vehicle;
import com.zt.cleanbot.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VehicleMessageServiceTest {

    @Test
    void shouldNotAutoCreateVehicleFromRegisterMessage() {
        VehicleMessageService service = new VehicleMessageService();

        VehicleService vehicleService = mock(VehicleService.class);
        RedisUtil redisUtil = mock(RedisUtil.class);

        when(vehicleService.existsBySerialNumber("-D12250003")).thenReturn(false);
        when(redisUtil.setVehicle(eq("-D12250003"), any(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);

        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "vehicleService", vehicleService);
        ReflectionTestUtils.setField(service, "redisUtil", redisUtil);
        ReflectionTestUtils.setField(service, "taskLogService", mock(TaskLogService.class));
        ReflectionTestUtils.setField(service, "vehicleLogService", mock(VehicleLogService.class));
        ReflectionTestUtils.setField(service, "mqttOutboundChannel", mock(MessageChannel.class));

        service.handleRegister("-D12250003", registerPayload());

        verify(vehicleService, never()).save(any(Vehicle.class));
    }

    private String registerPayload() {
        return "{"
                + "\"timestamp\":1779765720000,"
                + "\"data\":{"
                + "\"name\":\"D12_DOCK-003\","
                + "\"brand\":\"ZT\","
                + "\"model\":\"D12_DOCK\","
                + "\"vehicleType\":\"railcar\","
                + "\"batteryCapacity\":100,"
                + "\"weight\":0,"
                + "\"cleaningWidth\":0"
                + "}"
                + "}";
    }
}
