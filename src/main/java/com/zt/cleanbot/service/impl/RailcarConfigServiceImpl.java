package com.zt.cleanbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dao.RailcarConfigMapper;
import com.zt.cleanbot.dto.DeviceConfigRequest;
import com.zt.cleanbot.model.RailcarConfig;
import com.zt.cleanbot.model.RailcarControlMessage;
import com.zt.cleanbot.service.RailcarConfigService;
import com.zt.cleanbot.service.RailcarControlService;
import com.zt.cleanbot.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 轨道车配置服务实现
 */
@Slf4j
@Service
public class RailcarConfigServiceImpl extends ServiceImpl<RailcarConfigMapper, RailcarConfig>
        implements RailcarConfigService {

    private static final long CONFIG_CACHE_EXPIRE_SECONDS = 7 * 24 * 60 * 60;
    private static final int SETTING_FRAME_HEX_LENGTH = 140;
    private static final int INTERACTION_TAIL_START = 60;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RailcarControlService railcarControlService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateConfig(RailcarControlMessage controlMessage) {
        try {
            log.info("开始保存轨道车配置 - 产品型号: {}, 产品编号: {}",
                    controlMessage.getProductModel(), controlMessage.getProductNumber());

            LambdaQueryWrapper<RailcarConfig> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(RailcarConfig::getProductModel, controlMessage.getProductModel())
                    .eq(RailcarConfig::getProductNumber, controlMessage.getProductNumber());

            RailcarConfig existingConfig = this.getOne(queryWrapper);
            RailcarConfig config = convertToConfig(controlMessage);

            if (existingConfig != null) {
                config.setId(existingConfig.getId());
                boolean result = this.updateById(config);
                if (result) {
                    cacheConfigSnapshot(buildDeviceId(controlMessage.getProductModel(), controlMessage.getProductNumber()), config);
                    log.info("轨道车配置更新成功 - 产品型号: {}, 产品编号: {}",
                            controlMessage.getProductModel(), controlMessage.getProductNumber());
                    return true;
                }
            } else {
                boolean result = this.save(config);
                if (result) {
                    cacheConfigSnapshot(buildDeviceId(controlMessage.getProductModel(), controlMessage.getProductNumber()), config);
                    log.info("轨道车配置新增成功 - 产品型号: {}, 产品编号: {}",
                            controlMessage.getProductModel(), controlMessage.getProductNumber());
                    return true;
                }
            }

            log.error("轨道车配置保存失败 - 产品型号: {}, 产品编号: {}",
                    controlMessage.getProductModel(), controlMessage.getProductNumber());
            return false;

        } catch (Exception e) {
            log.error("保存轨道车配置异常 - 产品型号: {}, 产品编号: {}",
                    controlMessage.getProductModel(), controlMessage.getProductNumber(), e);
            throw new RuntimeException("保存配置失败", e);
        }
    }

    private RailcarConfig convertToConfig(RailcarControlMessage controlMessage) {
        RailcarConfig config = new RailcarConfig();

        config.setProductModel(controlMessage.getProductModel());
        config.setProductNumber(controlMessage.getProductNumber());
        config.setCompanyCode(controlMessage.getCompanyCode());
        config.setWorkMode(controlMessage.getWorkMode());
        config.setOperationMode(controlMessage.getOperationMode());
        config.setOperationEnable(controlMessage.getOperationEnable());
        config.setEdgeDetectionDelay(controlMessage.getEdgeDetectionDelay());
        config.setBridgeDetectionTime(controlMessage.getBridgeDetectionTime());
        config.setErrorReturnTime(controlMessage.getErrorReturnTime());
        config.setWalkingSpeed(controlMessage.getWalkingSpeed());
        config.setBrushSpeed(controlMessage.getBrushSpeed());
        config.setBridgeSpeed(controlMessage.getBridgeSpeed());
        config.setHeartbeatPulse(controlMessage.getHeartbeatPulse());
        config.setBackup(controlMessage.getBackup());

        if (controlMessage.getWorkTimeGroups() != null) {
            try {
                String workTimeGroupsJson = objectMapper.writeValueAsString(controlMessage.getWorkTimeGroups());
                config.setWorkTimeGroups(workTimeGroupsJson);
            } catch (JsonProcessingException e) {
                log.error("转换工作时间组为JSON失败", e);
                config.setWorkTimeGroups("[]");
            }
        } else {
            config.setWorkTimeGroups("[]");
        }

        return config;
    }

    @Override
    public RailcarConfig getConfig(String productModel, String productNumber) {
        String deviceId = buildDeviceId(productModel, productNumber);
        RailcarConfig cached = getCachedConfig(deviceId);
        if (cached != null) {
            return cached;
        }

        LambdaQueryWrapper<RailcarConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RailcarConfig::getProductModel, productModel)
                .eq(RailcarConfig::getProductNumber, productNumber);
        RailcarConfig config = this.getOne(queryWrapper);
        if (config != null) {
            cacheConfigSnapshot(deviceId, config);
        }
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateConfig(DeviceConfigRequest request) {
        try {
            String productModel = request.getModel() != null && !request.getModel().trim().isEmpty()
                    ? request.getModel().trim()
                    : (request.getDeviceId() != null && request.getDeviceId().length() >= 4
                            ? request.getDeviceId().substring(0, 4)
                            : "");
            String productNumber = request.getDeviceId() != null && request.getDeviceId().length() > 4
                    ? request.getDeviceId().substring(4)
                    : request.getDeviceId();

            log.info("开始保存轨道车配置(来自DeviceConfigRequest) - 产品型号: {}, 产品编号: {}", productModel, productNumber);

            LambdaQueryWrapper<RailcarConfig> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(RailcarConfig::getProductModel, productModel)
                    .eq(RailcarConfig::getProductNumber, productNumber);

            RailcarConfig existingConfig = this.getOne(queryWrapper);
            RailcarConfig config = convertToConfigFromRequest(request, productModel, productNumber);

            if (existingConfig != null) {
                config.setId(existingConfig.getId());
                boolean result = this.updateById(config);
                if (result) {
                    cacheConfigSnapshot(buildDeviceId(productModel, productNumber), config);
                    log.info("轨道车配置更新成功 - 产品型号: {}, 产品编号: {}", productModel, productNumber);
                    return true;
                }
            } else {
                boolean result = this.save(config);
                if (result) {
                    cacheConfigSnapshot(buildDeviceId(productModel, productNumber), config);
                    log.info("轨道车配置新增成功 - 产品型号: {}, 产品编号: {}", productModel, productNumber);
                    return true;
                }
            }
            log.error("轨道车配置保存失败 - 产品型号: {}, 产品编号: {}", productModel, productNumber);
            return false;
        } catch (Exception e) {
            log.error("保存轨道车配置异常 - 设备: {}", request.getDeviceId(), e);
            throw new RuntimeException("保存配置失败", e);
        }
    }

    private RailcarConfig convertToConfigFromRequest(DeviceConfigRequest request,
            String productModel, String productNumber) {
        RailcarConfig config = new RailcarConfig();
        config.setProductModel(productModel);
        config.setProductNumber(productNumber);
        config.setCompanyCode(request.getCompanyCode());
        config.setWorkMode(request.getWorkWay());
        config.setOperationMode(request.getControlMode());
        config.setOperationEnable(request.getEnableMode());
        config.setHeartbeatPulse(request.getHeartbeatSet());
        config.setBackup(request.getReserved());
        config.setBatteryLowLimit(request.getBatteryLowLimit());

        boolean isD12 = "-D12".equals(productModel) || "-T12".equals(productModel);

        if (isD12) {
            config.setRobotInPositionTime(request.getRobotInPositionTime());
            config.setLimitPositionCheckTime(request.getLimitPositionCheckTime());
            config.setWalkPositionCheckTime(request.getWalkPositionCheckTime());
            config.setWalkFastSpeed(request.getWalkFastSpeed());
            config.setWalkSlowSpeed(request.getWalkSlowSpeed());
            config.setMaxRowCount(request.getMaxRowCount());
        } else {
            config.setEdgeDetectionDelay(request.getEdgeDelay());
            config.setBridgeDetectionTime(request.getBridgeTime());
            config.setErrorReturnTime(request.getErrorReturnTime());
            config.setWalkingSpeed(request.getWalkSpeed());
            config.setBrushSpeed(request.getBrushSpeed());
            config.setBridgeSpeed(request.getBridgeSpeed());
        }

        java.util.List<RailcarControlMessage.WorkTimeGroup> timeGroups = new java.util.ArrayList<>();
        if (request.getTime1() != null && hasTimeValue(request.getTime1())) {
            timeGroups.add(new RailcarControlMessage.WorkTimeGroup(request.getTime1().getYearWeek(),
                    request.getTime1().getMonDay(), request.getTime1().getHrMin()));
        }
        if (request.getTime2() != null && hasTimeValue(request.getTime2())) {
            timeGroups.add(new RailcarControlMessage.WorkTimeGroup(request.getTime2().getYearWeek(),
                    request.getTime2().getMonDay(), request.getTime2().getHrMin()));
        }
        if (request.getTime3() != null && hasTimeValue(request.getTime3())) {
            timeGroups.add(new RailcarControlMessage.WorkTimeGroup(request.getTime3().getYearWeek(),
                    request.getTime3().getMonDay(), request.getTime3().getHrMin()));
        }
        if (request.getTime4() != null && hasTimeValue(request.getTime4())) {
            timeGroups.add(new RailcarControlMessage.WorkTimeGroup(request.getTime4().getYearWeek(),
                    request.getTime4().getMonDay(), request.getTime4().getHrMin()));
        }
        try {
            config.setWorkTimeGroups(objectMapper.writeValueAsString(timeGroups));
        } catch (JsonProcessingException e) {
            log.error("转换工作时间组为JSON失败", e);
            config.setWorkTimeGroups("[]");
        }
        return config;
    }

    private boolean hasTimeValue(DeviceConfigRequest.TimeGroup tg) {
        return (tg.getYearWeek() != null && !tg.getYearWeek().trim().isEmpty()) ||
                (tg.getMonDay() != null && !tg.getMonDay().trim().isEmpty()) ||
                (tg.getHrMin() != null && !tg.getHrMin().trim().isEmpty());
    }

    @Override
    public RailcarConfig getConfigByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 4) {
            return null;
        }
        String productModel = deviceId.substring(0, 4);
        String productNumber = deviceId.length() > 4 ? deviceId.substring(4) : deviceId;
        return getConfig(productModel, productNumber);
    }

    @Override
    public void cacheLatestConfig(DeviceConfigRequest request, String settingFrameHex) {
        if (request == null || request.getDeviceId() == null || request.getDeviceId().length() < 4) {
            return;
        }

        String productModel = request.getModel() != null && !request.getModel().trim().isEmpty()
                ? request.getModel().trim()
                : request.getDeviceId().substring(0, 4);
        String productNumber = request.getDeviceId().length() > 4
                ? request.getDeviceId().substring(4)
                : request.getDeviceId();
        String deviceId = buildDeviceId(productModel, productNumber);

        cacheConfigSnapshot(deviceId, convertToConfigFromRequest(request, productModel, productNumber));
        cacheSettingFrame(deviceId, settingFrameHex);
    }

    @Override
    public String getLatestSettingFrameTailByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 4) {
            return null;
        }

        String cachedSettingFrame = readCachedSettingFrame(deviceId);
        if (cachedSettingFrame != null) {
            return cachedSettingFrame.substring(INTERACTION_TAIL_START);
        }

        RailcarConfig config = getConfigByDeviceId(deviceId);
        if (config == null) {
            return null;
        }

        String rebuiltSettingFrameHex = rebuildSettingFrameHex(config);
        if (rebuiltSettingFrameHex == null || rebuiltSettingFrameHex.length() != SETTING_FRAME_HEX_LENGTH) {
            return null;
        }

        cacheSettingFrame(deviceId, rebuiltSettingFrameHex);
        return rebuiltSettingFrameHex.substring(INTERACTION_TAIL_START);
    }

    private RailcarConfig getCachedConfig(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return null;
        }

        Object cached = redisUtil.get(configSnapshotKey(deviceId));
        if (cached == null) {
            return null;
        }

        try {
            if (cached instanceof RailcarConfig) {
                return (RailcarConfig) cached;
            }
            if (cached instanceof String) {
                return objectMapper.readValue((String) cached, RailcarConfig.class);
            }
            return objectMapper.convertValue(cached, RailcarConfig.class);
        } catch (Exception e) {
            log.warn("读取轨道车配置缓存失败 - deviceId: {}", deviceId, e);
            return null;
        }
    }

    private void cacheConfigSnapshot(String deviceId, RailcarConfig config) {
        if (deviceId == null || config == null) {
            return;
        }
        try {
            redisUtil.set(configSnapshotKey(deviceId), objectMapper.writeValueAsString(config), CONFIG_CACHE_EXPIRE_SECONDS);
        } catch (Exception e) {
            log.warn("写入轨道车配置快照缓存失败 - deviceId: {}", deviceId, e);
        }
    }

    private void cacheSettingFrame(String deviceId, String settingFrameHex) {
        if (deviceId == null) {
            return;
        }
        String normalized = normalizeSettingFrameHex(settingFrameHex);
        if (normalized == null) {
            return;
        }
        redisUtil.set(settingFrameKey(deviceId), normalized, CONFIG_CACHE_EXPIRE_SECONDS);
    }

    private String readCachedSettingFrame(String deviceId) {
        String cached = redisUtil.getString(settingFrameKey(deviceId));
        return normalizeSettingFrameHex(cached);
    }

    private String normalizeSettingFrameHex(String hex) {
        if (hex == null) {
            return null;
        }
        String normalized = hex.trim().toUpperCase();
        if (normalized.length() != SETTING_FRAME_HEX_LENGTH) {
            return null;
        }
        return normalized.matches("[0-9A-F]+") ? normalized : null;
    }

    private String rebuildSettingFrameHex(RailcarConfig config) {
        DeviceConfigRequest request = buildRequestFromConfig(config);
        if (request == null) {
            return null;
        }
        return railcarControlService.encodeControlCommand(request);
    }

    private DeviceConfigRequest buildRequestFromConfig(RailcarConfig config) {
        if (config == null || config.getProductModel() == null || config.getProductNumber() == null) {
            return null;
        }

        DeviceConfigRequest request = new DeviceConfigRequest();
        request.setDeviceId(buildDeviceId(config.getProductModel(), config.getProductNumber()));
        request.setModel(config.getProductModel());
        request.setCompanyCode(config.getCompanyCode());
        request.setInfoCommandType(0x00);
        request.setWorkWay(config.getWorkMode());
        request.setControlMode(config.getOperationMode());
        request.setEnableMode(config.getOperationEnable());
        request.setHeartbeatSet(config.getHeartbeatPulse());
        request.setBatteryLowLimit(config.getBatteryLowLimit());
        request.setReserved(config.getBackup());
        request.setReserved2(0);

        boolean isD12 = "-D12".equals(config.getProductModel()) || "-T12".equals(config.getProductModel());
        if (isD12) {
            request.setRobotInPositionTime(config.getRobotInPositionTime());
            request.setLimitPositionCheckTime(config.getLimitPositionCheckTime());
            request.setWalkPositionCheckTime(config.getWalkPositionCheckTime());
            request.setWalkFastSpeed(config.getWalkFastSpeed());
            request.setWalkSlowSpeed(config.getWalkSlowSpeed());
            request.setMaxRowCount(config.getMaxRowCount());
        } else {
            request.setEdgeDelay(config.getEdgeDetectionDelay());
            request.setBridgeTime(config.getBridgeDetectionTime());
            request.setErrorReturnTime(config.getErrorReturnTime());
            request.setWalkSpeed(config.getWalkingSpeed());
            request.setBrushSpeed(config.getBrushSpeed());
            request.setBridgeSpeed(config.getBridgeSpeed());
        }
        return request;
    }

    private String buildDeviceId(String productModel, String productNumber) {
        if (productModel == null || productNumber == null) {
            return null;
        }
        return productModel + productNumber;
    }

    private String configSnapshotKey(String deviceId) {
        return "railcar:config:snapshot:" + deviceId;
    }

    private String settingFrameKey(String deviceId) {
        return "railcar:config:setting-frame:" + deviceId;
    }
}
