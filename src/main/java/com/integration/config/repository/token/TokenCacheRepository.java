package com.integration.config.repository.token;

import com.integration.config.entity.token.TokenCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Token缓存 Repository（Token 数据库）
 */
@Repository
public interface TokenCacheRepository extends JpaRepository<TokenCacheEntry, Long> {

    /**
     * 根据业务接口编码查询Token
     */
    Optional<TokenCacheEntry> findByApiCode(String apiCode);

    /**
     * 根据业务接口编码查询未过期的Token
     */
    @Query("SELECT t FROM TokenCacheEntry t WHERE t.apiCode = :apiCode AND t.expireTime > :now")
    TokenCacheEntry findValidByApiCode(@Param("apiCode") String apiCode, @Param("now") LocalDateTime now);

    /**
     * 删除指定Token接口关联的所有缓存
     */
    @Modifying
    void deleteByTokenApiCode(String tokenApiCode);

    /**
     * 清除所有过期Token
     */
    @Modifying
    @Query("DELETE FROM TokenCacheEntry t WHERE t.expireTime <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
