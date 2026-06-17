-- ========================================
-- 设备管理系统数据库表
-- ========================================

-- 1. 设备表（独立管理设备信息）
DROP TABLE IF EXISTS `device`;
CREATE TABLE `device` (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `company_code` VARCHAR(8) NOT NULL COMMENT '公司代号（8字节）',
  `product_type` VARCHAR(4) NOT NULL COMMENT '产品型号（4字节）',
  `product_id` VARCHAR(6) NOT NULL COMMENT '产品编号（6字节，非唯一）',
  `serial_number` VARCHAR(18) NOT NULL COMMENT '完整序列号（companyCode + productType + productId）',
  `device_name` VARCHAR(50) DEFAULT NULL COMMENT '设备名称',
  `device_model` VARCHAR(100) DEFAULT NULL COMMENT '设备型号详细信息',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '设备状态：active(可用), inactive(停用), maintenance(维护中)',
  `battery_capacity` INT(11) DEFAULT NULL COMMENT '电池容量（mAh）',
  `last_online_time` DATETIME DEFAULT NULL COMMENT '最后在线时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  UNIQUE KEY `uk_serial_number` (`serial_number`),
  KEY `idx_company_code` (`company_code`),
  KEY `idx_product_type` (`product_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表';

-- 2. 用户设备绑定表（关联用户和设备）
DROP TABLE IF EXISTS `user_device`;
CREATE TABLE `user_device` (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` INT(11) NOT NULL COMMENT '用户ID',
  `device_id` INT(11) NOT NULL COMMENT '设备ID',
  `bind_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  `unbind_time` DATETIME DEFAULT NULL COMMENT '解绑时间',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '绑定状态：active(激活), deleted(已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_device_active` (`user_id`, `device_id`, `status`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设备绑定表';

-- ========================================
-- 测试数据（可选）
-- ========================================

-- 插入测试设备数据
INSERT INTO `device` (`company_code`, `product_type`, `product_id`, `serial_number`, `device_name`, `device_model`, `status`)
VALUES
('ZTZN-PVC', '-D01', '250001', 'ZTZN-PVC-D01250001', 'D01干挂基本型-001', 'D01 干挂基本型', 'active'),
('ZTZN-PVC', '-D01', '250002', 'ZTZN-PVC-D01250002', 'D01干挂基本型-002', 'D01 干挂基本型', 'active'),
('ZTZN-PVC', '-D11', '250003', 'ZTZN-PVC-D11250003', 'D11干挂带扭-003', 'D11 干挂带扭', 'active'),
('ZTZN-PVC', '-D21', '250004', 'ZTZN-PVC-D21250004', 'D21干挂带跨-004', 'D21 干挂带跨', 'active'),
('ZTZN-PVC', '-T01', '250005', 'ZTZN-PVC-T01250005', 'T01履带基本型-005', 'T01 履带基本型', 'active');

-- 插入测试绑定数据（用户ID=1是admin）
-- INSERT INTO `user_device` (`user_id`, `device_id`, `status`)
-- VALUES
-- (1, 1, 'active'),
-- (1, 2, 'active');
