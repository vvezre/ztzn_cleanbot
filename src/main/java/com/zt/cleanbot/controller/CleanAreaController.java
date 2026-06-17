package com.zt.cleanbot.controller;
import com.zt.cleanbot.model.CleanArea;
import com.zt.cleanbot.service.CleanAreaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/clean-area")
@Slf4j
public class CleanAreaController {

    @Autowired
    private CleanAreaService cleanAreaService;

    @GetMapping("/list")
    public List<CleanArea> getAllCleanAreas() {
        log.info("查询所有清扫区域");
        return cleanAreaService.getAllCleanAreas();
    }

    @PostMapping("/add")
    public boolean addCleanArea(@RequestBody CleanArea cleanArea) {
        log.info("新增清扫区域");
        return cleanAreaService.save(cleanArea);
    }

    @PutMapping("/update")
    public boolean updateCleanArea(@RequestBody CleanArea cleanArea) {
        log.info("更新清扫区域");
        return cleanAreaService.updateById(cleanArea);
    }

    @DeleteMapping("/{id}")
    public boolean deleteCleanArea(@PathVariable Integer id) {
        log.info("删除清扫区域: {}", id);
        return cleanAreaService.removeById(id);
    }

    @GetMapping("/{id}")
    public CleanArea getCleanAreaById(@PathVariable Integer id) {
        log.info("根据ID查询清扫区域: {}", id);
        return cleanAreaService.getById(id);
    }
}