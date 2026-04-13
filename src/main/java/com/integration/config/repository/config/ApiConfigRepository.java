package com.integration.config.repository.config;

import com.integration.config.entity.config.ApiConfig;
import com.integration.config.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 接口配置 Repository（Config 数据库）
 */
@Repository
public interface ApiConfigRepository extends JpaRepository<ApiConfig, Long> {

    Optional<ApiConfig> findByCode(String code);

    Optional<ApiConfig> findByCodeAndStatus(String code, Status status);

    boolean existsByCode(String code);

    @Query("SELECT a FROM ApiConfig a WHERE " +
           "(:keyword IS NULL OR a.name LIKE %:keyword% OR a.code LIKE %:keyword% OR a.description LIKE %:keyword%) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "ORDER BY a.createdAt DESC")
    Page<ApiConfig> findByKeywordAndStatus(
            @Param("keyword") String keyword,
            @Param("status") Status status,
            Pageable pageable);

    List<ApiConfig> findByStatusOrderByCreatedAtDesc(Status status);

    @Query("SELECT a.code FROM ApiConfig a WHERE a.status = :status")
    List<String> findAllCodesByStatus(@Param("status") Status status);
}
