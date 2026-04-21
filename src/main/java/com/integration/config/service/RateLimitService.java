package com.integration.config.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 频率限制服务
 * 基于 Redis 的滑动窗口计数器实现
 * 支持按接口（apiCode）、按用户维度限流
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "integration:ratelimit:";

    /**
     * 检查并消耗令牌（滑动窗口计数器）
     *
     * @param apiCode 接口编码
     * @param userId  用户ID（null 时仅按接口维度限流）
     * @param window  时间窗口（秒）
     * @param max     窗口内最大请求数
     * @return true=允许通过, false=超限拒绝
     */
    public boolean tryAcquire(String apiCode, Long userId, int window, int max) {
        if (window <= 0 || max <= 0) {
            return true;
        }

        String key = KEY_PREFIX + apiCode + ":" + (userId != null ? userId : "global");

        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            log.warn("[RateLimitService] Redis increment returned null, key: {}", key);
            return true;
        }

        // 第一次写入时设置过期时间
        if (count == 1) {
            redisTemplate.expire(key, window, TimeUnit.SECONDS);
        }

        if (count > max) {
            log.info("[RateLimitService] 限流触发: apiCode={}, userId={}, count={}, max={}/{}s",
                    apiCode, userId, count, max, window);
            return false;
        }

        return true;
    }

    /**
     * 获取当前窗口内的已用请求数
     */
    public int getCurrentCount(String apiCode, Long userId) {
        String key = KEY_PREFIX + apiCode + ":" + (userId != null ? userId : "global");
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Integer.parseInt(val) : 0;
    }

    /**
     * 重置限流计数器
     */
    public void reset(String apiCode, Long userId) {
        String key = KEY_PREFIX + apiCode + ":" + (userId != null ? userId : "global");
        redisTemplate.delete(key);
    }
}
