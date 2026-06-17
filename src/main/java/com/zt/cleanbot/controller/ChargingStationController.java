package com.zt.cleanbot.controller;
import com.zt.cleanbot.model.ChargingStation;
import com.zt.cleanbot.service.ChargingStationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/charging-station")
@Slf4j
public class ChargingStationController {

    @Autowired
    private ChargingStationService chargingStationService;

    @GetMapping("/list")
    public List<ChargingStation> getAllChargingStations() {
        log.info("查询所有充电站");
        return chargingStationService.getAllChargingStations();
    }

    @PostMapping("/add")
    public boolean addChargingStation(@RequestBody ChargingStation chargingStation) {
        log.info("新增充电站");
        return chargingStationService.save(chargingStation);
    }

    @PutMapping("/update")
    public boolean updateChargingStation(@RequestBody ChargingStation chargingStation) {
        log.info("更新充电站");
        return chargingStationService.updateById(chargingStation);
    }

    @DeleteMapping("/{id}")
    public boolean deleteChargingStation(@PathVariable Integer id) {
        log.info("删除充电站: {}", id);
        return chargingStationService.removeById(id);
    }

    @GetMapping("/{id}")
    public ChargingStation getChargingStationById(@PathVariable Integer id) {
        log.info("根据ID查询充电站: {}", id);
        return chargingStationService.getById(id);
    }
}