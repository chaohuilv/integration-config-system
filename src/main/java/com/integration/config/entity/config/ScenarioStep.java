package com.integration.config.entity.config;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 场景步骤实体（Config 数据库）
 * 定义场景中的单个接口调用步骤
 */
@Entity
@Table(name = "SCENARIO_STEP", indexes = {
    @Index(name = "IDX_STEP_SCENARIO", columnList = "SCENARIO_ID"),
    @Index(name = "IDX_STEP_CODE", columnList = "SCENARIO_ID, STEP_CODE", unique = true),
    @Index(name = "IDX_STEP_ORDER", columnList = "SCENARIO_ID, STEP_ORDER")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioStep {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 所属场景ID */
    @Column(name = "SCENARIO_ID", nullable = false)
    private Long scenarioId;

    /** 步骤编码（场景内唯一） */
    @Column(name = "STEP_CODE", nullable = false, length = 50)
    private String stepCode;

    /** 步骤名称 */
    @Column(name = "STEP_NAME", nullable = false, length = 100)
    private String stepName;

    /** 执行顺序（从 1 开始） */
    @Column(name = "STEP_ORDER", nullable = false)
    private Integer stepOrder;

    /** 调用的接口编码 */
    @Column(name = "API_CODE", nullable = false, length = 50)
    private String apiCode;

    /** 输入参数映射（JSON） */
    @Column(name = "INPUT_MAPPING", columnDefinition = "TEXT")
    private String inputMapping;

    /** 输出参数映射（JSON） */
    @Column(name = "OUTPUT_MAPPING", columnDefinition = "TEXT")
    private String outputMapping;

    /** 执行条件表达式（可选） */
    @Column(name = "CONDITION_EXPR", length = 200)
    private String conditionExpr;

    /** 前序失败时是否跳过（0/1） */
    @Column(name = "SKIP_ON_ERROR")
    private Integer skipOnError;

    /** 失败重试次数，默认 0 */
    @Column(name = "RETRY_COUNT")
    private Integer retryCount;

    /** 缓存时长（秒），>0 时将输出写入场景缓存表 */
    @Column(name = "CACHE_SECONDS")
    private Integer cacheSeconds;

    /** 要缓存的输出字段（逗号分隔，如 access_token,refresh_token），空则缓存全部 */
    @Column(name = "CACHE_KEYS", length = 500)
    private String cacheKeys;

    /** 创建时间 */
    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = SnowflakeUtil.nextId();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
        if (this.skipOnError == null) {
            this.skipOnError = 0;
        }
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
