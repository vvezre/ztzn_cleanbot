package com.zt.cleanbot.service.impl;

import com.zt.cleanbot.service.TestService;
import org.springframework.stereotype.Service;

@Service  // 添加这个注解
public class TestServiceImpl implements TestService {


    @Override
    public int test() {
        System.out.println("你好");
        return 0;
    }
}
