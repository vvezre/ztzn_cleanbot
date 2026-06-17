package com.zt.cleanbot.dao;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zt.cleanbot.model.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}