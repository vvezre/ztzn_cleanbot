package com.zt.cleanbot.dao;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zt.cleanbot.model.Site;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SiteMapper extends BaseMapper<Site> {
}