package com.zt.cleanbot.service;

import com.zt.cleanbot.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 简单的字符串操作示例
     */
    public boolean setStringValue(String key, String value) {
        return redisUtil.set(key, value);
    }

    public boolean setStringValueWithExpire(String key, String value, long timeout) {
        return redisUtil.set(key, value, timeout, TimeUnit.SECONDS);
    }

    public String getStringValue(String key) {
        return redisUtil.getString(key);
    }

    /**
     * 对象操作示例 - 可以存储任何对象
     */
    public boolean setObject(String key, Object value) {
        return redisUtil.set(key, value);
    }

    public boolean setObjectWithExpire(String key, Object value, long timeout) {
        return redisUtil.set(key, value, timeout, TimeUnit.SECONDS);
    }

    public Object getObject(String key) {
        return redisUtil.get(key);
    }

    /**
     * 删除操作
     */
    public boolean delete(String key) {
        return redisUtil.delete(key);
    }

    /**
     * 检查key是否存在
     */
    public boolean exists(String key) {
        return redisUtil.hasKey(key);
    }

    /**
     * 设置过期时间
     */
    public boolean setExpire(String key, long timeout) {
        return redisUtil.expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 哈希操作示例
     */
    public boolean setHashValue(String key, String field, Object value) {
        return redisUtil.hset(key, field, value);
    }

    public Object getHashValue(String key, String field) {
        return redisUtil.hget(key, field);
    }

    public boolean deleteHashField(String key, String field) {
        return redisUtil.hdelete(key, field);
    }
}
