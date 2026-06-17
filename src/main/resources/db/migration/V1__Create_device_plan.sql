CREATE TABLE device_plan (
  id int(11) NOT NULL AUTO_INCREMENT,
  user_id int(11) NOT NULL COMMENT '所属用户ID',
  device_id varchar(64) NOT NULL COMMENT '设备序列号',
  is_repeat tinyint(2) NOT NULL DEFAULT '0' COMMENT '是否重复 1:重复 0:单次',
  execute_date date DEFAULT NULL COMMENT '单次执行日期',
  interval_unit varchar(20) DEFAULT NULL COMMENT '重复单位 day/week/month',
  interval_value int(11) DEFAULT NULL COMMENT '重复间隔数值',
  execute_days varchar(64) DEFAULT NULL COMMENT '每周或每月的具体号数(逗号分隔)',
  execute_time varchar(10) NOT NULL COMMENT '执行时间HH:mm',
  ction_mode int(11) NOT NULL COMMENT '工作使能模式',
  status tinyint(2) NOT NULL DEFAULT '1' COMMENT '状态 1:启用 0:停用',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_device_id (device_id),
  KEY idx_status_time (status, execute_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备计划任务表';
