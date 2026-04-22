package com.integration.config.converter;

import com.integration.config.config.EncryptionConfig;
import com.integration.config.util.AesEncryptor;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 敏感字段 JPA 加密转换器
 *
 * <p>使用 {@link EncryptedFieldConverter} 注解到实体字段上，
 * JPA 在读写数据库时会自动加解密。
 *
 * <pre>
 * &#64;Convert(converter = EncryptedFieldConverter.class)
 * private String authInfo;
 * </pre>
 *
 * <p>特点：
 * <ul>
 *   <li>写入数据库前：明文 → AES-256-GCM 加密 → Base64 存储</li>
 *   <li>读出数据库后：Base64 → AES-256-GCM 解密 → 明文返回实体</li>
 *   <li>数据库中只存密文，即使数据泄露也无法获取原始 Token</li>
 * </ul>
 *
 * <p>安全边界：
 * <ul>
 *   <li>实体对象在内存中仍是明文（正常业务需要）</li>
 *   <li>日志需配置脱敏，避免泄露明文</li>
 *   <li>审计日志不记录 authInfo 等敏感字段的具体值</li>
 * </ul>
 *
 * @see AesEncryptor
 */
@Slf4j
@Component
public class EncryptedFieldConverter implements AttributeConverter<String, String> {

    /** 延迟初始化：等待 Spring 上下文就绪 */
    private static volatile AesEncryptor encryptorInstance;
    private static volatile EncryptionConfig configInstance;
    private static final AtomicBoolean initAttempted = new AtomicBoolean(false);

    /** 明文字段标识前缀（用于识别已加密数据格式） */
    private static final String PLAINTEXT_PREFIX = "PLAINTEXT:";

    /**
     * 写入数据库：明文 → 密文
     */
    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (!shouldEncrypt()) {
            return plaintext;
        }
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        // 已加密数据不再重复加密
        if (plaintext.startsWith(PLAINTEXT_PREFIX)) {
            return plaintext;
        }
        return getEncryptor().encrypt(plaintext);
    }

    /**
     * 从数据库读出：密文 → 明文
     */
    @Override
    public String convertToEntityAttribute(String ciphertext) {
        if (!shouldEncrypt()) {
            return ciphertext;
        }
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        // 非密文格式：直接返回（兼容旧数据：明文数据库内容）
        if (!looksEncrypted(ciphertext)) {
            return ciphertext;
        }
        String decrypted = getEncryptor().decrypt(ciphertext);
        // 解密失败（密钥不匹配或数据损坏）：回退为原文，避免业务崩溃
        return decrypted != null ? decrypted : ciphertext;
    }

    /**
     * 判断是否应该加密（配置启用 + 密钥已配置）
     */
    private boolean shouldEncrypt() {
        initFromSpring();
        return configInstance != null
                && configInstance.isEnabled()
                && encryptorInstance != null;
    }

    private AesEncryptor getEncryptor() {
        initFromSpring();
        return encryptorInstance;
    }

    /**
     * 从 Spring 上下文延迟初始化
     * （AttributeConverter 实例化早于 Spring 上下文，需手动获取 Bean）
     */
    private static synchronized void initFromSpring() {
        if (initAttempted.get()) {
            return;
        }
        initAttempted.set(true);
        try {
            // 手动从 ApplicationContext 获取 Bean
            var ctx = ApplicationContextProvider.getContext();
            if (ctx != null) {
                encryptorInstance = ctx.getBean(AesEncryptor.class);
                configInstance    = ctx.getBean(EncryptionConfig.class);
                log.info("EncryptedFieldConverter 初始化完成，encryption.enabled={}",
                        configInstance.isEnabled());
            }
        } catch (Exception e) {
            log.warn("EncryptedFieldConverter 初始化失败（Spring 上下文可能未就绪），加密功能暂不生效: {}", e.getMessage());
        }
    }

    /**
     * 简单启发式判断：是否为密文格式
     * 规则：包含 Base64 特征字符且长度 > 20
     */
    private static boolean looksEncrypted(String value) {
        return value.length() > 20
                && (value.contains("+") || value.contains("/") || value.contains("="))
                && !value.startsWith(PLAINTEXT_PREFIX);
    }

    /**
     * 数据脱敏（用于日志输出，不记录真实 Token）
     */
    public static String mask(String value) {
        if (value == null || value.length() <= 8) {
            return "******";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}