package com.integration.config.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 敏感字段加密配置
 *
 * <p>加密密钥必须为 32 字节（AES-256），建议通过环境变量注入：
 * <pre>
 *   INTEGRATION_ENCRYPTION_KEY=your-32-byte-base64-encoded-key
 * </pre>
 *
 * <p>生成密钥：
 * <pre>
 *   openssl rand -base64 32
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "integration.encryption")
@Data
public class EncryptionConfig {

    /**
     * 是否启用字段加密（开发环境可关闭，生产必须开启）
     */
    private boolean enabled = true;

    /**
     * AES-256 密钥（Base64 编码的 32 字节密钥）
     *
     * <p>生成方式：openssl rand -base64 32
     * <p>注入优先级：环境变量 INTEGRATION_ENCRYPTION_KEY > 配置文件 > 默认值
     * <p>⚠️ 默认值仅供开发使用，生产环境必须替换
     */
    private String key = "ZGV2LW9ubHktc2VjcmV0LWtleS1nZW5lcmF0ZWQ=";

    /**
     * GCM 认证标签长度（位），128 = 最强完整性校验
     */
    private int gcmTagLength = 128;
}
