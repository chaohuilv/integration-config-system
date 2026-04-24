package com.integration.config.entity.log;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 场景执行记录实体（Log 数据库）
 * 记录每次场景执行的完整信息
 */
@Entity
@Table(name = "SCENARIO_EXECUTION", indexes = {
    @Index(name = "IDX_EXEC_SCENARIO", columnList = "SCENARIO_ID"),
    @Index(name = "IDX_EXEC_CODE", columnList = "SCENARIO_CODE"),
    @Index(name = "IDX_EXEC_STATUS", columnList = "STATUS"),
    @Index(name = "IDX_EXEC_TIME", columnList = "START_TIME")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioExecution {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 场景ID */
    @Column(name = "SCENARIO_ID")
    private Long scenarioId;

    /** 场景编码 */
    @Column(name = "SCENARIO_CODE", length = 50)
    private String scenarioCode;

    /** 场景名称 */
    @Column(name = "SCENARIO_NAME", length = 100)
    private String scenarioName;

    /** 执行状态：RUNNING / SUCCESS / FAILED / PARTIAL */
    @Column(name = "STATUS", length = 20)
    private String status;

    /** 开始时间 */
    @Column(name = "START_TIME")
    private LocalDateTime startTime;

    /** 结束时间 */
    @Column(name = "END_TIME")
    private LocalDateTime endTime;

    /** 耗时（毫秒） */
    @Column(name = "COST_TIME_MS")
    private Long costTimeMs;

    /** 触发来源：MANUAL / SCHEDULE / API */
    @Column(name = "TRIGGER_SOURCE", length = 50)
    private String triggerSource;

    /** 触发用户 */
    @Column(name = "TRIGGER_USER", length = 50)
    private String triggerUser;

    /** 错误信息 */
    @Column(name = "ERROR_MESSAGE", columnDefinition = "TEXT")
    private String errorMessage;

    /** 执行上下文（JSON，存储所有步骤输出） */
    @Column(name = "CONTEXT", columnDefinition = "TEXT")
    private String context;

    /** 链路追踪ID */
    @Column(name = "TRACE_ID", length = 50)
    private String traceId;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = SnowflakeUtil.nextId();
        }
        if (this.startTime == null) {
            this.startTime = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = "RUNNING";
        }
    }
}
