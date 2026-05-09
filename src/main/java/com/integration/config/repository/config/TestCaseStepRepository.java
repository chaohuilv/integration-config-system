package com.integration.config.repository.config;

import com.integration.config.entity.config.TestCaseStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 测试用例步骤 Repository
 */
@Repository
public interface TestCaseStepRepository extends JpaRepository<TestCaseStep, Long> {

    /**
     * 根据用例ID查询所有步骤（按顺序）
     */
    List<TestCaseStep> findByTestCaseIdOrderByStepOrderAsc(Long testCaseId);

    /**
     * 根据用例ID和步骤编码查询
     */
    Optional<TestCaseStep> findByTestCaseIdAndStepCode(Long testCaseId, String stepCode);

    /**
     * 根据用例ID删除所有步骤
     */
    void deleteByTestCaseId(Long testCaseId);

    /**
     * 根据用例ID统计步骤数
     */
    long countByTestCaseId(Long testCaseId);
}