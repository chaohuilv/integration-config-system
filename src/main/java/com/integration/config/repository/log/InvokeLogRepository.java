package com.integration.config.repository.log;

import com.integration.config.entity.log.InvokeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 调用日志 Repository（Log 数据库）
 */
@Repository
public interface InvokeLogRepository extends JpaRepository<InvokeLog, Long> {

    @Query("SELECT l FROM InvokeLog l WHERE " +
           "(:apiCode IS NULL OR l.apiCode = :apiCode) " +
           "AND (:startTime IS NULL OR l.invokeTime >= :startTime) " +
           "AND (:endTime IS NULL OR l.invokeTime <= :endTime) " +
           "AND (:success IS NULL OR l.success = :success) " +
           "AND (:requestUrl IS NULL OR l.requestUrl LIKE %:requestUrl%) " +
           "AND (:requestBody IS NULL OR l.requestBody LIKE %:requestBody%) " +
           "ORDER BY l.invokeTime DESC")
    Page<InvokeLog> findByConditions(
            @Param("apiCode") String apiCode,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("success") Boolean success,
            @Param("requestUrl") String requestUrl,
            @Param("requestBody") String requestBody,
            Pageable pageable);

    List<InvokeLog> findTop10ByApiCodeOrderByInvokeTimeDesc(String apiCode);

    @Query("SELECT COUNT(l) FROM InvokeLog l WHERE l.apiCode = :apiCode AND l.success = true")
    Long countSuccessByApiCode(@Param("apiCode") String apiCode);

    Long countByApiCode(String apiCode);

    @Modifying
    void deleteByInvokeTimeBefore(LocalDateTime time);

    // 全局统计
    @Query("SELECT COUNT(l) FROM InvokeLog l WHERE l.success = true")
    Long countAllSuccess();

    @Query("SELECT COUNT(l) FROM InvokeLog l WHERE l.success = false")
    Long countAllFail();

    // 今日统计
    @Query("SELECT COUNT(l) FROM InvokeLog l WHERE l.invokeTime >= :start AND l.success = true")
    Long countTodaySuccess(@Param("start") LocalDateTime start);

    @Query("SELECT COUNT(l) FROM InvokeLog l WHERE l.invokeTime >= :start AND l.success = false")
    Long countTodayFail(@Param("start") LocalDateTime start);

    @Query("SELECT COALESCE(AVG(l.costTime), 0) FROM InvokeLog l WHERE l.invokeTime >= :start")
    Double avgCostTimeToday(@Param("start") LocalDateTime start);

    // 最近24小时趋势（每小时）— H2 FORMATDATETIME
    @Query(value = "SELECT FORMATDATETIME(INVOKE_TIME, 'yyyy-MM-dd HH:00'), " +
           "COUNT(*), " +
           "SUM(CASE WHEN SUCCESS = true THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN SUCCESS = false THEN 1 ELSE 0 END) " +
           "FROM INVOKE_LOG WHERE INVOKE_TIME >= :start " +
           "GROUP BY FORMATDATETIME(INVOKE_TIME, 'yyyy-MM-dd HH:00') " +
           "ORDER BY 1", nativeQuery = true)
    List<Object[]> countHourlyTrend(@Param("start") LocalDateTime start);

    // 接口调用TOP排行（JPQL）
    @Query("SELECT l.apiCode, COUNT(l), " +
           "SUM(CASE WHEN l.success = true THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN l.success = false THEN 1 ELSE 0 END), " +
           "COALESCE(AVG(l.costTime), 0) " +
           "FROM InvokeLog l WHERE l.invokeTime >= :start " +
           "GROUP BY l.apiCode ORDER BY COUNT(l) DESC")
    List<Object[]> topApisByCalls(@Param("start") LocalDateTime start);

    // 最近7天每日统计 — H2 FORMATDATETIME
    @Query(value = "SELECT FORMATDATETIME(INVOKE_TIME, 'yyyy-MM-dd'), " +
           "COUNT(*), " +
           "SUM(CASE WHEN SUCCESS = true THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN SUCCESS = false THEN 1 ELSE 0 END), " +
           "COALESCE(AVG(COST_TIME), 0) " +
           "FROM INVOKE_LOG WHERE INVOKE_TIME >= :start " +
           "GROUP BY FORMATDATETIME(INVOKE_TIME, 'yyyy-MM-dd') " +
           "ORDER BY 1", nativeQuery = true)
    List<Object[]> countDailyTrend(@Param("start") LocalDateTime start);
}
