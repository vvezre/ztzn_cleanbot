package com.zt.cleanbot.controller;
import com.zt.cleanbot.model.VehicleLog;
import com.zt.cleanbot.service.VehicleLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/vehicleLog")
@Slf4j
public class VehicleLogController {

    @Autowired
    private VehicleLogService vehicleLogService;

    @GetMapping("/list")
    public List<VehicleLog> getAllVehicleLogs() {
        log.info("查询所有车辆日志");
        return vehicleLogService.getAllVehicleLogs();
    }

    @PostMapping("/add")
    public boolean addVehicleLog(@RequestBody VehicleLog vehicleLog) {
        log.info("新增车辆日志");
        return vehicleLogService.save(vehicleLog);
    }

    @PutMapping("/update")
    public boolean updateVehicleLog(@RequestBody VehicleLog vehicleLog) {
        log.info("更新车辆日志");
        return vehicleLogService.updateById(vehicleLog);
    }

    @DeleteMapping("/{id}")
    public boolean deleteVehicleLog(@PathVariable String id) {
        log.info("删除车辆日志: {}", id);
        return vehicleLogService.removeById(id);
    }

    @GetMapping("/{id}")
    public VehicleLog getVehicleLogById(@PathVariable String id) {
        log.info("根据ID查询车辆日志: {}", id);
        return vehicleLogService.getById(id);
    }
}