package com.integration.config.repository.config;

import com.integration.config.entity.config.MockConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MockConfigRepository extends JpaRepository<MockConfig, Long> {

    Optional<MockConfig> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<MockConfig> findByEnabledTrueOrderByPriorityAsc();

    List<MockConfig> findByEnabledTrueAndPathAndMethodOrderByPriorityAsc(String path, String method);

    @Query("SELECT m FROM MockConfig m WHERE " +
           "(:groupName IS NULL OR m.groupName = :groupName) AND " +
           "(:enabled IS NULL OR m.enabled = :enabled) AND " +
           "(:keyword IS NULL OR m.name LIKE %:keyword% OR m.code LIKE %:keyword% OR m.path LIKE %:keyword%)")
    Page<MockConfig> pageQuery(@Param("groupName") String groupName,
                               @Param("enabled") Boolean enabled,
                               @Param("keyword") String keyword,
                               Pageable pageable);

    @Query("SELECT DISTINCT m.groupName FROM MockConfig m WHERE m.groupName IS NOT NULL ORDER BY m.groupName")
    List<String> findAllGroupNames();

    @Query("SELECT COUNT(m) FROM MockConfig m WHERE m.enabled = true")
    long countEnabled();

    /**
     * 查找所有启用的 Mock 配置（按优先级排序），供 Servlet 使用
     */
    @Query("SELECT m FROM MockConfig m WHERE m.enabled = true ORDER BY m.priority ASC, m.id ASC")
    List<MockConfig> findAllEnabledOrderByPriority();
}
