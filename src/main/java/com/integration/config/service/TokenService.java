package com.integration.config.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.integration.config.enums.AppConstants;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * AccessToken 璁よ瘉鏈嶅姟
 * 鍩轰簬 Redis 瀛樺偍 Token锛屾敮鎸?Bearer Token 璁よ瘉妯″紡
 */
@Service
@Slf4j
public class TokenService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TokenService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 鍒涘缓 access_token
     *
     * @param userId      鐢ㄦ埛ID
     * @param userCode    鐢ㄦ埛缂栫爜
     * @param username    鐢ㄦ埛鍚?     * @param displayName 鏄剧ず鍚?     * @param clientIp    瀹㈡埛绔疘P
     * @return access_token 瀛楃涓?     */
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
                    AppConstants.REDIS_TOKEN_PREFIX + token,
                    json,
                    AppConstants.TOKEN_DEFAULT_EXPIRE_HOURS,
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
     * 楠岃瘉 Token锛岃繑鍥炵敤鎴蜂俊鎭?     *
     * @param token access_token
     * @return TokenInfo 濡傛灉鏈夋晥锛屽惁鍒?null
     */
    public TokenInfo validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String json = redisTemplate.opsForValue().get(AppConstants.REDIS_TOKEN_PREFIX + token);
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
     * 鎾ら攢 Token锛堢櫥鍑烘椂璋冪敤锛?     *
     * @param token access_token
     */
    public void revokeToken(String token) {
        if (token != null && !token.isBlank()) {
            Boolean deleted = redisTemplate.delete(AppConstants.REDIS_TOKEN_PREFIX + token);
            log.info("Token revoked: {}, deleted: {}", token, deleted);
        }
    }

    /**
     * 鍒锋柊 Token 杩囨湡鏃堕棿锛堟瘡娆¤姹傝嚜鍔ㄧ画鏈燂級
     *
     * @param token access_token
     */
    public void refreshToken(String token) {
        if (token != null && !token.isBlank()) {
            Boolean success = redisTemplate.expire(AppConstants.REDIS_TOKEN_PREFIX + token, AppConstants.TOKEN_DEFAULT_EXPIRE_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(success)) {
                log.warn("Failed to refresh token: {}", token);
            }
        }
    }

    /**
     * Token 瀛樺偍鐨勭敤鎴蜂俊鎭?     */
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
