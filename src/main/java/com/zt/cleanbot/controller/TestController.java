package com.zt.cleanbot.controller;

import com.zt.cleanbot.service.RedisService;
import com.zt.cleanbot.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
public class TestController {

    @Autowired
    private TestService testService;
    @Autowired
    private RedisService redisService;

    @GetMapping("/test")
    public int test() {
        testService.test();
        return 0;
    }

    @GetMapping("/redisAdd")
    public int redisAdd(@RequestParam String key,
                                        @RequestParam String value) {
        boolean result = redisService.setStringValue(key, value);
        return 1;
    }

    /**
     * 获取字符串值
     * http://localhost:9798/redisGet/hhh
     */
    @GetMapping("/redisGet/{key}")
    public Map<String, Object> getString(@PathVariable String key) {
        String value = redisService.getStringValue(key);
        Map<String, Object> response = new HashMap<>();
        response.put("success", value != null);
        response.put("message", value != null ? "获取成功" : "键不存在");
        response.put("data", value);
        return response;
    }



    /**
     * 删除键
     */
    @GetMapping("/redisDelete/{key}")
    public int delete(@PathVariable String key) {
        boolean result = redisService.delete(key);
        return 1;
    }

    /**
     * 检查键是否存在
     */
    @GetMapping("/exists/{key}")
    public Map<String, Object> exists(@PathVariable String key) {
        boolean exists = redisService.exists(key);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("exists", exists);
        response.put("message", exists ? "键存在" : "键不存在");
        return response;
    }
}
