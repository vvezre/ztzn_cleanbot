package com.zt.cleanbot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

/**
 * 轨道车控制消息模型 - 用于服务器向小车发送设置
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RailcarControlMessage {

    // 基础信息
    private String companyCode;              // 公司代号 (8字符)
    private String productModel;             // 产品型号 (4字符) GG01/GG02/GG03
    private String productNumber;            // 产品编号 (6字符) 如: 250001

    // 工作方式设置
    private Integer workMode;                // 运行方式: 0-无效,1-每日,2-每月,3-每年,4-每周

    // 工作时间组设置 (4组)
    private List<WorkTimeGroup> workTimeGroups;

    // 工作模式设置
    private Integer operationMode;           // 运行控制: 0-无效,1-Auto,2-Stop,3-Reset,4-Continuous,5-Manual
    private Integer operationEnable;         // 运行使能: 0-无效,1-检+左起+单,2-检+左起+双,3-检+右起+单,4-检+右起+双

    // 参数设置
    private Integer edgeDetectionDelay;      // 到边检测延时设置 (ms)
    private Integer bridgeDetectionTime;     // 垮桥检测时间设置 (ms)
    private Integer errorReturnTime;         // 纠错返回时间设置 (10ms)
    private Integer walkingSpeed;            // 行走速度设置 (0.1%)
    private Integer brushSpeed;              // 滚刷速度设置 (0.1%)
    private Integer bridgeSpeed;             // 垮桥速度设置 (0.1%)
    private Integer heartbeatPulse;          // 心跳脉冲设置 (10ms)
    private Integer backup;                  // 备用设置

    // 内部类 - 工作时间组
    @Data
    public static class WorkTimeGroup {
        private String weekYear;           // 周/年 (BCD格式)
        private String monthDay;           // 月/日 (BCD格式)
        private String hourMinute;         // 时/分 (BCD格式)

        // 构造函数
        public WorkTimeGroup() {}

        public WorkTimeGroup(String weekYear, String monthDay, String hourMinute) {
            this.weekYear = weekYear;
            this.monthDay = monthDay;
            this.hourMinute = hourMinute;
        }
    }
}