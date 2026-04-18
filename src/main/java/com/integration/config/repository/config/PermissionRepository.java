package com.integration.config.repository.config;

import com.integration.config.entity.config.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 权限仓库
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);

    boolean existsByCode(String code);

    List<Permission> findByMenuId(Long menuId);

    List<Permission> findByType(String type);

    @Query("SELECT p FROM Permission p WHERE p.menuId IN (SELECT rm.menuId FROM RoleMenu rm WHERE rm.roleId = ?1)")
    List<Permission> findByRoleId(Long roleId);
}
