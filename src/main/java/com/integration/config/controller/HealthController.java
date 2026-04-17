package com.integration.config.controller;

import com.integration.config.service.TokenService;
import com.integration.config.service.TokenService.TokenInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查 & Token 调试端点
 */
@RestController
@RequestMapping("/api/health")
@Slf4j
public class HealthController {

    private final RedisConnectionFactory redisConnectionFactory;
    private final ApplicationContext applicationContext;
    private final TokenService tokenService;

    public HealthController(RedisConnectionFactory redisConnectionFactory, 
                           ApplicationContext applicationContext,
                           TokenService tokenService) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.applicationContext = applicationContext;
        this.tokenService = tokenService;
    }

    @GetMapping("/session")
    public Map<String, Object> checkSession(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        // 1. 从 Authorization header 提取 Token
        String token = extractBearerToken(request);
        result.put("tokenProvided", token != null);
        result.put("tokenPrefix", token != null ? token.substring(0, Math.min(8, token.length())) + "..." : null);
        
        // 2. 验证 Token
        TokenInfo tokenInfo = tokenService.validateToken(token);
        result.put("tokenValid", tokenInfo != null);
        
        if (tokenInfo != null) {
            result.put("userId", tokenInfo.getUserId());
            result.put("userCode", tokenInfo.getUserCode());
            result.put("username", tokenInfo.getUsername());
            result.put("displayName", tokenInfo.getDisplayName());
            result.put("clientIp", tokenInfo.getClientIp());
        }
        
        // 3. 从 Request Attribute 读取（由 LoginFilter 设置）
        Long userIdAttr = (Long) request.getAttribute("userId");
        result.put("userIdFromRequestAttribute", userIdAttr);
        
        // 4. 测试 Redis 连接
        try {
            String ping = redisConnectionFactory.getConnection().ping();
            result.put("redisPing", ping);
            result.put("redisOk", true);
        } catch (Exception e) {
            result.put("redisOk", false);
            result.put("redisError", e.getMessage());
            log.error("Redis connection failed", e);
        }
        
        return result;
    }

    @GetMapping("/redis-keys")
    public Map<String, Object> redisKeys() {
        Map<String, Object> result = new HashMap<>();
        try {
            var conn = redisConnectionFactory.getConnection();
            var keys = conn.keyCommands().keys("*".getBytes());
            result.put("keyCount", keys != null ? keys.size() : 0);
            if (keys != null && !keys.isEmpty()) {
                result.put("keys", keys.stream().limit(50).map(k -> new String(k)).toList());
            }
            result.put("redisOk", true);
        } catch (Exception e) {
            result.put("redisOk", false);
            result.put("redisError", e.getMessage());
        }
        return result;
    }

    /**
     * 从 Authorization header 提取 Bearer token
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}
