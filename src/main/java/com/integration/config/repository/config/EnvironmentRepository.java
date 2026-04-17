package com.integration.config.repository.config;

import com.integration.config.entity.config.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, Long> {

    /**
     * 根据系统名称查询环境列表
     */
    List<Environment> findBySystemName(String systemName);

    /**
     * 根据系统名称和状态查询环境
     */
    List<Environment> findBySystemNameAndStatus(String systemName, String status);

    /**
     * 根据系统名称和环境名称查询
     */
    Optional<Environment> findBySystemNameAndEnvName(String systemName, String envName);

    /**
     * 分页查询，可按系统名称搜索
     */
    @Query("SELECT e FROM Environment e WHERE " +
           "(:systemName IS NULL OR e.systemName LIKE %:systemName%) " +
           "AND (:status IS NULL OR e.status = :status) " +
           "ORDER BY e.createdAt DESC")
    Page<Environment> findByConditions(
            @Param("systemName") String systemName,
            @Param("status") String status,
            Pageable pageable);

    /**
     * 获取所有系统名称（去重，用于下拉选择）
     */
    @Query("SELECT DISTINCT e.systemName FROM Environment e WHERE e.systemName IS NOT NULL AND e.systemName <> '' ORDER BY e.systemName")
    List<String> findAllSystemNames();

    /**
     * 获取所有启用的系统名称列表（用于下拉选择）
     */
    @Query("SELECT DISTINCT e.systemName FROM Environment e WHERE e.status = 'ACTIVE' AND e.systemName IS NOT NULL AND e.systemName <> '' ORDER BY e.systemName")
    List<String> findActiveSystemNames();

    /**
     * 获取指定系统下所有启用的环境
     */
    @Query("SELECT e FROM Environment e WHERE e.systemName = :systemName AND e.status = 'ACTIVE' ORDER BY e.envName")
    List<Environment> findActiveBySystemName(@Param("systemName") String systemName);
}