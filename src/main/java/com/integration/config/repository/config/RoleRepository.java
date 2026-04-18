package com.integration.config.repository.config;

import com.integration.config.entity.config.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色Repository
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * 根据角色编码查询
     */
    Optional<Role> findByCode(String code);

    /**
     * 根据状态查询角色列表
     */
    List<Role> findByStatus(String status);

    /**
     * 查询所有启用的角色
     */
    @Query("SELECT r FROM Role r WHERE r.status = 'ACTIVE' ORDER BY r.sortOrder")
    List<Role> findAllActive();

    /**
     * 根据用户ID查询角色列表
     */
    @Query("SELECT r FROM Role r JOIN UserRole ur ON r.id = ur.roleId WHERE ur.userId = :userId AND r.status = 'ACTIVE'")
    List<Role> findByUserId(@Param("userId") Long userId);

    /**
     * 根据接口ID查询角色列表
     */
    @Query("SELECT r FROM Role r JOIN ApiRole ar ON r.id = ar.roleId WHERE ar.apiId = :apiId AND r.status = 'ACTIVE'")
    List<Role> findByApiId(@Param("apiId") Long apiId);

    /**
     * 检查角色编码是否存在
     */
    boolean existsByCode(String code);

    /**
     * 检查角色编码是否存在（排除指定ID）
     */
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Role r WHERE r.code = :code AND r.id != :excludeId")
    boolean existsByCodeAndIdNot(@Param("code") String code, @Param("excludeId") Long excludeId);
}
