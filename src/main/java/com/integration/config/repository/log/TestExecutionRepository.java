package com.integration.config.repository.log;

import com.integration.config.entity.log.TestExecution;
import com.integration.config.enums.TestExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 测试执行记录 Repository
 */
@Repository
public interface TestExecutionRepository extends JpaRepository<TestExecution, Long> {

    /**
     * 分页查询（按用例）
     */
    @Query("SELECT e FROM TestExecution e WHERE " +
           "(:testCaseId IS NULL OR e.testCaseId = :testCaseId) AND " +
           "(:testSuiteId IS NULL OR e.testSuiteId = :testSuiteId) AND " +
           "(:status IS NULL OR e.status = :status) AND " +
           "(:triggerSource IS NULL OR e.triggerSource = :triggerSource) AND " +
           "(:keyword IS NULL OR e.testCaseCode LIKE %:keyword%)")
    Page<TestExecution> pageQuery(
            @Param("testCaseId") Long testCaseId,
            @Param("testSuiteId") Long testSuiteId,
            @Param("status") TestExecutionStatus status,
            @Param("triggerSource") String triggerSource,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * 分页查询（按时间范围）
     */
    @Query("SELECT e FROM TestExecution e WHERE " +
           "(:startTime IS NULL OR e.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR e.createdAt <= :endTime)")
    Page<TestExecution> findByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * 查询最近执行记录（按套件）
     */
    List<TestExecution> findByTestSuiteIdOrderByCreatedAtDesc(Long testSuiteId);

    /**
     * 查询用例最近执行记录
     */
    List<TestExecution> findByTestCaseIdOrderByCreatedAtDesc(Long testCaseId);
}