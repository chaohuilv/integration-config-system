package com.integration.config.repository.config;

import com.integration.config.entity.config.ScenarioStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 场景步骤 Repository
 */
@Repository
public interface ScenarioStepRepository extends JpaRepository<ScenarioStep, Long> {

    /**
     * 查询场景的所有步骤（按顺序）
     */
    List<ScenarioStep> findByScenarioIdOrderByStepOrder(Long scenarioId);

    /**
     * 查询场景的指定步骤
     */
    Optional<ScenarioStep> findByScenarioIdAndStepCode(Long scenarioId, String stepCode);

    /**
     * 删除场景的所有步骤
     */
    @Modifying
    @Query("DELETE FROM ScenarioStep s WHERE s.scenarioId = :scenarioId")
    void deleteByScenarioId(@Param("scenarioId") Long scenarioId);

    /**
     * 查询场景的最大步骤顺序
     */
    @Query("SELECT MAX(s.stepOrder) FROM ScenarioStep s WHERE s.scenarioId = :scenarioId")
    Integer findMaxStepOrder(@Param("scenarioId") Long scenarioId);

    /**
     * 统计场景的步骤数量
     */
    long countByScenarioId(Long scenarioId);
}
