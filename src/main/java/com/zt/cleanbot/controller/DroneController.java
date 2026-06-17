package com.zt.cleanbot.controller;
import com.zt.cleanbot.model.Drone;
import com.zt.cleanbot.service.DroneService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/drone")
@Slf4j
public class DroneController {

    @Autowired
    private DroneService droneService;

    @GetMapping("/list")
    public List<Drone> getAllDrones() {
        log.info("查询所有无人机");
        return droneService.getAllDrones();
    }

    @PostMapping("/add")
    public boolean addDrone(@RequestBody Drone drone) {
        log.info("新增无人机");
        return droneService.save(drone);
    }

    @PutMapping("/update")
    public boolean updateDrone(@RequestBody Drone drone) {
        log.info("更新无人机");
        return droneService.updateById(drone);
    }

    @DeleteMapping("/{id}")
    public boolean deleteDrone(@PathVariable Integer id) {
        log.info("删除无人机: {}", id);
        return droneService.removeById(id);
    }

    @GetMapping("/{id}")
    public Drone getDroneById(@PathVariable Integer id) {
        log.info("根据ID查询无人机: {}", id);
        return droneService.getById(id);
    }
}