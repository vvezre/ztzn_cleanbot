package com.zt.cleanbot.service;

/**
 * 累计清扫数据统计同步服务
 *
 * 功能：
 * 1. 定时从Redis同步设备累计数据到MySQL
 * 2. 自动计算累计清扫面积：Area = Mileage * 1000 * Width
 * 3. 更新vehicle表的累计统计字段
 */
public interface StatisticsSyncService {

    /**
     * 手动触发同步（用于测试）
     */
    void syncStatistics();

    /**
     * 获取上次同步时间
     */
    String getLastSyncTime();
}
