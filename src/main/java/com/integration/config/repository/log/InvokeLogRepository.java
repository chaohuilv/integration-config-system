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
           "ORDER BY l.invokeTime DESC")
    Page<InvokeLog> findByConditions(
            @Param("apiCode") String apiCode,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("success") Boolean success,
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
}
