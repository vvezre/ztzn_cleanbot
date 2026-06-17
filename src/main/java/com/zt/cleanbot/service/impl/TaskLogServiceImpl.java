package com.zt.cleanbot.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.TaskLogMapper;
import com.zt.cleanbot.dto.CityTaskRankDTO;
import com.zt.cleanbot.dto.CityTaskStatsDTO;
import com.zt.cleanbot.dto.MonthlyFailStatsDTO;
import com.zt.cleanbot.model.TaskLog;
import com.zt.cleanbot.service.TaskLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class TaskLogServiceImpl extends ServiceImpl<TaskLogMapper, TaskLog> implements TaskLogService {

    @Override
    public List<TaskLog> getAllTaskLogs() {
        return this.list();
    }


    @Override
    public TaskLog getByTaskId(String taskId) {
        QueryWrapper<TaskLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", taskId);
        return this.getOne(queryWrapper);
    }

    @Override
    public boolean existsByTaskId(String taskId) {
        QueryWrapper<TaskLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", taskId);
        return this.count(queryWrapper) > 0;
    }

    @Override
    @Transactional
    public boolean saveOrUpdateTaskLog(TaskLog taskLog) {
        if (taskLog == null || taskLog.getId() == null) {
            return false;
        }

        // 检查任务是否存在
        boolean exists = existsByTaskId(taskLog.getId());

        if (exists) {
            // 更新操作：只更新非空字段
            return updateTaskLogSelective(taskLog);
        } else {
            // 插入操作
            return this.save(taskLog);
        }
    }

    /**
     * 选择性更新任务日志，只更新非空字段
     */
    private boolean updateTaskLogSelective(TaskLog taskLog) {
        UpdateWrapper<TaskLog> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", taskLog.getId());

        // 只设置非空的字段
        if (taskLog.getVehicleId() != null) {
            updateWrapper.set("vehicle_id", taskLog.getVehicleId());
        }
        if (taskLog.getDroneId() != null) {
            updateWrapper.set("drone_id", taskLog.getDroneId());
        }
        if (taskLog.getAreaId() != null) {
            updateWrapper.set("area_id", taskLog.getAreaId());
        }
        if (taskLog.getRouteId() != null) {
            updateWrapper.set("route_id", taskLog.getRouteId());
        }
        if (taskLog.getStartTime() != null) {
            updateWrapper.set("start_time", taskLog.getStartTime());
        }
        if (taskLog.getEndTime() != null) {
            updateWrapper.set("end_time", taskLog.getEndTime());
        }
        if (taskLog.getStatus() != null) {
            updateWrapper.set("status", taskLog.getStatus());
        }
        if (taskLog.getCleaningArea() != null) {
            updateWrapper.set("cleaning_area", taskLog.getCleaningArea());
        }
        if (taskLog.getEfficiency() != null) {
            updateWrapper.set("efficiency", taskLog.getEfficiency());
        }
        if (taskLog.getPowerRestored() != null) {
            updateWrapper.set("power_restored", taskLog.getPowerRestored());
        }
        if (taskLog.getCleaningMode() != null) {
            updateWrapper.set("cleaning_mode", taskLog.getCleaningMode());
        }
        if (taskLog.getTaskType() != null) {
            updateWrapper.set("task_type", taskLog.getTaskType());
        }

        return this.update(updateWrapper);
    }


    @Override
    public List<CityTaskStatsDTO> getCityTaskStats(Date startDate, Date endDate) {
        return this.baseMapper.selectCityTaskStats(startDate, endDate);
    }

    @Override
    public List<MonthlyFailStatsDTO> getMonthlyFailStats() {
        return this.baseMapper.selectMonthlyFailStats();
    }

    @Override
    public List<CityTaskRankDTO> getCityTaskRankCurrentMonth() {
        return this.baseMapper.selectCityTaskRankCurrentMonth();
    }


    @Override
    public TaskLog getLatestWorkingTask(String vehicleId, String taskType) {
        QueryWrapper<TaskLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("vehicle_id", vehicleId)
                .eq("task_type", taskType)
                .eq("status", "working")
                .orderByDesc("start_time")
                .last("LIMIT 1");
        return this.getOne(queryWrapper);
    }
}