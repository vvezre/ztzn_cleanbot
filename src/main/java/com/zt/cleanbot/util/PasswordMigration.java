package com.zt.cleanbot.util;

import com.zt.cleanbot.model.User;
import com.zt.cleanbot.service.UserService;
import com.zt.cleanbot.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 密码迁移工具
 * 首次启动时自动执行，将明文密码加密
 * 执行后请删除此类或注释掉 @Component 注解
 */
// @Component  // 临时禁用，排查自动停止问题
public class PasswordMigration implements CommandLineRunner {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public void run(String... args) throws Exception {
        // 检查是否已执行过迁移
        Object migrated = redisUtil.get("password_migration_completed");
        if (migrated != null && (Boolean) migrated) {
            System.out.println("密码迁移已完成，跳过...");
            return;
        }

        System.out.println("开始密码迁移...");

        List<User> users = userService.list();
        int count = 0;

        for (User user : users) {
            String rawPassword = user.getPassword();

            // 检查是否已经是加密密码（BCrypt 加密后以 $2a$ 或 $2b$ 开头）
            if (rawPassword.startsWith("$2a$") || rawPassword.startsWith("$2b$")) {
                System.out.println("用户 " + user.getUsername() + " 密码已加密，跳过");
                continue;
            }

            // 加密密码
            String encodedPassword = passwordEncoder.encode(rawPassword);
            user.setPassword(encodedPassword);

            // 更新数据库
            userService.updateById(user);

            count++;
            System.out.println("已迁移用户: " + user.getUsername() + " (原密码: " + rawPassword + ")");
        }

        // 标记迁移完成
        redisUtil.set("password_migration_completed", true, 365, TimeUnit.DAYS);

        System.out.println("密码迁移完成！共迁移 " + count + " 个用户");
        System.out.println("请删除 PasswordMigration.java 或注释掉 @Component 注解");
    }
}
