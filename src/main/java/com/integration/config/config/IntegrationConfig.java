package com.integration.config.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * 全局配置
 */
@Configuration
@ConfigurationProperties(prefix = "integration")
@Data
public class IntegrationConfig {

    /**
     * HTTP 连接超时（毫秒）
     */
    private int httpConnectTimeout = 10000;

    /**
     * HTTP 读取超时（毫秒）
     */
    private int httpReadTimeout = 30000;

    /**
     * 是否记录请求日志
     */
    private boolean logRequest = true;

    /**
     * 是否记录响应日志
     */
    private boolean logResponse = true;

    /**
     * 最大重试次数
     */
    private int maxRetry = 3;

    // ========== 环境配置功能 ==========

    /**
     * 是否启用环境配置功能
     */
    private boolean environmentEnabled = true;
}
