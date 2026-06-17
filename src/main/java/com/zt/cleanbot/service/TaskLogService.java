package com.zt.cleanbot.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.dto.CityTaskRankDTO;
import com.zt.cleanbot.dto.CityTaskStatsDTO;
import com.zt.cleanbot.dto.MonthlyFailStatsDTO;
import com.zt.cleanbot.model.TaskLog;

import java.util.Date;
import java.util.List;

public interface TaskLogService extends IService<TaskLog> {
    List<TaskLog> getAllTaskLogs();
    // 新增方法：根据taskId查找任务日志
    TaskLog getByTaskId(String taskId);

    // 新增方法：检查taskId是否存在
    boolean existsByTaskId(String taskId);

    // 新增方法：保存或更新任务日志
    boolean saveOrUpdateTaskLog(TaskLog taskLog);

    // 新增：根据日期区间统计各市任务数据
    List<CityTaskStatsDTO> getCityTaskStats(Date startDate, Date endDate);

    // 新增：获取最近六个月每月失败统计
    List<MonthlyFailStatsDTO> getMonthlyFailStats();

    // 新增：获取当前月市级任务数量排名前十
    List<CityTaskRankDTO> getCityTaskRankCurrentMonth();
    /**
     * 获取指定车辆最新的working状态任务
     */
    TaskLog getLatestWorkingTask(String vehicleId, String taskType);
}