package com.zt.cleanbot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.DeviceControlAuditMapper;
import com.zt.cleanbot.model.DeviceControlAudit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备控制审计日志服务
 */
@Slf4j
@Service
public class DeviceControlAuditService extends ServiceImpl<DeviceControlAuditMapper, DeviceControlAudit> {

    /**
     * 保存审计日志
     */
    public boolean saveAuditLog(DeviceControlAudit audit) {
        try {
            return this.save(audit);
        } catch (Exception e) {
            log.error("保存审计日志失败", e);
            return false;
        }
    }

    /**
     * 查询设备的审计日志
     */
    public List<DeviceControlAudit> getAuditLogsByDevice(String deviceId, int days) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);

        QueryWrapper<DeviceControlAudit> wrapper = new QueryWrapper<>();
        wrapper.eq("device_id", deviceId)
                .ge("operation_time", startTime)
                .orderByDesc("operation_time");

        return this.list(wrapper);
    }

    /**
     * 查询用户的审计日志
     */
    public List<DeviceControlAudit> getAuditLogsByUser(Integer userId, int days) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);

        QueryWrapper<DeviceControlAudit> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .ge("operation_time", startTime)
                .orderByDesc("operation_time");

        return this.list(wrapper);
    }

    /**
     * 查询紧急模式操作日志
     */
    public List<DeviceControlAudit> getEmergencyModeLogs(int days) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);

        QueryWrapper<DeviceControlAudit> wrapper = new QueryWrapper<>();
        wrapper.eq("is_emergency_mode", true)
                .ge("operation_time", startTime)
                .orderByDesc("operation_time");

        return this.list(wrapper);
    }
}
