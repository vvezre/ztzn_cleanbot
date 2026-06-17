package com.zt.cleanbot.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class RedisUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 注入车辆专用的RedisTemplate
    @Autowired
    @Qualifier("vehicleRedisTemplate")
    private RedisTemplate<String, Object> vehicleRedisTemplate;
    /**
     * 设置缓存
     */
    public boolean set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 设置缓存并指定过期时间
     */
    public boolean set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取缓存
     */
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }


    /**
     * 设置字符串值
     */
    public boolean set(String key, String value, long expireSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
//            log.error("Redis set error: {}", e.getMessage(), e);
            return false;
        }
    }

//    /**
//     * 获取字符串值
//     */
//    public String get(String key) {
//        try {
//            Object value = redisTemplate.opsForValue().get(key);
//            return value != null ? value.toString() : null;
//        } catch (Exception e) {
////            log.error("Redis get error: {}", e.getMessage(), e);
//            return null;
//        }
//    }



    /**
     * 获取字符串类型的缓存
     */
    public String getString(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 删除缓存
     */
    public boolean delete(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 判断key是否存在
     */
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 设置过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取过期时间
     */
    public Long getExpire(String key, TimeUnit unit) {
        try {
            return redisTemplate.getExpire(key, unit);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 哈希操作 - 设置字段值
     */
    public boolean hset(String key, String field, Object value) {
        try {
            redisTemplate.opsForHash().put(key, field, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 哈希操作 - 获取字段值
     */
    public Object hget(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    /**
     * 哈希操作 - 删除字段
     */
    public boolean hdelete(String key, String field) {
        try {
            redisTemplate.opsForHash().delete(key, field);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 列表操作 - 向左添加
     */
    public boolean lpush(String key, Object value) {
        try {
            redisTemplate.opsForList().leftPush(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 列表操作 - 向右添加
     */
    public boolean rpush(String key, Object value) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 列表操作 - 获取范围
     */
    public Object lrange(String key, long start, long end) {
        try {
            return redisTemplate.opsForList().range(key, start, end);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Set 操作 - 获取成员列表（字符串）
     */
    public Set<String> smembers(String key) {
        try {
            Set<Object> rawMembers = redisTemplate.opsForSet().members(key);
            if (rawMembers == null) {
                return new LinkedHashSet<>();
            }
            return rawMembers.stream()
                    .filter(item -> item != null)
                    .map(Object::toString)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (Exception e) {
            e.printStackTrace();
            return new LinkedHashSet<>();
        }
    }

    /**
     * 直接按原始字符串读取 value，兼容 cleaner 写入的非 JSON Redis 值
     */
    public String getRawString(String key) {
        try {
            return redisTemplate.execute((RedisCallback<String>) connection -> {
                byte[] raw = connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8));
                return raw == null ? null : new String(raw, StandardCharsets.UTF_8);
            });
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 直接按原始字符串读取 Set，兼容 cleaner 写入的非 JSON Redis 值
     */
    public Set<String> smembersRawString(String key) {
        try {
            return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
                Set<byte[]> rawMembers = connection.sMembers(key.getBytes(StandardCharsets.UTF_8));
                Set<String> members = new LinkedHashSet<>();
                if (rawMembers == null) {
                    return members;
                }
                for (byte[] raw : rawMembers) {
                    if (raw != null) {
                        members.add(new String(raw, StandardCharsets.UTF_8));
                    }
                }
                return members;
            });
        } catch (Exception e) {
            e.printStackTrace();
            return new LinkedHashSet<>();
        }
    }

    /**
     * 车辆专用 - 设置缓存（无前缀，直接使用deviceId作为key）
     */
    public Long sremoveRawString(String key, String member) {
        try {
            return redisTemplate.execute((RedisCallback<Long>) connection ->
                    connection.sRem(
                            key.getBytes(StandardCharsets.UTF_8),
                            member.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public boolean setVehicle(String key, Object value) {
        try {
            vehicleRedisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 车辆专用 - 设置缓存并指定过期时间
     */
    public boolean setVehicle(String key, Object value, long timeout, TimeUnit unit) {
        try {
            vehicleRedisTemplate.opsForValue().set(key, value, timeout, unit);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    /**
     * 车辆专用 - 获取缓存
     */
    public Object getVehicle(String key) {
        return key == null ? null : vehicleRedisTemplate.opsForValue().get(key);
    }

    /**
     * 车辆专用 - 删除缓存
     */
    public boolean deleteVehicle(String key) {
        try {
            return Boolean.TRUE.equals(vehicleRedisTemplate.delete(key));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 车辆专用 - 判断key是否存在
     */
    public boolean hasVehicleKey(String key) {
        try {
            return Boolean.TRUE.equals(vehicleRedisTemplate.hasKey(key));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 车辆专用 - 设置过期时间
     */
    public boolean expireVehicle(String key, long timeout, TimeUnit unit) {
        try {
            return Boolean.TRUE.equals(vehicleRedisTemplate.expire(key, timeout, unit));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }



    // 在RedisUtil类中确保有这个方法
    /**
     * 车辆专用 - 获取所有key
     */
    public Set<String> getVehicleKeys() {
        try {
            return vehicleRedisTemplate.keys("*");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取设备相关的keys（过滤掉token等非设备数据）
     */
    public Set<String> getDeviceKeysOnly() {
        try {
            Set<String> allKeys = vehicleRedisTemplate.keys("*");
            if (allKeys == null) {
                return null;
            }

            // Filter out non-device keys
            return allKeys.stream()
                    .filter(key -> !key.toLowerCase().contains("token"))
                    .filter(key -> !key.toLowerCase().contains("refresh"))
                    .filter(key -> !key.startsWith("spring:"))
                    .filter(key -> !key.startsWith("__"))
                    .collect(java.util.stream.Collectors.toSet());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


}
