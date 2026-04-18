package com.integration.config.repository.config;

import com.integration.config.entity.config.ApiRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 接口-角色关联Repository
 */
@Repository
public interface ApiRoleRepository extends JpaRepository<ApiRole, Long> {

    /**
     * 根据接口ID查询角色关联
     */
    List<ApiRole> findByApiId(Long apiId);

    /**
     * 根据角色ID查询接口关联
     */
    List<ApiRole> findByRoleId(Long roleId);

    /**
     * 根据接口ID查询角色ID列表
     */
    @Query("SELECT ar.roleId FROM ApiRole ar WHERE ar.apiId = :apiId")
    List<Long> findRoleIdsByApiId(@Param("apiId") Long apiId);

    /**
     * 根据角色ID查询接口ID列表
     */
    @Query("SELECT ar.apiId FROM ApiRole ar WHERE ar.roleId = :roleId")
    List<Long> findApiIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除接口的角色关联
     */
    @Modifying
    @Query("DELETE FROM ApiRole ar WHERE ar.apiId = :apiId")
    void deleteByApiId(@Param("apiId") Long apiId);

    /**
     * 删除角色的接口关联
     */
    @Modifying
    @Query("DELETE FROM ApiRole ar WHERE ar.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 检查接口是否有指定角色
     */
    @Query("SELECT CASE WHEN COUNT(ar) > 0 THEN true ELSE false END FROM ApiRole ar WHERE ar.apiId = :apiId AND ar.roleId = :roleId")
    boolean existsByApiIdAndRoleId(@Param("apiId") Long apiId, @Param("roleId") Long roleId);

    /**
     * 检查用户是否可以访问指定接口
     * 通过用户的角色和接口的角色进行匹配
     */
    @Query("SELECT CASE WHEN COUNT(ar) > 0 THEN true ELSE false END FROM ApiRole ar " +
           "JOIN UserRole ur ON ar.roleId = ur.roleId " +
           "WHERE ar.apiId = :apiId AND ur.userId = :userId")
    boolean hasAccessToApi(@Param("userId") Long userId, @Param("apiId") Long apiId);

    /**
     * 查询用户可访问的所有接口ID
     */
    @Query("SELECT DISTINCT ar.apiId FROM ApiRole ar " +
           "JOIN UserRole ur ON ar.roleId = ur.roleId " +
           "WHERE ur.userId = :userId")
    List<Long> findApiIdsByUserId(@Param("userId") Long userId);
}
