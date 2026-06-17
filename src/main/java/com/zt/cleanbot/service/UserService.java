package com.zt.cleanbot.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.User;
import java.util.List;

public interface UserService extends IService<User> {
    List<User> getAllUsers();
    User findByUsername(String username);
}