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
 * 场景配置实体（Config 数据库）
 * 场景编排：将多个接口调用组合成一个自动化流程
 */
@Entity
@Table(name = "SCENARIO", indexes = {
    @Index(name = "IDX_SCENARIO_CODE", columnList = "CODE", unique = true),
    @Index(name = "IDX_SCENARIO_GROUP", columnList = "GROUP_NAME"),
    @Index(name = "IDX_SCENARIO_STATUS", columnList = "STATUS")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Scenario {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 场景编码，唯一标识 */
    @Column(name = "CODE", nullable = false, unique = true, length = 50)
    private String code;

    /** 场景名称 */
    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    /** 场景描述 */
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** 分组名称 */
    @Column(name = "GROUP_NAME", length = 50)
    private String groupName;

    /** 失败策略：STOP（立即终止）/ CONTINUE（继续执行） */
    @Column(name = "FAILURE_STRATEGY", length = 20)
    private String failureStrategy;

    /** 整体超时时间（秒），默认 300 */
    @Column(name = "TIMEOUT_SECONDS")
    private Integer timeoutSeconds;

    /** 状态：ACTIVE / INACTIVE */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20)
    private Status status;

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

    /** 更新人名称 */
    @Column(name = "UPDATED_BY_NAME", length = 50)
    private String updatedByName;

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
        if (this.failureStrategy == null) {
            this.failureStrategy = "STOP";
        }
        if (this.timeoutSeconds == null) {
            this.timeoutSeconds = 300;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
