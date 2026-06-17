package com.zt.cleanbot.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CityTaskStatsDTO {
    private String cityCode;        // 城市代码
    private String cityName;        // 城市名称
    private Integer totalTasks;     // 总任务数量
    private Integer successTasks;   // 成功任务数量
    private BigDecimal completionRate; // 完成率
}