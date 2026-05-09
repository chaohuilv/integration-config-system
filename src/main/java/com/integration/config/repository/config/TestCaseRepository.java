package com.integration.config.repository.config;

import com.integration.config.entity.config.TestCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 测试用例 Repository
 */
@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    /**
     * 根据编码查询
     */
    Optional<TestCase> findByCode(String code);

    /**
     * 编码是否存在
     */
    boolean existsByCode(String code);

    /**
     * 根据编码更新（排除自身）
     */
    @Query("SELECT COUNT(t) > 0 FROM TestCase t WHERE t.code = :code AND t.id <> :id")
    boolean existsByCodeAndIdNot(@Param("code") String code, @Param("id") Long id);

    /**
     * 分页查询
     */
    @Query("SELECT t FROM TestCase t WHERE " +
           "(:groupName IS NULL OR t.groupName = :groupName) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:keyword IS NULL OR t.code LIKE %:keyword% OR t.name LIKE %:keyword%) AND " +
           "(:tags IS NULL OR t.tags LIKE %:tags%)")
    Page<TestCase> pageQuery(
            @Param("groupName") String groupName,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("tags") String tags,
            Pageable pageable);

    /**
     * 查询所有启用的用例
     */
    List<TestCase> findByStatus(com.integration.config.enums.Status status);

    /**
     * 按分组查询
     */
    List<TestCase> findByGroupName(String groupName);

    /**
     * 根据标签查询
     */
    List<TestCase> findByTagsContaining(String tag);
}