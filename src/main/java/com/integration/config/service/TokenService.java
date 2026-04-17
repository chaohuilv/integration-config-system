package com.integration.config.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * AccessToken 认证服务
 * 基于 Redis 存储 Token，支持 Bearer Token 认证模式
 */
@Service
@Slf4j
public class TokenService {

    private static final String TOKEN_PREFIX = "integration:token:";
    private static final long DEFAULT_EXPIRE_HOURS = 24;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TokenService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建 access_token
     *
     * @param userId      用户ID
     * @param userCode    用户编码
     * @param username    用户名
     * @param displayName 显示名
     * @param clientIp    客户端IP
     * @return access_token 字符串
     */
    public String createToken(Long userId, String userCode, String username, String displayName, String clientIp) {
        String token = UUID.randomUUID().toString().replace("-", "");

        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setUserId(userId);
        tokenInfo.setUserCode(userCode);
        tokenInfo.setUsername(username);
        tokenInfo.setDisplayName(displayName);
        tokenInfo.setClientIp(clientIp);
        tokenInfo.setCreatedAt(System.currentTimeMillis());

        try {
            String json = objectMapper.writeValueAsString(tokenInfo);
            redisTemplate.opsForValue().set(
                    TOKEN_PREFIX + token,
                    json,
                    DEFAULT_EXPIRE_HOURS,
                    TimeUnit.HOURS
            );
            log.info("Token created for user: {} ({}) from {}", userCode, username, clientIp);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize token info", e);
            throw new RuntimeException("Token creation failed");
        }

        return token;
    }

    /**
     * 验证 Token，返回用户信息
     *
     * @param token access_token
     * @return TokenInfo 如果有效，否则 null
     */
    public TokenInfo validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String json = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readValue(json, TokenInfo.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize token info for token: {}", token, e);
            return null;
        }
    }

    /**
     * 撤销 Token（登出时调用）
     *
     * @param token access_token
     */
    public void revokeToken(String token) {
        if (token != null && !token.isBlank()) {
            Boolean deleted = redisTemplate.delete(TOKEN_PREFIX + token);
            log.info("Token revoked: {}, deleted: {}", token, deleted);
        }
    }

    /**
     * 刷新 Token 过期时间（每次请求自动续期）
     *
     * @param token access_token
     */
    public void refreshToken(String token) {
        if (token != null && !token.isBlank()) {
            Boolean success = redisTemplate.expire(TOKEN_PREFIX + token, DEFAULT_EXPIRE_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(success)) {
                log.warn("Failed to refresh token: {}", token);
            }
        }
    }

    /**
     * Token 存储的用户信息
     */
    @Data
    public static class TokenInfo {
        private Long userId;
        private String userCode;
        private String username;
        private String displayName;
        private String clientIp;
        private long createdAt;
    }
}
