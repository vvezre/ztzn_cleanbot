package com.zt.cleanbot.controller;
import com.zt.cleanbot.model.RouteLog;
import com.zt.cleanbot.service.RouteLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/route-log")
@Slf4j
public class RouteLogController {

    @Autowired
    private RouteLogService routeLogService;

    @GetMapping("/list")
    public List<RouteLog> getAllRouteLogs() {
        log.info("查询所有路径日志");
        return routeLogService.getAllRouteLogs();
    }

    @PostMapping("/add")
    public boolean addRouteLog(@RequestBody RouteLog routeLog) {
        log.info("新增路径日志");
        return routeLogService.save(routeLog);
    }

    @PutMapping("/update")
    public boolean updateRouteLog(@RequestBody RouteLog routeLog) {
        log.info("更新路径日志");
        return routeLogService.updateById(routeLog);
    }

    @DeleteMapping("/{id}")
    public boolean deleteRouteLog(@PathVariable Integer id) {
        log.info("删除路径日志: {}", id);
        return routeLogService.removeById(id);
    }

    @GetMapping("/{id}")
    public RouteLog getRouteLogById(@PathVariable Integer id) {
        log.info("根据ID查询路径日志: {}", id);
        return routeLogService.getById(id);
    }
}