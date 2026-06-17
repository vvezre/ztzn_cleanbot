package com.zt.cleanbot.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.Role;
import java.util.List;

public interface RoleService extends IService<Role> {
    List<Role> getAllRoles();
}