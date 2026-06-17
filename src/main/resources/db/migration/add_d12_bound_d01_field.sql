-- D12 绑定 D01 列表字段
-- 说明:
-- 1. 仅 D12 设备使用该字段
-- 2. 保存完整序列号，逗号分隔，最多5台
-- 3. 示例: -D01250001,-D01250002

ALTER TABLE `vehicle`
ADD COLUMN `bound_d01_serials` varchar(255) DEFAULT NULL COMMENT 'D12绑定的D01序列号列表(逗号分隔,最多5台)' AFTER `vehicle_type`;
