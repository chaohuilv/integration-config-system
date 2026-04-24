package com.integration.config.repository.token;

import com.integration.config.entity.token.TokenCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TokenCacheRepository extends JpaRepository<TokenCacheEntry, Long> {

    /**
     * 根据接口编码查找Token（未过期）
     */
    @Query("SELECT t FROM TokenCacheEntry t WHERE t.apiCode = :apiCode AND t.expireTime > :now")
    Optional<TokenCacheEntry> findValidByApiCode(@Param("apiCode") String apiCode, @Param("now") LocalDateTime now);

    /**
     * 根据接口编码查找（不含过期检查）
     */
    Optional<TokenCacheEntry> findByApiCode(String apiCode);

    /**
     * 删除所有过期缓存
     */
    @Modifying
    @Query("DELETE FROM TokenCacheEntry t WHERE t.expireTime < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    /**
     * 删除指定接口的Token
     */
    void deleteByApiCode(String apiCode);

    /**
     * 删除指定Token来源接口的Token
     */
    void deleteByTokenApiCode(String tokenApiCode);

    /**
     * 删除所有
     */
    void deleteAll();
}
