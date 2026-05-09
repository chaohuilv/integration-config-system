package com.integration.config.entity.log;

import com.integration.config.enums.TestExecutionStatus;
import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 测试执行记录实体（Log 数据库）
 */
@Entity
@Table(name = "TEST_EXECUTION", indexes = {
    @Index(name = "IDX_TE_CASE", columnList = "TEST_CASE_ID"),
    @Index(name = "IDX_TE_SUITE", columnList = "TEST_SUITE_ID"),
    @Index(name = "IDX_TE_STATUS", columnList = "STATUS"),
    @Index(name = "IDX_TE_CREATED", columnList = "CREATED_AT")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestExecution {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 用例ID */
    @Column(name = "TEST_CASE_ID")
    private Long testCaseId;

    /** 用例编码（冗余） */
    @Column(name = "TEST_CASE_CODE", length = 50)
    private String testCaseCode;

    /** 套件ID（若属于套件执行） */
    @Column(name = "TEST_SUITE_ID")
    private Long testSuiteId;

    /** 套件编码（冗余） */
    @Column(name = "TEST_SUITE_CODE", length = 50)
    private String testSuiteCode;

    /** 状态：PENDING / RUNNING / SUCCESS / FAILED / TIMEOUT / SKIPPED */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20)
    private TestExecutionStatus status;

    /** 开始时间 */
    @Column(name = "START_TIME")
    private LocalDateTime startTime;

    /** 结束时间 */
    @Column(name = "END_TIME")
    private LocalDateTime endTime;

    /** 总耗时（毫秒） */
    @Column(name = "COST_TIME_MS")
    private Long costTimeMs;

    /** 触发来源：MANUAL / SCHEDULED / API */
    @Column(name = "TRIGGER_SOURCE", length = 20)
    private String triggerSource;

    /** 触发人 */
    @Column(name = "TRIGGER_USER", length = 50)
    private String triggerUser;

    /** 总步骤数 */
    @Column(name = "TOTAL_STEPS")
    private Integer totalSteps;

    /** 通过步骤数 */
    @Column(name = "PASSED_STEPS")
    private Integer passedSteps;

    /** 失败步骤数 */
    @Column(name = "FAILED_STEPS")
    private Integer failedSteps;

    /** 跳过步骤数 */
    @Column(name = "SKIPPED_STEPS")
    private Integer skippedSteps;

    /** 错误信息（如有） */
    @Column(name = "ERROR_MESSAGE", columnDefinition = "TEXT")
    private String errorMessage;

    /** 链路追踪ID */
    @Column(name = "TRACE_ID", length = 64)
    private String traceId;

    /** 创建时间 */
    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = SnowflakeUtil.nextId();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = TestExecutionStatus.PENDING;
        }
        if (this.totalSteps == null) this.totalSteps = 0;
        if (this.passedSteps == null) this.passedSteps = 0;
        if (this.failedSteps == null) this.failedSteps = 0;
        if (this.skippedSteps == null) this.skippedSteps = 0;
    }
}