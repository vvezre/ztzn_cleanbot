package com.zt.cleanbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dto.VehicleIntegratedDTO;
import com.zt.cleanbot.dto.VehicleRedisData;
import com.zt.cleanbot.model.Vehicle;
import com.zt.cleanbot.service.LocalGeoService;
import com.zt.cleanbot.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VehicleServiceImplTest {

    @Test
    void getIntegratedVehiclesReturnsOneRowPerDeviceIdWhenRedisHasDuplicateKeys() {
        TestVehicleServiceImpl service = new TestVehicleServiceImpl(Arrays.asList(
                vehicle("-D01250001", "D01干挂基本型-001", "D01干挂基本型"),
                vehicle("-D12250001", "D12干挂接驳车-001", "D12干挂接驳车")));
        RedisUtil redisUtil = mock(RedisUtil.class);
        LocalGeoService localGeoService = mock(LocalGeoService.class);

        when(redisUtil.getDeviceKeysOnly()).thenReturn(new LinkedHashSet<String>(
                Arrays.asList("vehicle:state:1", "vehicle:shadow:1", "vehicle:state:2")));
        when(redisUtil.getVehicle("vehicle:state:1")).thenReturn(redisData("-D01250001", 80.0, 1000L));
        when(redisUtil.getVehicle("vehicle:shadow:1")).thenReturn(redisData("-D01250001", 81.0, 2000L));
        when(redisUtil.getVehicle("vehicle:state:2")).thenReturn(redisData("-D12250001", 75.0, 1500L));

        Map<String, String> provinces = new HashMap<String, String>();
        provinces.put("-D01250001", "江苏省");
        provinces.put("-D12250001", "江苏省");
        when(localGeoService.getProvincesByLocations(anyMap())).thenReturn(provinces);

        ReflectionTestUtils.setField(service, "redisUtil", redisUtil);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "localGeoService", localGeoService);

        List<VehicleIntegratedDTO> result = service.getIntegratedVehicles();

        assertEquals(2, result.size());
        assertEquals(Arrays.asList("-D01250001", "-D12250001"),
                result.stream().map(VehicleIntegratedDTO::getDeviceId).collect(Collectors.toList()));
        assertEquals(Arrays.asList("-D01250001", "-D12250001"), service.queriedSerialNumbers);
    }

    private VehicleRedisData redisData(String deviceId, Double battery, Long lastUpdateTime) {
        VehicleRedisData data = new VehicleRedisData();
        VehicleRedisData.LocationDTO location = new VehicleRedisData.LocationDTO();
        location.setLon(120.0);
        location.setLat(31.0);
        data.setDeviceId(deviceId);
        data.setBattery(battery);
        data.setStatus("DISPATCHED");
        data.setLastUpdateTime(lastUpdateTime);
        data.setLocation(location);
        return data;
    }

    private Vehicle vehicle(String serialNumber, String name, String model) {
        Vehicle vehicle = new Vehicle();
        vehicle.setSerialNumber(serialNumber);
        vehicle.setName(name);
        vehicle.setModel(model);
        vehicle.setBrand("ZT");
        vehicle.setVehicleType("railcar");
        return vehicle;
    }

    private static class TestVehicleServiceImpl extends VehicleServiceImpl {
        private final Map<String, Vehicle> vehiclesBySerialNumber = new HashMap<String, Vehicle>();
        private List<String> queriedSerialNumbers = new ArrayList<String>();

        private TestVehicleServiceImpl(List<Vehicle> vehicles) {
            for (Vehicle vehicle : vehicles) {
                vehiclesBySerialNumber.put(vehicle.getSerialNumber(), vehicle);
            }
        }

        @Override
        public List<Vehicle> getBySerialNumbers(List<String> serialNumbers) {
            queriedSerialNumbers = new ArrayList<String>(serialNumbers);
            List<Vehicle> vehicles = new ArrayList<Vehicle>();
            for (String serialNumber : serialNumbers) {
                Vehicle vehicle = vehiclesBySerialNumber.get(serialNumber);
                if (vehicle != null && !vehicles.contains(vehicle)) {
                    vehicles.add(vehicle);
                }
            }
            return vehicles;
        }
    }
}
