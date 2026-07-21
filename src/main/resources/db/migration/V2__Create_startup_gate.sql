-- 启动门禁表 - 控制设备是否允许启动
-- 上位机启动时通过 MQTT retained 消息读取此状态
CREATE TABLE IF NOT EXISTS railcar_startup_gate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    serial_number VARCHAR(32) NOT NULL COMMENT '设备完整序列号，如 -T01250001',
    disabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否禁用启动：0-允许启动，1-禁用启动',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '操作者用户名',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_serial_number (serial_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='启动门禁表';
