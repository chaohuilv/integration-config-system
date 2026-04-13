package com.integration.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 统一接口配置系统 - 主启动类
 *
 * 功能：
 * 1. 集中管理所有外部接口配置
 * 2. 提供 Web UI 配置界面
 * 3. 支持接口调试与响应缓存
 * 4. 统一日志与错误处理
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableAsync
@EnableScheduling
public class IntegrationConfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationConfigApplication.class, args);
    }
}
