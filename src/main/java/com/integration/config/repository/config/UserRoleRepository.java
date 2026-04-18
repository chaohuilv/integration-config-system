package com.integration.config.repository.config;

import com.integration.config.entity.config.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户-角色关联Repository
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /**
     * 根据用户ID查询角色关联
     */
    List<UserRole> findByUserId(Long userId);

    /**
     * 根据角色ID查询用户关联
     */
    List<UserRole> findByRoleId(Long roleId);

    /**
     * 根据用户ID查询角色ID列表
     */
    @Query("SELECT ur.roleId FROM UserRole ur WHERE ur.userId = :userId")
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 根据角色ID查询用户ID列表
     */
    @Query("SELECT ur.userId FROM UserRole ur WHERE ur.roleId = :roleId")
    List<Long> findUserIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除用户的角色关联
     */
    @Modifying
    @Query("DELETE FROM UserRole ur WHERE ur.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * 删除角色的用户关联
     */
    @Modifying
    @Query("DELETE FROM UserRole ur WHERE ur.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 检查用户是否有指定角色
     */
    @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UserRole ur WHERE ur.userId = :userId AND ur.roleId = :roleId")
    boolean existsByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
