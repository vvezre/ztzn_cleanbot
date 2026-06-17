package com.zt.cleanbot.controller;
import com.zt.cleanbot.dto.CityTaskRankDTO;
import com.zt.cleanbot.dto.CityTaskStatsDTO;
import com.zt.cleanbot.dto.MonthlyFailStatsDTO;
import com.zt.cleanbot.model.TaskLog;
import com.zt.cleanbot.service.TaskLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/task-log")
@Slf4j
public class TaskLogController {

    @Autowired
    private TaskLogService taskLogService;

    @GetMapping("/list")
    public List<TaskLog> getAllTaskLogs() {
        log.info("查询所有任务日志");
        return taskLogService.getAllTaskLogs();
    }

    @PostMapping("/add")
    public boolean addTaskLog(@RequestBody TaskLog taskLog) {
        log.info("新增任务日志");
        return taskLogService.save(taskLog);
    }

    @PutMapping("/update")
    public boolean updateTaskLog(@RequestBody TaskLog taskLog) {
        log.info("更新任务日志");
        return taskLogService.updateById(taskLog);
    }

    @DeleteMapping("/{id}")
    public boolean deleteTaskLog(@PathVariable String id) {
        log.info("删除任务日志: {}", id);
        return taskLogService.removeById(id);
    }

    @GetMapping("/{id}")
    public TaskLog getTaskLogById(@PathVariable String id) {
        log.info("根据ID查询任务日志: {}", id);
        return taskLogService.getById(id);
    }

    @GetMapping("/city-stats")
    public List<CityTaskStatsDTO> getCityTaskStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        log.info("查询各市任务统计，日期区间: {} 至 {}", startDate, endDate);

        // 将结束日期设置为当天的23:59:59，确保包含当天的所有数据
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(endDate);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        Date actualEndDate = calendar.getTime();

        return taskLogService.getCityTaskStats(startDate, actualEndDate);
    }


    // 新增：获取最近六个月每月失败统计
    @GetMapping("/monthly-fail-stats")
    public List<MonthlyFailStatsDTO> getMonthlyFailStats() {
        log.info("查询最近六个月每月失败统计");
        return taskLogService.getMonthlyFailStats();
    }
    // 新增：当前月市级任务数量排名前十
    @GetMapping("/city-task-rank")
    public List<CityTaskRankDTO> getCityTaskRankCurrentMonth() {
        log.info("查询当前月市级任务数量排名前十");
        return taskLogService.getCityTaskRankCurrentMonth();
    }

}