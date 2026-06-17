package com.zt.cleanbot.controller;
import com.zt.cleanbot.dto.VehicleIntegratedDTO;
import com.zt.cleanbot.model.Vehicle;
import com.zt.cleanbot.service.LocalGeoService;
import com.zt.cleanbot.service.VehicleMessageService;
import com.zt.cleanbot.service.VehicleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/vehicle")
@Slf4j
public class VehicleController {

    // 管理员角色ID列表（超级管理员=1，设备管理员=2）
    private static final List<Integer> ADMIN_ROLE_IDS = Arrays.asList(1, 2);

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private LocalGeoService localGeoService;

    @Autowired
    private VehicleMessageService vehicleMessageService;

    @GetMapping("/list")
    public List<Vehicle> getAllVehicles(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        Integer roleId = (Integer) request.getAttribute("roleId");
        String username = (String) request.getAttribute("username");

        log.info("查询车辆列表 - 用户: {}, 角色ID: {}", username, roleId);

        // 管理员返回所有设备
        if (roleId != null && ADMIN_ROLE_IDS.contains(roleId)) {
            log.info("管理员用户 {} 获取所有设备", username);
            return vehicleService.getAllVehicles();
        }

        // 普通用户返回绑定的设备
        if (userId != null) {
            log.info("普通用户 {} 获取绑定的设备", username);
            return vehicleService.getVehiclesByUserId(userId);
        }

        // 未认证用户返回空列表
        log.warn("未认证用户尝试获取设备列表");
        return Collections.emptyList();
    }

    @PostMapping("/add")
    public boolean addVehicle(@RequestBody Vehicle vehicle) {
        log.info("新增车辆");
        return vehicleService.save(vehicle);
    }

    @PutMapping("/update")
    public boolean updateVehicle(@RequestBody Vehicle vehicle) {
        log.info("更新车辆");
        return vehicleService.updateById(vehicle);
    }

    @DeleteMapping("/{id}")
    public boolean deleteVehicle(@PathVariable Integer id) {
        log.info("删除车辆: {}", id);
        return vehicleService.removeById(id);
    }

    @GetMapping("/{id}")
    public Vehicle getVehicleById(@PathVariable Integer id) {
        log.info("根据ID查询车辆: {}", id);
        return vehicleService.getById(id);
    }

    // 在VehicleController中添加新接口
    @GetMapping("/integrated-list")
    public List<VehicleIntegratedDTO> getIntegratedVehicles() {
        log.info("查询整合后的车辆数据");
        return vehicleService.getIntegratedVehicles();
    }



    // 根据省份查询整合后的车辆数据（路径参数）
    @GetMapping("/integrated-list/{province}")
    public List<VehicleIntegratedDTO> getIntegratedVehiclesByProvince(@PathVariable String province) {
        log.info("根据省份查询整合后的车辆数据: {}", province);
        return vehicleService.getIntegratedVehiclesByProvince(province);
    }

    // 根据省份查询整合后的车辆数据（查询参数）
    @GetMapping("/integrated-list/by-province")
    public List<VehicleIntegratedDTO> getIntegratedVehiclesByProvinceParam(@RequestParam String province) {
        log.info("根据省份查询整合后的车辆数据: {}", province);
        return vehicleService.getIntegratedVehiclesByProvince(province);
    }

    // 新增：获取所有省份列表
    @GetMapping("/provinces")
    public List<String> getAllProvinces() {
        log.info("获取所有省份列表");
        return localGeoService.getAllProvinceNames();
    }

    // 新增：根据经纬度查询省份
    @GetMapping("/location-to-province")
    public String getProvinceByLocation(@RequestParam Double longitude, @RequestParam Double latitude) {
        log.info("根据经纬度查询省份: longitude={}, latitude={}", longitude, latitude);
        return localGeoService.getProvinceByLocation(longitude, latitude);
    }



    // 或者使用GET方式（根据需求选择）
    @GetMapping("/{deviceId}/command")
    public ResponseEntity<Boolean> sendCommandToVehicleGet(
            @PathVariable String deviceId,
            @RequestParam String command) {
        log.info("发送命令到车辆(GET) - 设备ID: {}, 命令: {}", deviceId, command);
        try {
            boolean result = vehicleMessageService.sendCommandToVehicle(deviceId, command);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("发送命令失败 - 设备ID: {}, 命令: {}", deviceId, command, e);
            return ResponseEntity.internalServerError().body(false);
        }
    }
}