-- ========================================
-- Cumulative Cleaning Statistics - MySQL 5.7 Compatible
-- ========================================

USE pvcleaning;

-- Add total_run_time column (unit: seconds)
-- Check if column exists first to avoid errors
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'pvcleaning'
    AND TABLE_NAME = 'vehicle'
    AND COLUMN_NAME = 'total_run_time');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE vehicle ADD COLUMN total_run_time DOUBLE DEFAULT 0 COMMENT ''Cumulative runtime (seconds)''',
    'SELECT ''Column total_run_time already exists''');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add total_mileage column (unit: km)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'pvcleaning'
    AND TABLE_NAME = 'vehicle'
    AND COLUMN_NAME = 'total_mileage');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE vehicle ADD COLUMN total_mileage DOUBLE DEFAULT 0 COMMENT ''Cumulative mileage (km)''',
    'SELECT ''Column total_mileage already exists''');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add total_area column (unit: m2)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'pvcleaning'
    AND TABLE_NAME = 'vehicle'
    AND COLUMN_NAME = 'total_area');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE vehicle ADD COLUMN total_area DOUBLE DEFAULT 0 COMMENT ''Cumulative cleaning area (m2)''',
    'SELECT ''Column total_area already exists''');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Verify the columns were added
SELECT
    COLUMN_NAME,
    DATA_TYPE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'pvcleaning'
  AND TABLE_NAME = 'vehicle'
  AND COLUMN_NAME IN ('total_run_time', 'total_mileage', 'total_area')
ORDER BY ORDINAL_POSITION;
