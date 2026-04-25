package com.integration.config.converter;

import com.integration.config.config.EncryptionConfig;
import com.integration.config.util.AesEncryptor;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    /** 明文字段标识前缀（用于识别已加密数据格式） */
    private static final String PLAINTEXT_PREFIX = "PLAINTEXT:";

    /** 密文标识前缀（AesEncryptor.ENC_PREFIX 的副本，避免循环依赖） */
    private static final String ENC_PREFIX = "ENC:aes_gcm:";

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
        if (plaintext.startsWith(PLAINTEXT_PREFIX) || plaintext.startsWith(ENC_PREFIX)) {
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
        // 新格式：有前缀标识，直接解密
        if (ciphertext.startsWith(ENC_PREFIX)) {
            String decrypted = getEncryptor().decrypt(ciphertext);
            return decrypted != null ? decrypted : ciphertext;
        }
        // 旧格式：尝试启发式识别密文（向后兼容修复前写入的数据）
        if (looksEncrypted(ciphertext)) {
            String decrypted = getEncryptor().decrypt(ciphertext);
            return decrypted != null ? decrypted : ciphertext;
        }
        // 明文数据（兼容无加密的历史记录）
        return ciphertext;
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
     *
     * <p>JPA AttributeConverter 由 Hibernate 实例化，早于 Spring Bean 初始化完成。
     * 因此采用「重试直到成功」策略：每次 convert 调用时检查上下文是否已就绪，
     * 一旦拿到 Bean 就缓存到静态字段，后续不再查询上下文。
     */
    private static synchronized void initFromSpring() {
        // 已成功初始化，直接返回
        if (encryptorInstance != null) {
            return;
        }
        try {
            var ctx = ApplicationContextProvider.getContext();
            if (ctx != null) {
                encryptorInstance = ctx.getBean(AesEncryptor.class);
                configInstance    = ctx.getBean(EncryptionConfig.class);
                log.info("EncryptedFieldConverter 初始化完成，encryption.enabled={}",
                        configInstance.isEnabled());
            }
            // ctx == null → 上下文尚未就绪，下次 convert 调用时重试
        } catch (Exception e) {
            // 获取 Bean 失败（上下文不完整），下次重试
            log.trace("EncryptedFieldConverter 等待 Spring 上下文就绪: {}", e.getMessage());
        }
    }

    /**
     * 启发式判断：是否为旧格式密文（无前缀标识的 AES-GCM Base64）
     * 规则：长度 >= 40（12字节IV + 16字节Tag 最小 28字节 → Base64 ≥ 40字符）
     *       且符合 Base64 字符集（仅含 A-Za-z0-9+/=）
     */
    private static boolean looksEncrypted(String value) {
        if (value.length() < 40 || value.startsWith(PLAINTEXT_PREFIX)) {
            return false;
        }
        // 快速检查：如果不含 Base64 特征字符（+ / =）且长度恰好在普通文本范围内，不太可能是密文
        // 但仅靠 +/= 判断不可靠，改为检查是否全是 Base64 合法字符
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=')) {
                return false;
            }
        }
        return true;
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