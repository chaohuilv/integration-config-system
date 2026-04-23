package com.integration.config.repository.config;

import com.integration.config.entity.config.AlertRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertRecordRepository extends JpaRepository<AlertRecord, Long> {

    /** 查找某规则最近一次 FIRING 告警时间（用于冷却判断） */
    @Query("SELECT MAX(r.alertTime) FROM AlertRecord r WHERE r.ruleCode = :ruleCode AND r.status = 'FIRING'")
    LocalDateTime findLastFiringAlertTime(@Param("ruleCode") String ruleCode);

    @Query("SELECT MAX(r.alertTime) FROM AlertRecord r WHERE r.ruleCode = :ruleCode AND r.apiCode = :apiCode AND r.status = 'FIRING'")
    LocalDateTime findLastFiringAlertTimeByApi(@Param("ruleCode") String ruleCode, @Param("apiCode") String apiCode);

    @Query("SELECT r FROM AlertRecord r WHERE " +
           "(:keyword IS NULL OR r.ruleName LIKE %:keyword% OR r.ruleCode LIKE %:keyword% OR r.apiCode LIKE %:keyword%) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:alertType IS NULL OR r.alertType = :alertType) " +
           "AND (:startTime IS NULL OR r.alertTime >= :startTime) " +
           "AND (:endTime IS NULL OR r.alertTime <= :endTime) " +
           "ORDER BY r.alertTime DESC")
    Page<AlertRecord> findByConditions(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("alertType") String alertType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    @Query("SELECT COUNT(r) FROM AlertRecord r WHERE r.status = 'FIRING'")
    Long countFiring();

    @Query("SELECT COUNT(r) FROM AlertRecord r WHERE r.alertTime >= :start")
    Long countSince(@Param("start") LocalDateTime start);

    @Modifying
    @Query("DELETE FROM AlertRecord r WHERE r.alertTime < :time")
    void deleteOlderThan(@Param("time") LocalDateTime time);

    List<AlertRecord> findByApiCodeAndStatus(String apiCode, String status);

    List<AlertRecord> findByRuleCodeAndStatus(String ruleCode, String status);
}
