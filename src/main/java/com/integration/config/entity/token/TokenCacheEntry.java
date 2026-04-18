package com.integration.config.entity.token;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Token缓存实体（Token 数据库）
 * 替代内存 ConcurrentHashMap，Token 持久化存储
 */
@Entity
@Table(name = "TOKEN_CACHE", indexes = {
    @Index(name = "IDX_API_CODE", columnList = "API_CODE"),
    @Index(name = "IDX_EXPIRE_TIME", columnList = "EXPIRE_TIME")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenCacheEntry {

    @Id
    @Column(name = "ID")
    private Long id;

    /**
     * 使用该Token的业务接口编码
     */
    @Column(name = "API_CODE", nullable = false, unique = true, length = 50)
    private String apiCode;

    /**
     * Token 值
     */
    @Column(name = "TOKEN", nullable = false, columnDefinition = "TEXT")
    private String token;

    /**
     * 对应的Token接口编码
     */
    @Column(name = "TOKEN_API_CODE", length = 50)
    private String tokenApiCode;

    /**
     * 过期时间
     */
    @Column(name = "EXPIRE_TIME", nullable = false)
    private LocalDateTime expireTime;

    /**
     * 创建/刷新时间
     */
    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = SnowflakeUtil.nextId();
        }
        createdAt = LocalDateTime.now();
    }

    /**
     * 判断Token是否已过期
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expireTime);
    }
}
