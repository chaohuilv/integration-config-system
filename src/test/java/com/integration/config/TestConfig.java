package com.integration.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 测试配置类
 * 排除 Redis 等需要外部连接的组件
 */
@Configuration
@EnableAutoConfiguration(exclude = {
    RedisAutoConfiguration.class
})
public class TestConfig {
}
