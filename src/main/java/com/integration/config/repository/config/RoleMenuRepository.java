package com.integration.config.repository.config;

import com.integration.config.entity.config.RoleMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色菜单关联仓库
 */
@Repository
public interface RoleMenuRepository extends JpaRepository<RoleMenu, Long> {

    List<RoleMenu> findByRoleId(Long roleId);

    List<RoleMenu> findByMenuId(Long menuId);

    @Query("SELECT rm.menuId FROM RoleMenu rm WHERE rm.roleId = ?1")
    List<Long> findMenuIdsByRoleId(Long roleId);

    @Query("SELECT rm.roleId FROM RoleMenu rm WHERE rm.menuId = ?1")
    List<Long> findRoleIdsByMenuId(Long menuId);

    @Modifying
    @Query("DELETE FROM RoleMenu rm WHERE rm.roleId = ?1")
    void deleteByRoleId(Long roleId);

    @Modifying
    @Query("DELETE FROM RoleMenu rm WHERE rm.menuId = ?1")
    void deleteByMenuId(Long menuId);

    boolean existsByRoleIdAndMenuId(Long roleId, Long menuId);

    @Query("SELECT m FROM RoleMenu m WHERE m.roleId = ?1")
    List<RoleMenu> findAllByRoleId(Long roleId);

    /**
     * 通过多个角色ID查询菜单ID列表
     */
    @Query("SELECT DISTINCT rm.menuId FROM RoleMenu rm WHERE rm.roleId IN ?1")
    List<Long> findMenuIdsByRoleIds(List<Long> roleIds);
}
