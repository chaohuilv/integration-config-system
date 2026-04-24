package com.integration.config.service;

import com.integration.config.entity.token.TokenCacheEntry;
import com.integration.config.repository.token.TokenCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Token缓存管理器（数据库持久化）
 * Token存储在独立 H2 数据库中，支持多实例共享
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenCacheManager {

    private final TokenCacheRepository tokenCacheRepository;

    /**
     * 获取缓存的Token（未过期）
     * @param apiCode 业务接口编码
     * @return Token字符串，不存在或已过期返回null
     */
    @Transactional(value = "tokenTransactionManager", readOnly = true)
    public String getCachedToken(String apiCode) {
        TokenCacheEntry entry = tokenCacheRepository.findValidByApiCode(apiCode, LocalDateTime.now()).orElse(null);
        if (entry != null) {
            log.debug("Token缓存命中: apiCode={}", apiCode);
            return entry.getToken();
        }
        return null;
    }

    /**
     * 缓存Token（upsert：存在则更新，不存在则插入）
     * @param apiCode 业务接口编码
     * @param token Token值
     * @param cacheSeconds 缓存时间（秒），<=0 表示不缓存
     * @param tokenApiCode 获取Token的接口编码
     */
    @Transactional("tokenTransactionManager")
    public void cacheToken(String apiCode, String token, int cacheSeconds, String tokenApiCode) {
        if (cacheSeconds <= 0) {
            return;
        }
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(cacheSeconds);
        // 查找是否已有记录，有则更新，无则新建
        TokenCacheEntry existing = tokenCacheRepository.findByApiCode(apiCode).orElse(null);
        if (existing != null) {
            existing.setToken(token);
            existing.setTokenApiCode(tokenApiCode);
            existing.setExpireTime(expireTime);
            tokenCacheRepository.save(existing);
        } else {
            TokenCacheEntry entry = TokenCacheEntry.builder()
                    .apiCode(apiCode)
                    .token(token)
                    .tokenApiCode(tokenApiCode)
                    .expireTime(expireTime)
                    .build();
            tokenCacheRepository.save(entry);
        }
        log.info("Token已持久化到数据库: apiCode={}, tokenApiCode={}, cacheSeconds={}", apiCode, tokenApiCode, cacheSeconds);
    }

    /**
     * 清除指定接口的Token缓存
     */
    @Transactional("tokenTransactionManager")
    public void clearToken(String apiCode) {
        tokenCacheRepository.findByApiCode(apiCode)
                .ifPresent(entry -> {
                    tokenCacheRepository.delete(entry);
                    log.info("Token缓存已清除: apiCode={}", apiCode);
                });
    }

    /**
     * 清除所有Token缓存
     */
    @Transactional("tokenTransactionManager")
    public void clearAll() {
        tokenCacheRepository.deleteAll();
        log.info("所有Token缓存已清除");
    }

    /**
     * 强制使指定Token接口关联的所有缓存失效
     */
    @Transactional("tokenTransactionManager")
    public void invalidateByTokenApiCode(String tokenApiCode) {
        tokenCacheRepository.deleteByTokenApiCode(tokenApiCode);
        log.info("Token接口相关缓存已失效: tokenApiCode={}", tokenApiCode);
    }

    /**
     * 定时清理过期Token（每小时执行一次）
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional("tokenTransactionManager")
    public void cleanExpired() {
        int deleted = tokenCacheRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("清理过期Token缓存: {}条", deleted);
        }
    }
}
