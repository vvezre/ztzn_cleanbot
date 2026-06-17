package com.zt.cleanbot.controller;
import com.zt.cleanbot.model.DroneLog;
import com.zt.cleanbot.service.DroneLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/droneLog")
@Slf4j
public class DroneLogController {

    @Autowired
    private DroneLogService droneLogService;

    @GetMapping("/list")
    public List<DroneLog> getAllDroneLogs() {
        log.info("查询所有无人机日志");
        return droneLogService.getAllDroneLogs();
    }

    @PostMapping("/add")
    public boolean addDroneLog(@RequestBody DroneLog droneLog) {
        log.info("新增无人机日志");
        return droneLogService.save(droneLog);
    }

    @PutMapping("/update")
    public boolean updateDroneLog(@RequestBody DroneLog droneLog) {
        log.info("更新无人机日志");
        return droneLogService.updateById(droneLog);
    }

    @DeleteMapping("/{id}")
    public boolean deleteDroneLog(@PathVariable String id) {
        log.info("删除无人机日志: {}", id);
        return droneLogService.removeById(id);
    }

    @GetMapping("/{id}")
    public DroneLog getDroneLogById(@PathVariable String id) {
        log.info("根据ID查询无人机日志: {}", id);
        return droneLogService.getById(id);
    }

}