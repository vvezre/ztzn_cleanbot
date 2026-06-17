package com.zt.cleanbot.controller;

import com.zt.cleanbot.common.Result;
import com.zt.cleanbot.dto.LoginRequest;
import com.zt.cleanbot.dto.LoginResponse;
import com.zt.cleanbot.model.Role;
import com.zt.cleanbot.model.User;
import com.zt.cleanbot.service.RoleService;
import com.zt.cleanbot.service.UserService;
import com.zt.cleanbot.util.JwtUtil;
import com.zt.cleanbot.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = { "*" }, maxAge = 3600L)
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 普通用户注册
     * 注册账号一律为 roleId=3（任务操作员）：可扫码、可用快捷按钮、可用定时任务，不能下发参数
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody LoginRequest request) {
        // 1. 校验输入
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            return Result.error(400, "用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return Result.error(400, "密码不能为空");
        }
        if (request.getUsername().trim().length() < 2 || request.getUsername().trim().length() > 20) {
            return Result.error(400, "用户名长度需在2-20个字符之间");
        }
        if (request.getPassword().length() < 4 || request.getPassword().length() > 32) {
            return Result.error(400, "密码长度需在4-32个字符之间");
        }

        // 2. 检查用户名是否已存在
        String username = request.getUsername().trim();
        User existingUser = userService.findByUsername(username);
        if (existingUser != null) {
            return Result.error(409, "用户名已存在");
        }

        // 3. 创建用户（roleId=3 任务操作员，普通账户）
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(request.getPassword()); // 明文存储（与当前系统一致）
        newUser.setRoleId(3); // 任务操作员：可扫码、快捷按钮、定时任务，不能下发参数
        newUser.setRealName(username); // 默认真实姓名与用户名相同
        newUser.setStatus("able");
        userService.save(newUser);

        // 4. 注册成功后自动登录，生成 Token
        Role role = roleService.getById(3);
        List<String> permissions;
        try {
            String permissionsJson = role.getPermissions();
            if (permissionsJson == null || permissionsJson.trim().isEmpty()) {
                permissions = java.util.Collections.emptyList();
            } else {
                permissions = objectMapper.readValue(
                        permissionsJson,
                        new TypeReference<List<String>>() {
                        });
            }
        } catch (Exception e) {
            return Result.error(500, "权限配置错误: " + e.getMessage());
        }

        String accessToken = jwtUtil.generateToken(
                newUser.getUserId(),
                newUser.getUsername(),
                newUser.getRoleId(),
                role.getName(),
                permissions);

        String refreshToken = jwtUtil.generateRefreshToken(
                newUser.getUserId(),
                newUser.getUsername());

        redisUtil.set("refresh_token:" + newUser.getUserId(), refreshToken, 604800);

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(3600L);

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setUserId(newUser.getUserId());
        userInfo.setUsername(newUser.getUsername());
        userInfo.setRealName(newUser.getRealName());
        userInfo.setRoleId(newUser.getRoleId());
        userInfo.setRoleName(role.getName());
        response.setUser(userInfo);

        System.out.println("Register & Auto-Login Success. User: " + username);
        return Result.success(response);
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        // 1. 查找用户
        User user = userService.findByUsername(request.getUsername());
        if (user == null) {
            return Result.error(401, "用户名或密码错误");
        }

        // 2. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }

        // 3. 检查用户状态
        if ("disable".equals(user.getStatus())) {
            return Result.error(403, "账户已被禁用");
        }

        // 4. 获取角色和权限
        Role role = roleService.getById(user.getRoleId());
        if (role == null) {
            return Result.error(500, "用户角色配置错误");
        }

        // 5. 解析权限（从 JSON 字符串）
        List<String> permissions;
        try {
            String permissionsJson = role.getPermissions();
            if (permissionsJson == null || permissionsJson.trim().isEmpty()) {
                permissions = java.util.Collections.emptyList();
            } else {
                permissions = objectMapper.readValue(
                        permissionsJson,
                        new TypeReference<List<String>>() {
                        });
            }
        } catch (Exception e) {
            return Result.error(500, "权限配置错误: " + e.getMessage());
        }

        // 6. 生成 Token
        String accessToken = jwtUtil.generateToken(
                user.getUserId(),
                user.getUsername(),
                user.getRoleId(),
                role.getName(),
                permissions);

        String refreshToken = jwtUtil.generateRefreshToken(
                user.getUserId(),
                user.getUsername());

        // 7. 存储 Refresh Token 到 Redis (7天过期)
        redisUtil.set("refresh_token:" + user.getUserId(), refreshToken, 604800);

        // 8. 构建响应
        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(3600L); // 1小时

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setUserId(user.getUserId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        userInfo.setRoleId(user.getRoleId());
        userInfo.setRoleName(role.getName());
        response.setUser(userInfo);

        System.out.println("Login Success. Generated AccessToken: " + accessToken);
        return Result.success(response);
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestHeader("Authorization") String refreshToken) {
        // 移除 "Bearer " 前缀
        if (refreshToken.startsWith("Bearer ")) {
            refreshToken = refreshToken.substring(7);
        }

        // 1. 验证 Refresh Token
        if (!jwtUtil.validateToken(refreshToken) || jwtUtil.isTokenExpired(refreshToken)) {
            return Result.error(401, "Refresh Token 无效或已过期");
        }

        // 2. 从 Token 中提取用户信息
        Integer userId = jwtUtil.getUserIdFromToken(refreshToken);
        String username = jwtUtil.getUsernameFromToken(refreshToken);

        // 3. 验证 Redis 中的 Refresh Token
        String storedToken = (String) redisUtil.get("refresh_token:" + userId);
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            return Result.error(401, "Refresh Token 已失效");
        }

        // 4. 查询用户和角色
        User user = userService.getById(userId);
        if (user == null || "disable".equals(user.getStatus())) {
            return Result.error(403, "用户不存在或已禁用");
        }

        Role role = roleService.getById(user.getRoleId());
        List<String> permissions;
        // 5. 生成新的 Access Token
        try {
            String permissionsJson = role.getPermissions();
            if (permissionsJson == null || permissionsJson.trim().isEmpty()) {
                permissions = java.util.Collections.emptyList();
            } else {
                permissions = objectMapper.readValue(
                        permissionsJson,
                        new TypeReference<List<String>>() {
                        });
            }
        } catch (Exception e) {
            return Result.error(500, "权限配置错误: " + e.getMessage());
        }

        // 5. 生成新的 Access Token
        String newAccessToken = jwtUtil.generateToken(
                user.getUserId(),
                user.getUsername(),
                user.getRoleId(),
                role.getName(),
                permissions);

        // 6. 构建响应
        LoginResponse response = new LoginResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(refreshToken); // 保持原 Refresh Token
        response.setExpiresIn(3600L);

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setUserId(user.getUserId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        userInfo.setRoleId(user.getRoleId());
        userInfo.setRoleName(role.getName());
        response.setUser(userInfo);

        return Result.success(response);
    }

    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String token) {
        // 从 Header 中提取 token
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 从 Token 中提取用户 ID
        Integer userId = jwtUtil.getUserIdFromToken(token);

        // 删除 Redis 中的 Refresh Token
        redisUtil.delete("refresh_token:" + userId);

        return Result.success("登出成功");
    }
}
