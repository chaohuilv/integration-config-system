package com.integration.config.repository.log;

import com.integration.config.entity.log.ScenarioStepLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 场景步骤执行日志 Repository
 */
@Repository
public interface ScenarioStepLogRepository extends JpaRepository<ScenarioStepLog, Long> {

    /**
     * 查询执行记录的所有步骤日志
     */
    List<ScenarioStepLog> findByExecutionIdOrderByStepOrder(Long executionId);

    /**
     * 查询执行记录的指定步骤日志
     */
    List<ScenarioStepLog> findByExecutionIdAndStepCode(Long executionId, String stepCode);
}
