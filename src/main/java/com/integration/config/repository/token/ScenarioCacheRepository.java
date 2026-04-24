package com.integration.config.repository.token;

import com.integration.config.entity.token.ScenarioCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScenarioCacheRepository extends JpaRepository<ScenarioCache, Long> {

    /**
     * 根据缓存Key查找（未过期）
     */
    @Query("SELECT c FROM ScenarioCache c WHERE c.cacheKey = :cacheKey AND c.expireTime > :now")
    Optional<ScenarioCache> findValidByKey(@Param("cacheKey") String cacheKey, @Param("now") LocalDateTime now);

    /**
     * 根据缓存Key查找（含过期检查）
     */
    Optional<ScenarioCache> findByCacheKey(String cacheKey);

    /**
     * 根据场景ID删除所有缓存
     */
    void deleteByScenarioId(Long scenarioId);

    /**
     * 根据场景编码删除所有缓存
     */
    void deleteByScenarioCode(String scenarioCode);

    /**
     * 根据步骤编码删除缓存
     */
    void deleteByScenarioCodeAndStepCode(String scenarioCode, String stepCode);

    /**
     * 清理过期缓存
     */
    @Modifying
    @Query("DELETE FROM ScenarioCache c WHERE c.expireTime < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    /**
     * 查询某场景的所有缓存（用于调试）
     */
    List<ScenarioCache> findByScenarioCode(String scenarioCode);
}
