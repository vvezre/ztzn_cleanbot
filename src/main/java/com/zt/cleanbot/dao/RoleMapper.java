package com.zt.cleanbot.dao;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zt.cleanbot.model.Role;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}