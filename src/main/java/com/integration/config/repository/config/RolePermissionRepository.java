package com.integration.config.repository.config;

import com.integration.config.entity.config.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色权限关联仓库
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRoleId(Long roleId);

    List<RolePermission> findByPermissionId(Long permissionId);

    @Query("SELECT rp.permissionId FROM RolePermission rp WHERE rp.roleId = ?1")
    List<Long> findPermissionIdsByRoleId(Long roleId);

    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.roleId = ?1")
    void deleteByRoleId(Long roleId);

    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.permissionId = ?1")
    void deleteByPermissionId(Long permissionId);

    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

    @Query("SELECT p.code FROM RolePermission rp JOIN Permission p ON rp.permissionId = p.id WHERE rp.roleId = ?1")
    List<String> findPermissionCodesByRoleId(Long roleId);

    @Query("SELECT DISTINCT p.code FROM RolePermission rp " +
           "JOIN Permission p ON rp.permissionId = p.id " +
           "JOIN UserRole ur ON rp.roleId = ur.roleId " +
           "WHERE ur.userId = ?1")
    List<String> findPermissionCodesByUserId(Long userId);
}
