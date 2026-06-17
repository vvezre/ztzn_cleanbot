USE pvcleaning;

-- Method: Ignore error if column exists
-- MySQL 5.7 will show error if column exists, but will continue

ALTER TABLE vehicle ADD COLUMN total_run_time DOUBLE DEFAULT 0;
ALTER TABLE vehicle ADD COLUMN total_mileage DOUBLE DEFAULT 0;
ALTER TABLE vehicle ADD COLUMN total_area DOUBLE DEFAULT 0;

-- Show result
DESCRIBE vehicle;
