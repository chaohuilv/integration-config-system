package com.integration.config.repository.log;

import com.integration.config.entity.log.TestReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 测试报告 Repository
 */
@Repository
public interface TestReportRepository extends JpaRepository<TestReport, Long> {

    /**
     * 根据执行记录ID查询报告
     */
    Optional<TestReport> findByTestExecutionId(Long testExecutionId);

    /**
     * 根据套件执行记录ID查询报告
     */
    Optional<TestReport> findByTestSuiteExecutionId(Long testSuiteExecutionId);

    /**
     * 查询最近的报告
     */
    List<TestReport> findTop10ByOrderByGeneratedAtDesc();
}