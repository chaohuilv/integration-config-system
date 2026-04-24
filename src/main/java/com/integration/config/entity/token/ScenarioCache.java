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
 * 场景输出缓存实体（Token 数据库）
 * 存储场景步骤的输出结果，支持过期自动失效
 * 用于 Token 等有时效性的数据在多次执行间共享
 */
@Entity
@Table(name = "SCENARIO_CACHE", indexes = {
    @Index(name = "IDX_SC_CACHE_KEY", columnList = "CACHE_KEY", unique = true),
    @Index(name = "IDX_SC_SCENARIO", columnList = "SCENARIO_ID"),
    @Index(name = "IDX_SC_EXPIRE", columnList = "EXPIRE_TIME")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioCache {

    @Id
    @Column(name = "ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 缓存Key，格式：{scenarioCode}:{stepCode}:{outputKey}
     * 例如：oauth_flow:login:access_token
     */
    @Column(name = "CACHE_KEY", nullable = false, unique = true, length = 200)
    private String cacheKey;

    /**
     * 所属场景ID
     */
    @Column(name = "SCENARIO_ID", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long scenarioId;

    /**
     * 所属场景编码
     */
    @Column(name = "SCENARIO_CODE", nullable = false, length = 50)
    private String scenarioCode;

    /**
     * 步骤编码
     */
    @Column(name = "STEP_CODE", nullable = false, length = 50)
    private String stepCode;

    /**
     * 输出字段名（JsonPath 提取后的 key）
     */
    @Column(name = "OUTPUT_KEY", nullable = false, length = 100)
    private String outputKey;

    /**
     * 缓存值（JSON 字符串）
     */
    @Column(name = "CACHE_VALUE", nullable = false, columnDefinition = "TEXT")
    private String cacheValue;

    /**
     * 过期时间
     */
    @Column(name = "EXPIRE_TIME", nullable = false)
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = SnowflakeUtil.nextId();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * 判断缓存是否已过期
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expireTime);
    }
}
