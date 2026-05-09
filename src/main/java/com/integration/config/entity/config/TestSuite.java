package com.integration.config.entity.config;

import com.integration.config.enums.Status;
import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 测试套件实体（Config 数据库）
 * 将多个测试用例分组，支持顺序/并行执行
 */
@Entity
@Table(name = "TEST_SUITE", indexes = {
    @Index(name = "IDX_TEST_SUITE_CODE", columnList = "CODE", unique = true),
    @Index(name = "IDX_TEST_SUITE_STATUS", columnList = "STATUS"),
    @Index(name = "IDX_TEST_SUITE_GROUP", columnList = "GROUP_NAME")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestSuite {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 套件编码，唯一标识 */
    @Column(name = "CODE", nullable = false, unique = true, length = 50)
    private String code;

    /** 套件名称 */
    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    /** 套件描述 */
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** 分组名称 */
    @Column(name = "GROUP_NAME", length = 100)
    private String groupName;

    /** 状态：ACTIVE / INACTIVE */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20)
    private Status status;

    /** 执行模式：SEQUENTIAL（顺序）/ PARALLEL（并行），默认 SEQUENTIAL */
    @Column(name = "EXECUTION_MODE", length = 20)
    private String executionMode;

    /** 并发数（PARALLEL 模式时生效），默认 3 */
    @Column(name = "CONCURRENCY")
    private Integer concurrency;

    /** 第一个失败即停止整个套件，默认 false */
    @Column(name = "STOP_ON_FIRST_FAILURE")
    private Boolean stopOnFirstFailure;

    /** 套件级超时时间（毫秒），默认 300000（5分钟） */
    @Column(name = "TIMEOUT_MS")
    private Integer timeoutMs;

    /** 执行完成（无论成功失败）是否通知，默认 true */
    @Column(name = "NOTIFY_ON_COMPLETE")
    private Boolean notifyOnComplete;

    /** 创建时间 */
    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** 创建人ID */
    @Column(name = "CREATED_BY_ID")
    private Long createdById;

    /** 创建人名称 */
    @Column(name = "CREATED_BY_NAME", length = 50)
    private String createdByName;

    /** 更新人ID */
    @Column(name = "UPDATED_BY_ID")
    private Long updatedById;

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
        if (this.status == null) {
            this.status = Status.ACTIVE;
        }
        if (this.executionMode == null) {
            this.executionMode = "SEQUENTIAL";
        }
        if (this.concurrency == null) {
            this.concurrency = 3;
        }
        if (this.stopOnFirstFailure == null) {
            this.stopOnFirstFailure = false;
        }
        if (this.timeoutMs == null) {
            this.timeoutMs = 300000;
        }
        if (this.notifyOnComplete == null) {
            this.notifyOnComplete = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}