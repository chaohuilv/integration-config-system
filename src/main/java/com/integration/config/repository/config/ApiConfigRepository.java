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
           "AND (:latestVersion IS NULL OR a.latestVersion = :latestVersion) " +
           "AND (:deprecated IS NULL OR a.deprecated = :deprecated) " +
           "ORDER BY a.baseCode ASC, a.version DESC")
    Page<ApiConfig> findByConditions(
            @Param("keyword") String keyword,
            @Param("status") Status status,
            @Param("latestVersion") Boolean latestVersion,
            @Param("deprecated") Boolean deprecated,
            Pageable pageable);

    @Query("SELECT a FROM ApiConfig a WHERE " +
           "(:keyword IS NULL OR a.name LIKE %:keyword% OR a.code LIKE %:keyword% OR a.description LIKE %:keyword%) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "ORDER BY a.baseCode ASC, a.version DESC")
    Page<ApiConfig> findByKeywordAndStatus(
            @Param("keyword") String keyword,
            @Param("status") Status status,
            Pageable pageable);

    List<ApiConfig> findByStatusOrderByCreatedAtDesc(Status status);

    /** 按分组名称 + 创建时间排序 */
    List<ApiConfig> findByStatusOrderByGroupNameAscCreatedAtDesc(Status status);

    /** 按状态和分组名称筛选 */
    List<ApiConfig> findByStatusAndGroupNameOrderByCreatedAtDesc(Status status, String groupName);

    @Query("SELECT a.code FROM ApiConfig a WHERE a.status = :status")
    List<String> findAllCodesByStatus(@Param("status") Status status);

    // ==================== 版本控制查询 ====================

    /**
     * 查询某 baseCode 的所有版本（支持 baseCode 为 null）
     * 使用 JPQL 显式处理 null 值比较
     */
    @Query("SELECT a FROM ApiConfig a WHERE " +
           "(:baseCode IS NULL AND a.baseCode IS NULL OR a.baseCode = :baseCode) " +
           "ORDER BY a.version DESC")
    List<ApiConfig> findVersionsByBaseCode(@Param("baseCode") String baseCode);

    /** 查询某 baseCode 的所有版本（JPA 方法式，baseCode=null 查不到，需用上面的 JPQL） */
    List<ApiConfig> findByBaseCodeOrderByVersionDesc(String baseCode);

    /** 查询某 baseCode 的最新版本 */
    Optional<ApiConfig> findByBaseCodeAndLatestVersionTrue(String baseCode);

    /** 查询同一 baseCode 是否有 latestVersion=true 的版本 */
    boolean existsByBaseCodeAndLatestVersionTrue(String baseCode);

    /** 查询某 baseCode 指定版本 */
    Optional<ApiConfig> findByBaseCodeAndVersion(String baseCode, String version);

    /** 查询某 baseCode 最高版本号（如 v1/v2/v3 -> v3） */
    @Query("SELECT a FROM ApiConfig a WHERE a.baseCode = :baseCode ORDER BY a.version DESC")
    List<ApiConfig> findByBaseCodeOrderByVersionDescRaw(@Param("baseCode") String baseCode);
}
