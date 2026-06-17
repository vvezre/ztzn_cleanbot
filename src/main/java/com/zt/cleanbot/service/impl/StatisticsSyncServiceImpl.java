package com.zt.cleanbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dao.VehicleMapper;
import com.zt.cleanbot.dto.VehicleRedisData;
import com.zt.cleanbot.model.Vehicle;
import com.zt.cleanbot.service.StatisticsSyncService;
import com.zt.cleanbot.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Service
public class StatisticsSyncServiceImpl implements StatisticsSyncService {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsSyncServiceImpl.class);

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private VehicleMapper vehicleMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private LocalDateTime lastSyncTime;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Scheduled task - runs every 5 minutes
     */
    @Scheduled(cron = "0 */5 * * * ?")
    @Override
    public void syncStatistics() {
        long startTime = System.currentTimeMillis();
        logger.info("Starting statistics synchronization...");

        try {
            Set<String> keys = redisUtil.getDeviceKeysOnly();
            if (keys == null || keys.isEmpty()) {
                logger.warn("No device keys found in Redis");
                return;
            }

            logger.info("Found {} device keys in Redis", keys.size());

            int successCount = 0;
            int failCount = 0;
            int skipCount = 0;

            for (String key : keys) {
                try {
                    Object redisData = redisUtil.getVehicle(key);
                    if (redisData == null) {
                        logger.debug("Redis key {} has null value, skipping", key);
                        skipCount++;
                        continue;
                    }

                    VehicleRedisData vehicleRedisData = parseRedisData(redisData);
                    if (vehicleRedisData == null || vehicleRedisData.getDeviceId() == null) {
                        logger.warn("Failed to parse Redis data for key: {}, skipping", key);
                        skipCount++;
                        continue;
                    }

                    Vehicle dbVehicle = vehicleMapper.selectById(key);
                    if (dbVehicle == null) {
                        logger.debug("Vehicle not found in MySQL for key: {}, skipping", key);
                        skipCount++;
                        continue;
                    }

                    Double totalRunTime = vehicleRedisData.getRunTimeTotal();
                    Double totalMileage = vehicleRedisData.getMileageTotal();

                    if (totalRunTime == null && totalMileage == null) {
                        logger.debug("Device {} has no statistics data, skipping", key);
                        skipCount++;
                        continue;
                    }

                    Double totalArea = null;
                    if (totalMileage != null && dbVehicle.getCleaningWidth() != null) {
                        totalArea = totalMileage * 1000.0 * dbVehicle.getCleaningWidth();
                        logger.debug("Device {} area calculation: {} km * 1000 * {} m = {} m²",
                                key, totalMileage, dbVehicle.getCleaningWidth(), totalArea);
                    }

                    boolean updated = false;
                    Vehicle updateEntity = new Vehicle();
                    updateEntity.setId(dbVehicle.getId());
                    updateEntity.setUpdateTime(LocalDateTime.now());

                    if (totalRunTime != null) {
                        updateEntity.setTotalRunTime(totalRunTime);
                        updated = true;
                    }

                    if (totalMileage != null) {
                        updateEntity.setTotalMileage(totalMileage);
                        updated = true;
                    }

                    if (totalArea != null) {
                        updateEntity.setTotalArea(totalArea);
                        updated = true;
                    }

                    if (updated) {
                        vehicleMapper.updateById(updateEntity);
                        successCount++;
                        logger.debug("Successfully updated device {} statistics: runtime={}s, mileage={}km, area={}m²",
                                key, totalRunTime, totalMileage, totalArea);
                    } else {
                        skipCount++;
                    }

                } catch (Exception e) {
                    failCount++;
                    logger.error("Error processing device {}", key, e);
                }
            }

            lastSyncTime = LocalDateTime.now();
            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.info("Statistics synchronization completed: success={}, skipped={}, failed={}, elapsed={}ms",
                    successCount, skipCount, failCount, elapsedTime);

        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.error("Statistics synchronization failed after {}ms", elapsedTime, e);
        }
    }

    private VehicleRedisData parseRedisData(Object redisData) {
        try {
            if (redisData == null) {
                return null;
            }

            // Skip JWT tokens and other non-vehicle data
            if (redisData instanceof String) {
                String strData = (String) redisData;

                // Detect JWT token format (contains 2 dots)
                if (strData.contains(".") && strData.split("\\.").length >= 3) {
                    logger.debug("Skipping JWT token or similar data");
                    return null;
                }

                // Try to parse as JSON
                return objectMapper.readValue(strData, VehicleRedisData.class);
            } else if (redisData instanceof VehicleRedisData) {
                return (VehicleRedisData) redisData;
            } else {
                return objectMapper.convertValue(redisData, VehicleRedisData.class);
            }
        } catch (Exception e) {
            logger.debug("Failed to parse Redis data (might be non-vehicle data): {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String getLastSyncTime() {
        if (lastSyncTime == null) {
            return "Not yet synchronized";
        }
        return lastSyncTime.format(TIME_FORMATTER);
    }
}
