package com.integration.config.service;

import com.integration.config.entity.token.ScenarioCache;
import com.integration.config.repository.token.ScenarioCacheRepository;
import com.integration.config.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 场景输出缓存服务
 * 支持 Token 等有时效性的数据在多次场景执行间共享
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScenarioCacheService {

    private final ScenarioCacheRepository scenarioCacheRepository;

    // 显式指定 tokenTransactionManager，避免被 configTransactionManager 覆盖
    private String getTxManager() { return "tokenTransactionManager"; }

    /**
     * 构建缓存Key
     * @param scenarioCode 场景编码
     * @param stepCode 步骤编码
     * @param outputKey 输出字段名
     */
    public static String buildCacheKey(String scenarioCode, String stepCode, String outputKey) {
        return scenarioCode + ":" + stepCode + ":" + outputKey;
    }

    /**
     * 获取缓存值（未过期）
     * @param scenarioCode 场景编码
     * @param stepCode 步骤编码
     * @param outputKey 输出字段名
     * @return 缓存值（JSON反序列化后的对象），不存在或已过期返回 null
     */
    public Object get(String scenarioCode, String stepCode, String outputKey) {
        String cacheKey = buildCacheKey(scenarioCode, stepCode, outputKey);
        Optional<ScenarioCache> entry = scenarioCacheRepository.findValidByKey(cacheKey, LocalDateTime.now());
        if (entry.isEmpty()) {
            log.debug("场景缓存未命中: {}", cacheKey);
            return null;
        }
        log.info("场景缓存命中: {}, 剩余TTL约{}秒", cacheKey,
                java.time.Duration.between(LocalDateTime.now(), entry.get().getExpireTime()).getSeconds());
        try {
            return JsonUtil.fromJson(entry.get().getCacheValue(), Object.class);
        } catch (Exception e) {
            log.warn("缓存值反序列化失败: {}", cacheKey, e);
            return entry.get().getCacheValue();
        }
    }

    /**
     * 获取缓存值（原始字符串）
     */
    public String getString(String scenarioCode, String stepCode, String outputKey) {
        String cacheKey = buildCacheKey(scenarioCode, stepCode, outputKey);
        return scenarioCacheRepository.findValidByKey(cacheKey, LocalDateTime.now())
                .map(ScenarioCache::getCacheValue)
                .orElse(null);
    }

    /**
     * 缓存输出字段（单个 key）
     * @param scenarioId 场景ID
     * @param scenarioCode 场景编码
     * @param stepCode 步骤编码
     * @param outputKey 输出字段名
     * @param value 缓存值（任意可序列化对象）
     * @param cacheSeconds 缓存时间（秒），<=0 表示不缓存
     */
    @Transactional("tokenTransactionManager")
    public void put(Long scenarioId, String scenarioCode, String stepCode,
                    String outputKey, Object value, int cacheSeconds) {
        if (cacheSeconds <= 0) {
            return;
        }
        String cacheKey = buildCacheKey(scenarioCode, stepCode, outputKey);
        // 统一用 JsonUtil.toJson 序列化，确保存储的都是合法 JSON
        // 纯字符串如 JWT 会被序列化为 "eyJ0eXAi..."（带引号），fromJson 时能正确还原为 String
        String cacheValue = JsonUtil.toJson(value);

        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(cacheSeconds);

        Optional<ScenarioCache> existing = scenarioCacheRepository.findByCacheKey(cacheKey);
        if (existing.isPresent()) {
            ScenarioCache entry = existing.get();
            entry.setCacheValue(cacheValue);
            entry.setExpireTime(expireTime);
            scenarioCacheRepository.save(entry);
        } else {
            ScenarioCache entry = ScenarioCache.builder()
                    .scenarioId(scenarioId)
                    .scenarioCode(scenarioCode)
                    .stepCode(stepCode)
                    .outputKey(outputKey)
                    .cacheKey(cacheKey)
                    .cacheValue(cacheValue)
                    .expireTime(expireTime)
                    .build();
            scenarioCacheRepository.save(entry);
        }
        log.info("场景缓存写入: {} = {} (TTL={}s)", cacheKey,
                value instanceof String ? value : JsonUtil.toJson(value), cacheSeconds);
    }

    /**
     * 批量缓存输出字段（多个 key）
     * @param scenarioId 场景ID
     * @param scenarioCode 场景编码
     * @param stepCode 步骤编码
     * @param outputMap 输出字段 Map（key 为 outputKey，value 为值）
     * @param cacheSeconds 缓存时间（秒）
     */
    @Transactional("tokenTransactionManager")
    public void putAll(Long scenarioId, String scenarioCode, String stepCode,
                       Map<String, Object> outputMap, int cacheSeconds) {
        if (cacheSeconds <= 0 || outputMap == null || outputMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : outputMap.entrySet()) {
            put(scenarioId, scenarioCode, stepCode, entry.getKey(), entry.getValue(), cacheSeconds);
        }
    }

    /**
     * 清除场景所有缓存
     */
    @Transactional("tokenTransactionManager")
    public void clearByScenario(String scenarioCode) {
        scenarioCacheRepository.deleteByScenarioCode(scenarioCode);
        log.info("场景缓存已清除: {}", scenarioCode);
    }

    /**
     * 清除场景+步骤的缓存
     */
    @Transactional("tokenTransactionManager")
    public void clearByStep(String scenarioCode, String stepCode) {
        scenarioCacheRepository.deleteByScenarioCodeAndStepCode(scenarioCode, stepCode);
        log.info("步骤缓存已清除: {}/{}", scenarioCode, stepCode);
    }

    /**
     * 清除所有场景缓存
     */
    @Transactional("tokenTransactionManager")
    public void clearAll() {
        scenarioCacheRepository.deleteAll();
        log.info("所有场景缓存已清除");
    }

    /**
     * 查询场景所有缓存（用于调试）
     */
    public List<ScenarioCache> listByScenario(String scenarioCode) {
        return scenarioCacheRepository.findByScenarioCode(scenarioCode);
    }

    /**
     * 定时清理过期缓存（每小时执行）
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional("tokenTransactionManager")
    public void cleanExpired() {
        int deleted = scenarioCacheRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("清理过期场景缓存: {}条", deleted);
        }
    }
}
