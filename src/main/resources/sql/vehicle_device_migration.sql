-- ========================================
-- Vehicle表和设备表合并 - 数据库迁移脚本
-- ========================================

-- 1. 为vehicle表添加设备相关字段
ALTER TABLE `vehicle`
ADD COLUMN `company_code` VARCHAR(8) DEFAULT NULL COMMENT '公司代号（8字节）' AFTER `id`,
ADD COLUMN `product_type` VARCHAR(4) DEFAULT NULL COMMENT '产品型号（4字节）' AFTER `company_code`,
ADD COLUMN `product_id` VARCHAR(6) DEFAULT NULL COMMENT '产品编号（6字节，非唯一）' AFTER `product_type`,
ADD COLUMN `last_online_time` DATETIME DEFAULT NULL COMMENT '最后在线时间' AFTER `cleaning_width`,
ADD COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER `last_online_time`,
ADD COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`;

-- 2. 为vehicle表添加索引
-- 注意：product_id 不是唯一的，serialNumber（productType + productId）才是唯一标识
ALTER TABLE `vehicle`
ADD KEY `idx_product_id` (`product_id`),
ADD KEY `idx_company_code` (`company_code`),
ADD KEY `idx_product_type` (`product_type`),
ADD UNIQUE KEY `uk_serial_number` (`serial_number`);

-- 3. 删除旧的user_device表（如果存在）
DROP TABLE IF EXISTS `user_device`;

-- 4. 创建新的user_device表（使用vehicle_id）
CREATE TABLE `user_device` (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` INT(11) NOT NULL COMMENT '用户ID',
  `vehicle_id` INT(11) NOT NULL COMMENT '车辆/设备ID（关联vehicle表）',
  `bind_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  `unbind_time` DATETIME DEFAULT NULL COMMENT '解绑时间',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '绑定状态：active(激活), deleted(已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_vehicle_active` (`user_id`, `vehicle_id`, `status`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_vehicle_id` (`vehicle_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设备绑定表';

-- ========================================
-- 插入测试数据（可选）
-- ========================================

-- 插入测试车辆数据
INSERT INTO `vehicle` (`company_code`, `product_type`, `product_id`, `serial_number`, `name`, `model`, `status`)
VALUES
('ZTZN-PVC', '-D01', '250001', 'ZTZN-PVC-D01250001', 'D01干挂基本型-001', 'D01 干挂基本型', 'active'),
('ZTZN-PVC', '-D01', '250002', 'ZTZN-PVC-D01250002', 'D01干挂基本型-002', 'D01 干挂基本型', 'active'),
('ZTZN-PVC', '-D11', '250003', 'ZTZN-PVC-D11250003', 'D11干挂带扭-003', 'D11 干挂带扭', 'active'),
('ZTZN-PVC', '-D21', '250004', 'ZTZN-PVC-D21250004', 'D21干挂带跨-004', 'D21 干挂带跨', 'active'),
('ZTZN-PVC', '-T01', '250005', 'ZTZN-PVC-T01250005', 'T01履带基本型-005', 'T01 履带基本型', 'active')
ON DUPLICATE KEY UPDATE
  `company_code` = VALUES(`company_code`),
  `product_type` = VALUES(`product_type`),
  `product_id` = VALUES(`product_id`);

-- 插入测试绑定数据（用户ID=1是admin）
-- INSERT INTO `user_device` (`user_id`, `vehicle_id`, `status`)
-- SELECT 1, `id`, 'active' FROM `vehicle` WHERE `product_id` IN ('250001', '250002');

-- ========================================
-- 验证数据
-- ========================================

-- 查看vehicle表结构
-- DESC `vehicle`;

-- 查看user_device表结构
-- DESC `user_device`;

-- 查看测试数据
-- SELECT * FROM `vehicle`;
-- SELECT * FROM `user_device`;
