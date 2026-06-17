-- ========================================
-- 设备数据修复脚本 v2
-- 统一 vehicle 表的 serial_number 格式
-- 新格式：productType + productId（如 -D01250001）
-- 与 MQTT topic / Redis key 一致
-- ========================================

-- =====================
-- 第一部分：修复已有正式设备的 serial_number 格式
-- 旧格式：ZTZN-PVC-D01250001 → 新格式：-D01250001
-- =====================

-- 修复 ID 28-33 等已有正式设备（company_code, product_type, product_id 都有值的记录）
UPDATE vehicle
SET serial_number = CONCAT(product_type, product_id)
WHERE company_code IS NOT NULL
  AND product_type IS NOT NULL
  AND product_id IS NOT NULL
  AND serial_number != CONCAT(product_type, product_id);

-- =====================
-- 第二部分：补全缺失的 vehicle_type
-- 根据 product_type 自动判断
-- =====================

-- -D 开头 → railcar（轨道车）
UPDATE vehicle
SET vehicle_type = 'railcar'
WHERE product_type LIKE '-D%'
  AND (vehicle_type IS NULL OR vehicle_type = '');

-- -T 开头 → tracklayer（履带车）
UPDATE vehicle
SET vehicle_type = 'tracklayer'
WHERE product_type LIKE '-T%'
  AND (vehicle_type IS NULL OR vehicle_type = '');

-- =====================
-- 第三部分：修复 name 格式
-- 格式：型号名称-编号后3位（如 D01干挂基本型-001）
-- =====================

UPDATE vehicle SET name = CONCAT('D01干挂基本型-', RIGHT(product_id, 3))
WHERE product_type = '-D01' AND product_id IS NOT NULL;

UPDATE vehicle SET name = CONCAT('D11干挂带扭-', RIGHT(product_id, 3))
WHERE product_type = '-D11' AND product_id IS NOT NULL;

UPDATE vehicle SET name = CONCAT('D12干挂接驳车-', RIGHT(product_id, 3))
WHERE product_type = '-D12' AND product_id IS NOT NULL;

UPDATE vehicle SET name = CONCAT('D21干挂带跨-', RIGHT(product_id, 3))
WHERE product_type = '-D21' AND product_id IS NOT NULL;

UPDATE vehicle SET name = CONCAT('T01履带基本型-', RIGHT(product_id, 3))
WHERE product_type = '-T01' AND product_id IS NOT NULL;

UPDATE vehicle SET name = CONCAT('T11履带无人值守-', RIGHT(product_id, 3))
WHERE product_type = '-T11' AND product_id IS NOT NULL;

UPDATE vehicle SET name = CONCAT('T12履带充电仓-', RIGHT(product_id, 3))
WHERE product_type = '-T12' AND product_id IS NOT NULL;

UPDATE vehicle SET name = CONCAT('T21履带操控看守-', RIGHT(product_id, 3))
WHERE product_type = '-T21' AND product_id IS NOT NULL;

-- =====================
-- 第四部分：修复 model 字段
-- =====================

UPDATE vehicle SET model = 'D01干挂基本型' WHERE product_type = '-D01';
UPDATE vehicle SET model = 'D11干挂带扭' WHERE product_type = '-D11';
UPDATE vehicle SET model = 'D12干挂接驳车' WHERE product_type = '-D12';
UPDATE vehicle SET model = 'D21干挂带跨' WHERE product_type = '-D21';
UPDATE vehicle SET model = 'T01履带基本型' WHERE product_type = '-T01';
UPDATE vehicle SET model = 'T11履带无人值守' WHERE product_type = '-T11';
UPDATE vehicle SET model = 'T12履带充电仓' WHERE product_type = '-T12';
UPDATE vehicle SET model = 'T21履带操控看守' WHERE product_type = '-T21';

-- =====================
-- 第五部分：索引调整
-- product_id 不是唯一的，serial_number（productType + productId）才是唯一标识
-- =====================

-- 删除 product_id 上的唯一索引（如果存在）
-- 注意：执行前请确认索引名称，可能因环境不同而不同
ALTER TABLE vehicle DROP INDEX uk_product_id;

-- 给 serial_number 添加唯一索引
ALTER TABLE vehicle ADD UNIQUE KEY `uk_serial_number` (`serial_number`);

-- product_id 改为普通索引（仍然需要查询）
ALTER TABLE vehicle ADD KEY `idx_product_id` (`product_id`);

-- =====================
-- 第六部分：验证结果
-- =====================

SELECT id, company_code, product_type, product_id, name, serial_number, model, status, vehicle_type
FROM vehicle
WHERE company_code IS NOT NULL
ORDER BY id;
