package com.zt.cleanbot.controller;
import com.zt.cleanbot.model.CleanRoute;
import com.zt.cleanbot.service.CleanRouteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/clean-route")
@Slf4j
public class CleanRouteController {

    @Autowired
    private CleanRouteService cleanRouteService;

    @GetMapping("/list")
    public List<CleanRoute> getAllCleanRoutes() {
        log.info("查询所有清扫路径");
        return cleanRouteService.getAllCleanRoutes();
    }

    @PostMapping("/add")
    public boolean addCleanRoute(@RequestBody CleanRoute cleanRoute) {
        log.info("新增清扫路径");
        return cleanRouteService.save(cleanRoute);
    }

    @PutMapping("/update")
    public boolean updateCleanRoute(@RequestBody CleanRoute cleanRoute) {
        log.info("更新清扫路径");
        return cleanRouteService.updateById(cleanRoute);
    }

    @DeleteMapping("/{id}")
    public boolean deleteCleanRoute(@PathVariable Integer id) {
        log.info("删除清扫路径: {}", id);
        return cleanRouteService.removeById(id);
    }

    @GetMapping("/{id}")
    public CleanRoute getCleanRouteById(@PathVariable Integer id) {
        log.info("根据ID查询清扫路径: {}", id);
        return cleanRouteService.getById(id);
    }
}