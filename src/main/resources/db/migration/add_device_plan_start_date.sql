-- 为 device_plan 表新增 start_date 字段
-- 用于锁定重复任务的周期基准日，避免因计划删除重建导致 create_time 变化引起触发时间漂移
ALTER TABLE device_plan
    ADD COLUMN start_date date DEFAULT NULL COMMENT '重复任务周期基准日（首次执行日），用于计算重复周期' AFTER execute_date;
