package com.zt.cleanbot.dto;

import lombok.Data;

@Data
public class CityTaskRankDTO {
    private String cityCode;     // 市行政码
    private String cityName;     // 市名
    private Integer totalTasks;  // 总任务数量
    private Integer successTasks; // 成功任务数量
}