package com.zt.cleanbot.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    String[] value();  // 需要的权限列表，满足其一即可
    boolean requireAll() default false;  // 是否需要全部权限
}
