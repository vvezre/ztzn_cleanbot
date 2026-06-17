package com.zt.cleanbot.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MonthlyFailStatsDTO {
    private String yearMonth;    // 年月，格式：2025-10
    private Integer totalTasks;  // 当月总任务数
    private Integer failTasks;   // 当月失败任务数
    private BigDecimal failRate; // 当月失败率
}