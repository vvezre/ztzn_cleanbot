package com.zt.cleanbot.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.RoleMapper;
import com.zt.cleanbot.model.Role;
import com.zt.cleanbot.service.RoleService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

    @Override
    public List<Role> getAllRoles() {
        return this.list();
    }
}