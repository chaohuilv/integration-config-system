package com.integration.config.util;

import com.integration.config.config.EncryptionConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密工具
 *
 * <p>特点：
 * <ul>
 *   <li>AES-256：256 位密钥，军事级安全</li>
 *   <li>GCM 模式：带认证标签，防篡改（非对称加密泄露无法被修改）</li>
 *   <li>每次加密随机 IV/GCM Nonce，无需静态向量</li>
 *   <li>密文格式：Base64(IV + 加密数据 + AuthTag)</li>
 * </ul>
 *
 * <p>数据格式：
 * <pre>
 *   Base64(12字节IV || 密文 || 16字节认证标签)
 * </pre>
 */
@Slf4j
@Component
public class AesEncryptor {

    /**
     * 加密数据格式标识前缀
     * <p>加密输出统一添加此前缀，解密时据此识别是否为密文，
     * 避免依赖启发式字符判断导致密文被当明文返回。
     * <p>格式：ENC:aes_gcm:<Base64密文>
     */
    public static final String ENC_PREFIX = "ENC:aes_gcm:";

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;        // GCM 推荐 IV 长度
    private static final int GCM_TAG_LENGTH = 128;      // 认证标签 128 位
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EncryptionConfig config;
    private final SecretKey secretKey;
    private final byte[] ivBuffer = new byte[GCM_IV_LENGTH];

    public AesEncryptor(EncryptionConfig config) {
        this.config = config;
        this.secretKey = decodeKey(config.getKey());
    }

    /**
     * 解密字段值
     *
     * @param encryptedValue 数据库中的密文（Base64 编码）
     * @return 解密后的原文（原文为空时返回 null）
     */
    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return null;
        }
        // 兼容无前缀的旧密文（向后兼容）
        String base64Payload = encryptedValue;
        if (encryptedValue.startsWith(ENC_PREFIX)) {
            base64Payload = encryptedValue.substring(ENC_PREFIX.length());
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Payload);
            // 格式：IV(12) + 密文 + 认证标签
            System.arraycopy(decoded, 0, ivBuffer, 0, GCM_IV_LENGTH);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, ivBuffer);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            // 认证标签紧跟在密文之后
            byte[] ciphertext = new byte[decoded.length - GCM_IV_LENGTH];
            System.arraycopy(decoded, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("AES 解密失败，已返回 null。数据可能未被加密或密钥错误: {}",
                    e.getMessage());
            return null;
        }
    }

    /**
     * 加密字段值
     *
     * @param plaintext 明文
     * @return Base64 编码的密文（原文为空时返回 null）
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        try {
            // 生成随机 IV（每次加密都用新 IV，保证即使相同原文密文也不同）
            RANDOM.nextBytes(ivBuffer);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, ivBuffer);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 拼接：IV + 密文（含认证标签，由 GCM 模式自动追加）
            byte[] combined = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(ivBuffer, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, GCM_IV_LENGTH, ciphertext.length);

            return ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("AES 加密失败，字段将保持明文。错误: {}", e.getMessage(), e);
            return plaintext;
        }
    }

    private SecretKey decodeKey(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 生成新的 32 字节 AES 密钥（Base64 格式）
     * 用于首次部署或轮换密钥
     *
     * <pre>
     *   # 控制台执行
     *   openssl rand -base64 32
     * </pre>
     */
    public static String generateKey() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * 生成新密钥（Main 方法）
     */
    public static void main(String[] args) {
        System.out.println("生成的 AES-256 密钥：");
        System.out.println(generateKey());
    }
}