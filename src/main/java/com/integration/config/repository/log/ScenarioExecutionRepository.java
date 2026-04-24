package com.integration.config.repository.log;

import com.integration.config.entity.log.ScenarioExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 场景执行记录 Repository
 */
@Repository
public interface ScenarioExecutionRepository extends JpaRepository<ScenarioExecution, Long> {

    /**
     * 分页查询执行记录
     */
    @Query("SELECT e FROM ScenarioExecution e WHERE " +
           "(:scenarioId IS NULL OR e.scenarioId = :scenarioId) AND " +
           "(:status IS NULL OR e.status = :status) AND " +
           "(:scenarioCode IS NULL OR e.scenarioCode = :scenarioCode)")
    Page<ScenarioExecution> pageQuery(
            @Param("scenarioId") Long scenarioId,
            @Param("status") String status,
            @Param("scenarioCode") String scenarioCode,
            Pageable pageable);

    /**
     * 查询场景的最近执行记录
     */
    List<ScenarioExecution> findTop10ByScenarioIdOrderByStartTimeDesc(Long scenarioId);

    /**
     * 查询正在执行的记录
     */
    List<ScenarioExecution> findByStatus(String status);
}
