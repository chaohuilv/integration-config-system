package com.integration.config.repository.config;

import com.integration.config.entity.config.Scenario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 场景配置 Repository
 */
@Repository
public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    /**
     * 根据编码查询
     */
    Optional<Scenario> findByCode(String code);

    /**
     * 编码是否存在
     */
    boolean existsByCode(String code);

    /**
     * 分页查询
     */
    @Query("SELECT s FROM Scenario s WHERE " +
           "(:groupName IS NULL OR s.groupName = :groupName) AND " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:keyword IS NULL OR s.code LIKE %:keyword% OR s.name LIKE %:keyword%)")
    Page<Scenario> pageQuery(
            @Param("groupName") String groupName,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * 查询所有启用的场景
     */
    List<Scenario> findByStatus(com.integration.config.enums.Status status);

    /**
     * 按分组查询
     */
    List<Scenario> findByGroupName(String groupName);
}
