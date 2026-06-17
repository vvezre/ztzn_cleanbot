package com.zt.cleanbot.model;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProvinceBoundary {
    private Integer id;
    private String provinceCode;
    private String provinceName;
    private String boundaryText; // WKT格式的边界数据
    private BigDecimal centerLongitude;
    private BigDecimal centerLatitude;
}