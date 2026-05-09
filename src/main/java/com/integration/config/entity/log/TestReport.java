package com.integration.config.entity.log;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 测试报告实体（Log 数据库）
 */
@Entity
@Table(name = "TEST_REPORT", indexes = {
    @Index(name = "IDX_TR_EXECUTION", columnList = "TEST_EXECUTION_ID"),
    @Index(name = "IDX_TR_SUITE_EXECUTION", columnList = "TEST_SUITE_EXECUTION_ID")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestReport {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 用例执行记录ID */
    @Column(name = "TEST_EXECUTION_ID")
    private Long testExecutionId;

    /** 套件执行记录ID（若属于套件） */
    @Column(name = "TEST_SUITE_EXECUTION_ID")
    private Long testSuiteExecutionId;

    /** 报告类型：SUITE / CASE */
    @Column(name = "REPORT_TYPE", length = 20)
    private String reportType;

    /** 报告名称，如「用户模块冒烟测试报告」 */
    @Column(name = "REPORT_NAME", length = 200)
    private String reportName;

    /** 摘要 JSON（含统计数据） */
    @Column(name = "SUMMARY", columnDefinition = "TEXT")
    private String summary;

    /** 完整报告 JSON（含所有步骤详细结果） */
    @Column(name = "DETAILS", columnDefinition = "TEXT")
    private String details;

    /** 总耗时（毫秒） */
    @Column(name = "DURATION_MS")
    private Long durationMs;

    /** 报告生成时间 */
    @Column(name = "GENERATED_AT")
    private LocalDateTime generatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = SnowflakeUtil.nextId();
        }
        if (this.generatedAt == null) {
            this.generatedAt = LocalDateTime.now();
        }
    }
}