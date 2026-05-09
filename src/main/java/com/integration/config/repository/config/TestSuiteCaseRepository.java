package com.integration.config.repository.config;

import com.integration.config.entity.config.TestSuiteCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 测试套件-用例关联 Repository
 */
@Repository
public interface TestSuiteCaseRepository extends JpaRepository<TestSuiteCase, Long> {

    /**
     * 根据套件ID查询所有关联（按顺序）
     */
    List<TestSuiteCase> findByTestSuiteIdOrderByCaseOrderAsc(Long testSuiteId);

    /**
     * 根据套件ID删除所有关联
     */
    void deleteByTestSuiteId(Long testSuiteId);

    /**
     * 根据套件ID统计用例数
     */
    long countByTestSuiteId(Long testSuiteId);
}