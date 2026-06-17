package com.zt.cleanbot.service;

import com.zt.cleanbot.dao.ProvinceBoundaryMapper;
import com.zt.cleanbot.model.ProvinceBoundary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LocalGeoService {

    @Autowired
    private ProvinceBoundaryMapper provinceBoundaryMapper;

    /**
     * 根据经纬度获取省份信息（精确边界判断）
     */
    public String getProvinceByLocation(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            return "未知";
        }

        try {
            ProvinceBoundary provinceBoundary = provinceBoundaryMapper.findProvinceByCoordinate(
                    BigDecimal.valueOf(longitude),
                    BigDecimal.valueOf(latitude)
            );

            if (provinceBoundary != null) {
                return provinceBoundary.getProvinceName();
            } else {
                // 如果精确查询失败，尝试使用备用方案（矩形边界）
                return getProvinceByApproximate(longitude, latitude);
            }

        } catch (Exception e) {
            log.error("根据经纬度查询省份失败: longitude={}, latitude={}", longitude, latitude, e);
            return "未知";
        }
    }

    /**
     * 备用方案：使用矩形边界近似判断
     */
    private String getProvinceByApproximate(Double longitude, Double latitude) {
        try {
            // 这里可以调用之前的矩形边界查询逻辑
            // 或者使用其他备用方案
            log.warn("使用备用方案查询省份: longitude={}, latitude={}", longitude, latitude);
            return "未知";
        } catch (Exception e) {
            log.error("备用方案查询省份失败", e);
            return "未知";
        }
    }

    /**
     * 批量获取省份信息
     */
    public Map<String, String> getProvincesByLocations(Map<String, Double[]> locationMap) {
        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, Double[]> entry : locationMap.entrySet()) {
            Double[] location = entry.getValue();
            if (location.length == 2) {
                String province = getProvinceByLocation(location[0], location[1]);
                result.put(entry.getKey(), province);
            }
        }

        return result;
    }

    /**
     * 验证省份名称是否存在
     */
    public boolean isValidProvince(String provinceName) {
        if (provinceName == null || provinceName.trim().isEmpty()) {
            return false;
        }

        try {
            ProvinceBoundary provinceBoundary = provinceBoundaryMapper.findByProvinceName(provinceName.trim());
            return provinceBoundary != null;
        } catch (Exception e) {
            log.error("验证省份名称失败: {}", provinceName, e);
            return false;
        }
    }

    /**
     * 获取所有省份列表
     */
    public List<String> getAllProvinceNames() {
        return provinceBoundaryMapper.findAllProvinceNames();
    }
}