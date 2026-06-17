package com.zt.cleanbot.controller;
import com.zt.cleanbot.model.User;
import com.zt.cleanbot.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/list")
    public List<User> getAllUsers() {
        log.info("查询所有用户");
        return userService.getAllUsers();
    }

    @PostMapping("/add")
    public boolean addUser(@RequestBody User user) {
        log.info("新增用户");
        return userService.save(user);
    }

    @PutMapping("/update")
    public boolean updateUser(@RequestBody User user) {
        log.info("更新用户");
        return userService.updateById(user);
    }

    @DeleteMapping("/{id}")
    public boolean deleteUser(@PathVariable Integer id) {
        log.info("删除用户: {}", id);
        return userService.removeById(id);
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Integer id) {
        log.info("根据ID查询用户: {}", id);
        return userService.getById(id);
    }
}