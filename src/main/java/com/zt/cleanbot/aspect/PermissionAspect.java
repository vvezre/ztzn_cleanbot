package com.zt.cleanbot.aspect;

import com.zt.cleanbot.annotation.RequirePermission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Aspect
@Component
public class PermissionAspect {

    @Around("@annotation(com.zt.cleanbot.annotation.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 获取方法上的注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RequirePermission annotation = signature.getMethod().getAnnotation(RequirePermission.class);

        // 2. 获取需要的权限
        String[] requiredPermissions = annotation.value();
        boolean requireAll = annotation.requireAll();

        // 3. 获取当前用户的权限
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("未登录或登录已过期");
        }

        List<String> userPermissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // 4. 检查权限
        boolean hasPermission;
        if (requireAll) {
            // 需要所有权限
            hasPermission = userPermissions.containsAll(Arrays.asList(requiredPermissions));
        } else {
            // 满足任一权限即可
            hasPermission = Arrays.stream(requiredPermissions)
                    .anyMatch(userPermissions::contains);
        }

        if (!hasPermission) {
            throw new SecurityException("权限不足");
        }

        // 5. 权限验证通过，执行方法
        return joinPoint.proceed();
    }
}
