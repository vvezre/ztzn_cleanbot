-- 新增 battery_low_limit 字段到 railcar_config 表
-- 电池低电量警戒线（2字节 BCD，如 50 = 5.0%）
ALTER TABLE `railcar_config`
    ADD COLUMN `battery_low_limit` INT(11) DEFAULT NULL COMMENT '电池低电量警戒线（BCD，如50=5.0%）'
    AFTER `backup`;
