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
 * 测试步骤执行结果实体（Log 数据库）
 */
@Entity
@Table(name = "TEST_STEP_RESULT", indexes = {
    @Index(name = "IDX_TSR_EXECUTION", columnList = "TEST_EXECUTION_ID")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestStepResult {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 执行记录ID */
    @Column(name = "TEST_EXECUTION_ID", nullable = false)
    private Long testExecutionId;

    /** 步骤序号 */
    @Column(name = "STEP_ORDER")
    private Integer stepOrder;

    /** 步骤编码 */
    @Column(name = "STEP_CODE", length = 50)
    private String stepCode;

    /** 步骤类型：API / SCENARIO / MOCK */
    @Column(name = "STEP_TYPE", length = 20)
    private String stepType;

    /** 目标编码 */
    @Column(name = "TARGET_CODE", length = 50)
    private String targetCode;

    /** 状态：SUCCESS / FAILED / TIMEOUT / SKIPPED */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20)
    private TestExecutionStatus status;

    /** 开始时间 */
    @Column(name = "START_TIME")
    private LocalDateTime startTime;

    /** 结束时间 */
    @Column(name = "END_TIME")
    private LocalDateTime endTime;

    /** 耗时（毫秒） */
    @Column(name = "COST_TIME_MS")
    private Long costTimeMs;

    /** 请求参数（JSON，截断 20KB） */
    @Column(name = "REQUEST_PARAMS", columnDefinition = "TEXT")
    private String requestParams;

    /** 响应数据（截断 20KB） */
    @Column(name = "RESPONSE_DATA", columnDefinition = "TEXT")
    private String responseData;

    /** 断言结果（JSON） */
    @Column(name = "ASSERTION_RESULTS", columnDefinition = "TEXT")
    private String assertionResults;

    /** 错误信息 */
    @Column(name = "ERROR_MESSAGE", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = SnowflakeUtil.nextId();
        }
    }
}