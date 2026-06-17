package com.zt.cleanbot.controller;

import com.zt.cleanbot.service.StatisticsSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 累计清扫数据统计同步控制器
 *
 * 提供手动触发同步的API接口
 * 定时任务：每5分钟自动执行一次（无需手动调用）
 */
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/statistics")
@Slf4j
public class StatisticsSyncController {

    @Autowired
    private StatisticsSyncService statisticsSyncService;

    /**
     * 手动触发同步
     *
     * POST /statistics/sync
     *
     * 响应示例：
     * {
     *   "success": true,
     *   "message": "同步完成",
     *   "lastSyncTime": "2026-02-03 14:30:00"
     * }
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> manualSync() {
        log.info("手动触发累计清扫数据同步");

        try {
            // 执行同步
            statisticsSyncService.syncStatistics();

            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "同步完成");
            response.put("lastSyncTime", statisticsSyncService.getLastSyncTime());

            log.info("手动同步完成");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("手动同步失败", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "同步失败: " + e.getMessage());
            response.put("lastSyncTime", statisticsSyncService.getLastSyncTime());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 查询上次同步时间
     *
     * GET /statistics/last-sync-time
     *
     * 响应示例：
     * {
     *   "lastSyncTime": "2026-02-03 14:30:00",
     *   "autoSyncInterval": "5分钟"
     * }
     */
    @GetMapping("/last-sync-time")
    public ResponseEntity<Map<String, Object>> getLastSyncTime() {
        Map<String, Object> response = new HashMap<>();
        response.put("lastSyncTime", statisticsSyncService.getLastSyncTime());
        response.put("autoSyncInterval", "5分钟");

        return ResponseEntity.ok(response);
    }
}
