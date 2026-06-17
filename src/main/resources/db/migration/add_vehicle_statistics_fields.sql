-- ========================================
-- 累计清扫数据统计字段 - 数据库迁移脚本
-- ========================================
-- 功能：为 vehicle 表添加累计统计数据字段
-- 执行方式：mysql -u root -p pvcleaning < add_vehicle_statistics_fields.sql
-- ========================================

USE pvcleaning;

-- 检查并添加累计运行时间字段（单位：秒）
ALTER TABLE vehicle
ADD COLUMN IF NOT EXISTS total_run_time DOUBLE DEFAULT 0 COMMENT '累计运行时间(秒)';

-- 检查并添加累计运行里程字段（单位：公里）
ALTER TABLE vehicle
ADD COLUMN IF NOT EXISTS total_mileage DOUBLE DEFAULT 0 COMMENT '累计运行里程(km)';

-- 检查并添加累计清扫面积字段（单位：平方米）
ALTER TABLE vehicle
ADD COLUMN IF NOT EXISTS total_area DOUBLE DEFAULT 0 COMMENT '累计清扫面积(m²)';

-- 验证字段是否添加成功
SELECT
    COLUMN_NAME,
    DATA_TYPE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'pvcleaning'
  AND TABLE_NAME = 'vehicle'
  AND COLUMN_NAME IN ('total_run_time', 'total_mileage', 'total_area')
ORDER BY ORDINAL_POSITION;

-- ========================================
-- 注意事项：
-- 1. MySQL 5.7+ 支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS 语法
-- 2. 如果字段已存在，该语句不会报错
-- 3. 累计清扫面积计算公式：Area = Mileage * 1000 * Width
--    - Mileage: 里程 (km)
--    - Width: 清洁宽度 (m)
--    - Area: 面积 (m²)
-- 4. 数据同步由 StatisticsSyncService 定时任务处理（每5分钟）
-- ========================================
