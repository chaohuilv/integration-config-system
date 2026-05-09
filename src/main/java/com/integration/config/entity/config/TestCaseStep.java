package com.integration.config.entity.config;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 测试用例步骤实体（Config 数据库）
 * 支持三种步骤类型：API / SCENARIO / MOCK
 */
@Entity
@Table(name = "TEST_CASE_STEP", indexes = {
    @Index(name = "IDX_TCS_CASE", columnList = "TEST_CASE_ID"),
    @Index(name = "IDX_TCS_ORDER", columnList = "TEST_CASE_ID, STEP_ORDER")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseStep {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 所属用例ID */
    @Column(name = "TEST_CASE_ID", nullable = false)
    private Long testCaseId;

    /** 步骤顺序（从 1 开始） */
    @Column(name = "STEP_ORDER", nullable = false)
    private Integer stepOrder;

    /** 步骤编码（用例内唯一） */
    @Column(name = "STEP_CODE", nullable = false, length = 50)
    private String stepCode;

    /** 步骤名称 */
    @Column(name = "STEP_NAME", length = 100)
    private String stepName;

    /** 步骤类型：API / SCENARIO / MOCK */
    @Column(name = "STEP_TYPE", nullable = false, length = 20)
    private String stepType;

    /** 目标编码（API code / Scenario code / Mock code） */
    @Column(name = "TARGET_CODE", length = 50)
    private String targetCode;

    /** 目标ID（冗余存储） */
    @Column(name = "TARGET_ID")
    private Long targetId;

    /** 请求覆盖参数（JSON，合并到目标配置的请求参数上） */
    @Column(name = "REQUEST_OVERRIDE", columnDefinition = "TEXT")
    private String requestOverride;

    /** 断言配置（JSON 数组） */
    @Column(name = "ASSERTIONS", columnDefinition = "TEXT")
    private String assertions;

    /** 前序失败时是否跳过（0/1），默认 0 */
    @Column(name = "SKIP_ON_ERROR")
    private Integer skipOnError;

    /** 步骤超时（毫秒），默认继承用例超时 */
    @Column(name = "TIMEOUT_MS")
    private Integer timeoutMs;

    /** 是否启用，默认 true */
    @Column(name = "ENABLED")
    private Boolean enabled;

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
        if (this.enabled == null) {
            this.enabled = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}