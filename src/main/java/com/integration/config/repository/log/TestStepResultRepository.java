package com.integration.config.repository.log;

import com.integration.config.entity.log.TestStepResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 测试步骤执行结果 Repository
 */
@Repository
public interface TestStepResultRepository extends JpaRepository<TestStepResult, Long> {

    /**
     * 根据执行记录ID查询所有步骤结果（按顺序）
     */
    List<TestStepResult> findByTestExecutionIdOrderByStepOrderAsc(Long testExecutionId);

    /**
     * 根据执行记录ID删除所有步骤结果
     */
    void deleteByTestExecutionId(Long testExecutionId);
}