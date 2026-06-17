package com.zt.cleanbot.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zt.cleanbot.model.ProvinceBoundary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ProvinceBoundaryMapper extends BaseMapper<ProvinceBoundary> {

    /**
     * 根据经纬度查询所属省份（使用空间查询）
     */
    @Select("SELECT pb.* FROM province_boundary pb " +
            "WHERE ST_Within(ST_GeomFromText(CONCAT('POINT(', #{longitude}, ' ', #{latitude}, ')')), pb.boundary_polygon) " +
            "LIMIT 1")
    ProvinceBoundary findProvinceByCoordinate(@Param("longitude") BigDecimal longitude,
                                              @Param("latitude") BigDecimal latitude);

    /**
     * 根据省份名称查询
     */
    @Select("SELECT * FROM province_boundary WHERE province_name = #{provinceName}")
    ProvinceBoundary findByProvinceName(@Param("provinceName") String provinceName);

    /**
     * 获取所有省份列表
     */
    @Select("SELECT province_name FROM province_boundary ORDER BY province_name")
    List<String> findAllProvinceNames();
}