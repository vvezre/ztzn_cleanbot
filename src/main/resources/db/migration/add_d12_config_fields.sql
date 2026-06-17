-- 为 railcar_config 表新增 D12 接驳车专属参数列
-- 执行前请确认已备份 railcar_config 表

ALTER TABLE railcar_config
    ADD COLUMN robot_in_position_time    INT NULL COMMENT 'D12-机器人在位判断时间(BCD, 单位ms)',
    ADD COLUMN limit_position_check_time INT NULL COMMENT 'D12-前后极限位检时间(BCD, 单位ms)',
    ADD COLUMN walk_position_check_time  INT NULL COMMENT 'D12-前后行走到位检时间(BCD, 单位ms)',
    ADD COLUMN walk_fast_speed           INT NULL COMMENT 'D12-前后行走快速度(BCD)',
    ADD COLUMN walk_slow_speed           INT NULL COMMENT 'D12-前后行走慢速度(BCD)',
    ADD COLUMN max_row_count             INT NULL COMMENT 'D12-工作区域最大排数量(BCD)';
