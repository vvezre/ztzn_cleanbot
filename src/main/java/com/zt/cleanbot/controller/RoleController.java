package com.zt.cleanbot.controller;
import com.zt.cleanbot.model.Role;
import com.zt.cleanbot.service.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/role")
@Slf4j
public class RoleController {

    @Autowired
    private RoleService roleService;

    @GetMapping("/list")
    public List<Role> getAllRoles() {
        log.info("查询所有角色");
        return roleService.getAllRoles();
    }

    @PostMapping("/add")
    public boolean addRole(@RequestBody Role role) {
        log.info("新增角色");
        return roleService.save(role);
    }

    @PutMapping("/update")
    public boolean updateRole(@RequestBody Role role) {
        log.info("更新角色");
        return roleService.updateById(role);
    }

    @DeleteMapping("/{id}")
    public boolean deleteRole(@PathVariable Integer id) {
        log.info("删除角色: {}", id);
        return roleService.removeById(id);
    }

    @GetMapping("/{id}")
    public Role getRoleById(@PathVariable Integer id) {
        log.info("根据ID查询角色: {}", id);
        return roleService.getById(id);
    }
}