package com.integration.config.entity.log;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 场景步骤执行日志实体（Log 数据库）
 * 记录场景中每个步骤的执行详情
 */
@Entity
@Table(name = "SCENARIO_STEP_LOG", indexes = {
    @Index(name = "IDX_STEP_LOG_EXEC", columnList = "EXECUTION_ID"),
    @Index(name = "IDX_STEP_LOG_STEP", columnList = "STEP_ID"),
    @Index(name = "IDX_STEP_LOG_TIME", columnList = "START_TIME")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioStepLog {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 执行记录ID */
    @Column(name = "EXECUTION_ID", nullable = false)
    private Long executionId;

    /** 步骤ID */
    @Column(name = "STEP_ID")
    private Long stepId;

    /** 步骤编码 */
    @Column(name = "STEP_CODE", length = 50)
    private String stepCode;

    /** 步骤名称 */
    @Column(name = "STEP_NAME", length = 100)
    private String stepName;

    /** 步骤顺序 */
    @Column(name = "STEP_ORDER")
    private Integer stepOrder;

    /** 执行状态：SUCCESS / FAILED / SKIPPED */
    @Column(name = "STATUS", length = 20)
    private String status;

    /** 请求参数（JSON） */
    @Column(name = "REQUEST_PARAMS", columnDefinition = "TEXT")
    private String requestParams;

    /** 响应数据（JSON） */
    @Column(name = "RESPONSE_DATA", columnDefinition = "TEXT")
    private String responseData;

    /** 错误信息 */
    @Column(name = "ERROR_MESSAGE", columnDefinition = "TEXT")
    private String errorMessage;

    /** 开始时间 */
    @Column(name = "START_TIME")
    private LocalDateTime startTime;

    /** 结束时间 */
    @Column(name = "END_TIME")
    private LocalDateTime endTime;

    /** 耗时（毫秒） */
    @Column(name = "COST_TIME_MS")
    private Long costTimeMs;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = SnowflakeUtil.nextId();
        }
        if (this.startTime == null) {
            this.startTime = LocalDateTime.now();
        }
    }
}
