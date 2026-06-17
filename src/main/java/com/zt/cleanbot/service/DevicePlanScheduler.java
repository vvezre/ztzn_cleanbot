package com.zt.cleanbot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zt.cleanbot.dto.DeviceConfigRequest;
import com.zt.cleanbot.model.DevicePlan;
import com.zt.cleanbot.model.RailcarConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
public class DevicePlanScheduler {

    @Autowired
    private DevicePlanService devicePlanService;

    @Autowired
    private DeviceControlService deviceControlService;

    @Autowired
    private RailcarConfigService railcarConfigService;

    /**
     * 每分钟整点执行一次计划任务扫描
     */
    @Scheduled(cron = "0 * * * * ?")
    public void scanAndExecutePlans() {
        LocalDateTime now = LocalDateTime.now();
        String currentTimeString = now.format(DateTimeFormatter.ofPattern("HH:mm"));
        LocalDate currentDate = now.toLocalDate();

        // 查找所有启用状态的计划
        QueryWrapper<DevicePlan> query = new QueryWrapper<>();
        query.eq("status", 1)
                .eq("execute_time", currentTimeString);

        List<DevicePlan> plans = devicePlanService.list(query);

        for (DevicePlan plan : plans) {
            try {
                if (shouldExecuteToday(plan, currentDate)) {
                    executeDeviceControl(plan);
                }
            } catch (Exception e) {
                log.error("执行设备计划任务失败: planId={}", plan.getId(), e);
            }
        }
    }

    private boolean shouldExecuteToday(DevicePlan plan, LocalDate currentDate) {
        if (plan.getIsRepeat() == null || plan.getIsRepeat() == 0) {
            // 单次执行
            if (plan.getExecuteDate() != null && plan.getExecuteDate().equals(currentDate)) {
                return true;
            }
            return false;
        }

        // 重复执行
        String unit = plan.getIntervalUnit();
        Integer val = plan.getIntervalValue();
        if (unit == null || val == null || val <= 0)
            return false;

        // Bug Fix 2：优先使用显式 startDate 作为周期基准，避免删除重建后 createTime 变化导致周期漂移
        LocalDate baseDate;
        if (plan.getStartDate() != null) {
            baseDate = plan.getStartDate();
        } else if (plan.getCreateTime() != null) {
            baseDate = plan.getCreateTime().toLocalDate();
        } else {
            baseDate = currentDate;
        }

        if ("day".equals(unit)) {
            long daysBetween = ChronoUnit.DAYS.between(baseDate, currentDate);
            return daysBetween >= 0 && daysBetween % val == 0;
        } else if ("week".equals(unit)) {
            long daysBetween = ChronoUnit.DAYS.between(baseDate, currentDate);
            // Bug Fix 1：直接对总天数取模(val*7)，避免整数除法 daysBetween/7 导致同一周内多天均触发
            if (daysBetween >= 0 && daysBetween % (val * 7L) == 0) {
                int dayOfWeek = currentDate.getDayOfWeek().getValue();
                return containsValue(plan.getExecuteDays(), String.valueOf(dayOfWeek));
            }
        } else if ("month".equals(unit)) {
            long monthsBetween = ChronoUnit.MONTHS.between(baseDate.withDayOfMonth(1), currentDate.withDayOfMonth(1));
            if (monthsBetween >= 0 && monthsBetween % val == 0) {
                int dayOfMonth = currentDate.getDayOfMonth();
                return containsValue(plan.getExecuteDays(), String.valueOf(dayOfMonth));
            }
        }

        return false;
    }

    private boolean containsValue(String commaSeparatedValues, String target) {
        if (commaSeparatedValues == null || commaSeparatedValues.trim().isEmpty()) {
            return false;
        }
        String[] parts = commaSeparatedValues.split(",");
        for (String part : parts) {
            if (part.trim().equals(target)) {
                return true;
            }
        }
        return false;
    }

    private void executeDeviceControl(DevicePlan plan) {
        log.info("触发云端计划任务: 设备={}", plan.getDeviceId());

        DeviceConfigRequest request = new DeviceConfigRequest();
        request.setDeviceId(plan.getDeviceId());

        // 1. 从历史配置中填充完整参数，避免下发时覆盖设备原有配置为 0
        String deviceId = plan.getDeviceId();
        if (deviceId != null && deviceId.length() >= 10) {
            String productModel = deviceId.substring(0, 4); // 前4位，如 "-D12"
            String productNumber = deviceId.substring(deviceId.length() - 6); // 末6位，如 "250001"
            RailcarConfig config = railcarConfigService.getConfig(productModel, productNumber);
            if (config != null) {
                fillFromConfig(request, config);
                log.info("使用历史配置填充参数: deviceId={}, walkSpeed={}, brushSpeed={}",
                        deviceId, config.getWalkingSpeed(), config.getBrushSpeed());
            } else {
                log.warn("未找到历史配置，将以默认值下发: deviceId={}", deviceId);
            }
        }

        // 2. 计划任务固定以 AUTO 启动；不覆盖 enableMode（已由 fillFromConfig 从配置表取出）
        request.setControlMode(1); // 1 = AUTO 启动
        request.setUserId(plan.getUserId());
        request.setUsername("ScheduleSystem");

        deviceControlService.sendControlCommand(request);

        // 对应单次任务，执行完毕后停用
        if (plan.getIsRepeat() == null || plan.getIsRepeat() == 0) {
            DevicePlan updatePlan = new DevicePlan();
            updatePlan.setId(plan.getId());
            updatePlan.setStatus(0);
            devicePlanService.updateById(updatePlan);
        }
    }

    /**
     * 将 railcar_config 历史配置映射填入 DeviceConfigRequest
     * workWay 和时间组（time1~4）已弃用，固定不填（编码器默认输出 0000）
     */
    private void fillFromConfig(DeviceConfigRequest request, RailcarConfig config) {
        request.setCompanyCode(config.getCompanyCode());
        request.setEnableMode(config.getOperationEnable()); // 清洁使能模式，从 railcar_config 表读取
        request.setWalkSpeed(config.getWalkingSpeed());
        request.setBrushSpeed(config.getBrushSpeed());
        request.setBridgeSpeed(config.getBridgeSpeed());
        request.setEdgeDelay(config.getEdgeDetectionDelay());
        request.setBridgeTime(config.getBridgeDetectionTime());
        request.setErrorReturnTime(config.getErrorReturnTime());
        request.setHeartbeatSet(config.getHeartbeatPulse());
        request.setBatteryLowLimit(config.getBatteryLowLimit()); // 电池低电量警戒线
        request.setReserved(config.getBackup());
        // workWay / time1~4 已弃用，不填，编码默认 0
        // reserved2 使用默认值 0，编码器会自动填充
    }
}
