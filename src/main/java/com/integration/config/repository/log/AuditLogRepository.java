package com.integration.config.repository.log;

import com.integration.config.entity.log.AuditSysLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志 Repository
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditSysLog, Long>, JpaSpecificationExecutor<AuditSysLog> {

    // 今日审计数
    @Query("SELECT COUNT(l) FROM AuditSysLog l WHERE l.operateTime >= :start")
    Long countToday(@Param("start") LocalDateTime start);

    // 最近操作（实时活动）
    List<AuditSysLog> findTop20ByOrderByOperateTimeDesc();

    // 按模块统计
    @Query("SELECT l.module, COUNT(l) FROM AuditSysLog l WHERE l.operateTime >= :start GROUP BY l.module ORDER BY COUNT(l) DESC")
    List<Object[]> countByModule(@Param("start") LocalDateTime start);

    // 按操作类型统计
    @Query("SELECT l.operateType, COUNT(l) FROM AuditSysLog l WHERE l.operateTime >= :start GROUP BY l.operateType ORDER BY COUNT(l) DESC")
    List<Object[]> countByOperateType(@Param("start") LocalDateTime start);

    // 最近7天每日审计数 — H2 FORMATDATETIME
    @Query(value = "SELECT FORMATDATETIME(OPERATE_TIME, 'yyyy-MM-dd'), COUNT(*) " +
           "FROM AUDIT_LOG WHERE OPERATE_TIME >= :start " +
           "GROUP BY FORMATDATETIME(OPERATE_TIME, 'yyyy-MM-dd') ORDER BY 1", nativeQuery = true)
    List<Object[]> countDailyTrend(@Param("start") LocalDateTime start);
}
