package com.integration.config.entity.config;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 测试套件-用例关联实体（Config 数据库）
 */
@Entity
@Table(name = "TEST_SUITE_CASE", indexes = {
    @Index(name = "IDX_TSC_SUITE", columnList = "TEST_SUITE_ID"),
    @Index(name = "IDX_TSC_CASE", columnList = "TEST_CASE_ID")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestSuiteCase {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 套件ID */
    @Column(name = "TEST_SUITE_ID", nullable = false)
    private Long testSuiteId;

    /** 用例ID */
    @Column(name = "TEST_CASE_ID", nullable = false)
    private Long testCaseId;

    /** 用例在套件中的执行顺序 */
    @Column(name = "CASE_ORDER", nullable = false)
    private Integer caseOrder;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = SnowflakeUtil.nextId();
        }
    }
}