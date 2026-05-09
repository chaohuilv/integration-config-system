package com.integration.config.repository.config;

import com.integration.config.entity.config.TestSuite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 测试套件 Repository
 */
@Repository
public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {

    /**
     * 根据编码查询
     */
    Optional<TestSuite> findByCode(String code);

    /**
     * 编码是否存在
     */
    boolean existsByCode(String code);

    /**
     * 分页查询
     */
    @Query("SELECT t FROM TestSuite t WHERE " +
           "(:groupName IS NULL OR t.groupName = :groupName) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:keyword IS NULL OR t.code LIKE %:keyword% OR t.name LIKE %:keyword%)")
    Page<TestSuite> pageQuery(
            @Param("groupName") String groupName,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * 查询所有启用的套件
     */
    List<TestSuite> findByStatus(com.integration.config.enums.Status status);
}