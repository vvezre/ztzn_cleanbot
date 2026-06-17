-- 用户设备绑定表
CREATE TABLE IF NOT EXISTS `user_device` (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` INT(11) NOT NULL COMMENT '用户ID',
  `company_code` VARCHAR(8) NOT NULL COMMENT '公司代号（8字节）',
  `product_type` VARCHAR(4) NOT NULL COMMENT '产品型号（4字节）',
  `product_id` VARCHAR(6) NOT NULL COMMENT '产品编号（6字节，非唯一）',
  `serial_number` VARCHAR(18) NOT NULL COMMENT '完整序列号（companyCode + productType + productId）',
  `device_name` VARCHAR(50) DEFAULT NULL COMMENT '设备名称（用户自定义）',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '绑定状态：active(激活), inactive(未激活), deleted(已删除)',
  `bind_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_serial_number` (`serial_number`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设备绑定表';

-- 插入测试数据（可选）
-- INSERT INTO `user_device` (`user_id`, `company_code`, `product_type`, `product_id`, `serial_number`, `device_name`, `status`)
-- VALUES
-- (1, 'ZTZN-PVC', '-D01', '250001', 'ZTZN-PVC-D01250001', 'D01干挂基本型-250001', 'active'),
-- (1, 'ZTZN-PVC', '-D01', '250002', 'ZTZN-PVC-D01250002', 'D01干挂基本型-250002', 'active'),
-- (2, 'ZTZN-PVC', '-D11', '250003', 'ZTZN-PVC-D11250003', 'D11干挂带扭-250003', 'active');
