package com.integration.config.repository.config;

import com.integration.config.entity.config.AlertRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    Optional<AlertRule> findByRuleCode(String ruleCode);

    List<AlertRule> findByStatus(String status);

    @Query("SELECT r FROM AlertRule r WHERE r.status = 'ACTIVE' ORDER BY r.createdAt DESC")
    List<AlertRule> findAllActive();

    @Query("SELECT r FROM AlertRule r WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR r.ruleName LIKE %:keyword% OR r.ruleCode LIKE %:keyword%) " +
           "AND (:alertType IS NULL OR :alertType = '' OR r.alertType = :alertType) " +
           "AND (:status IS NULL OR :status = '' OR r.status = :status) " +
           "ORDER BY r.createdAt DESC")
    Page<AlertRule> findByConditions(
            @Param("keyword") String keyword,
            @Param("alertType") String alertType,
            @Param("status") String status,
            Pageable pageable);
}
