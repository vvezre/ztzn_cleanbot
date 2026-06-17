package com.zt.cleanbot.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.UserMapper;
import com.zt.cleanbot.model.User;
import com.zt.cleanbot.service.UserService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public boolean save(User entity) {
        if (entity != null && entity.getUserId() == null) {
            entity.setUserId(getNextUserId());
        }
        return super.save(entity);
    }

    @Override
    public List<User> getAllUsers() {
        return this.list();
    }

    @Override
    public User findByUsername(String username) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        return this.getOne(queryWrapper);
    }

    private synchronized Integer getNextUserId() {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("user_id");
        queryWrapper.orderByDesc("user_id");
        queryWrapper.last("LIMIT 1");

        User lastUser = this.getOne(queryWrapper, false);
        return lastUser == null || lastUser.getUserId() == null ? 1 : lastUser.getUserId() + 1;
    }
}
