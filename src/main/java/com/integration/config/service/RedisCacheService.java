package com.integration.config.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 缓存服务（替换本地 ConcurrentHashMap 缓存）
 * 支持多实例共享缓存
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedisCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${integration.cache.prefix:integration:cache:}")
    private String cachePrefix;

    @Value("${integration.cache.ttl:300}")
    private long defaultTtlSeconds;

    /**
     * 获取缓存
     */
    public Object get(String key) {
        try {
            String fullKey = cachePrefix + key;
            String value = redisTemplate.opsForValue().get(fullKey);
            if (value != null) {
                log.debug("Redis 缓存命中: {}", key);
                return objectMapper.readValue(value, Object.class);
            }
        } catch (Exception e) {
            log.warn("Redis 获取缓存失败: key={}, error={}", key, e.getMessage());
        }
        return null;
    }

    /**
     * 设置缓存
     */
    public void put(String key, Object value, int ttlSeconds) {
        try {
            String fullKey = cachePrefix + key;
            String jsonValue = objectMapper.writeValueAsString(value);
            Duration ttl = Duration.ofSeconds(ttlSeconds > 0 ? ttlSeconds : defaultTtlSeconds);
            redisTemplate.opsForValue().set(fullKey, jsonValue, ttl);
            log.debug("Redis 缓存写入: key={}, ttl={}s", key, ttl.getSeconds());
        } catch (Exception e) {
            log.warn("Redis 设置缓存失败: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        try {
            String fullKey = cachePrefix + key;
            redisTemplate.delete(fullKey);
            log.debug("Redis 缓存删除: key={}", key);
        } catch (Exception e) {
            log.warn("Redis 删除缓存失败: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 判断缓存是否存在
     */
    public boolean exists(String key) {
        try {
            String fullKey = cachePrefix + key;
            return Boolean.TRUE.equals(redisTemplate.hasKey(fullKey));
        } catch (Exception e) {
            log.warn("Redis 检查缓存失败: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 清除指定前缀的所有缓存（用于测试或清理）
     */
    public void clearByPrefix(String prefix) {
        try {
            String fullPattern = cachePrefix + prefix + "*";
            var keys = redisTemplate.keys(fullPattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Redis 缓存清理: pattern={}, count={}", fullPattern, keys.size());
            }
        } catch (Exception e) {
            log.warn("Redis 清理缓存失败: prefix={}, error={}", prefix, e.getMessage());
        }
    }
}