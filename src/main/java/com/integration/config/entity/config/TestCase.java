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
 * 测试用例实体（Config 数据库）
 * 自动化测试用例，支持 API/场景编排/Mock 三种步骤类型
 */
@Entity
@Table(name = "TEST_CASE", indexes = {
    @Index(name = "IDX_TEST_CASE_CODE", columnList = "CODE", unique = true),
    @Index(name = "IDX_TEST_CASE_STATUS", columnList = "STATUS"),
    @Index(name = "IDX_TEST_CASE_GROUP", columnList = "GROUP_NAME")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 用例编码，唯一标识 */
    @Column(name = "CODE", nullable = false, unique = true, length = 50)
    private String code;

    /** 用例名称 */
    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    /** 用例描述 */
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** 分组名称 */
    @Column(name = "GROUP_NAME", length = 100)
    private String groupName;

    /** 状态：ACTIVE / INACTIVE */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20)
    private Status status;

    /** 优先级（1=最高，5=最低），默认 3 */
    @Column(name = "PRIORITY")
    private Integer priority;

    /** 标签，逗号分隔，如 smoke,regression */
    @Column(name = "TAGS", length = 500)
    private String tags;

    /** 超时时间（毫秒），默认 60000 */
    @Column(name = "TIMEOUT_MS")
    private Integer timeoutMs;

    /** 失败策略：STOP（立即终止）/ CONTINUE（继续执行） */
    @Column(name = "FAILURE_STRATEGY", length = 20)
    private String failureStrategy;

    /** 失败时是否发送通知 */
    @Column(name = "NOTIFY_ON_FAILURE")
    private Boolean notifyOnFailure;

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
        if (this.priority == null) {
            this.priority = 3;
        }
        if (this.timeoutMs == null) {
            this.timeoutMs = 60000;
        }
        if (this.failureStrategy == null) {
            this.failureStrategy = "STOP";
        }
        if (this.notifyOnFailure == null) {
            this.notifyOnFailure = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}