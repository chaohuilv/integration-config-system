package com.integration.config.service;

import com.integration.config.entity.config.Permission;
import com.integration.config.repository.config.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 权限服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

    private final PermissionRepository permissionRepository;

    /**
     * 获取所有权限
     */
    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }

    /**
     * 获取按钮权限
     */
    public List<Permission> getButtonPermissions() {
        return permissionRepository.findByType("BUTTON");
    }

    /**
     * 获取菜单权限
     */
    public List<Permission> getMenuPermissions() {
        return permissionRepository.findByType("MENU");
    }

    /**
     * 根据菜单获取权限
     */
    public List<Permission> getPermissionsByMenuId(Long menuId) {
        return permissionRepository.findByMenuId(menuId);
    }

    /**
     * 根据编码获取权限
     */
    public Optional<Permission> getPermissionByCode(String code) {
        return permissionRepository.findByCode(code);
    }

    /**
     * 创建权限
     */
    @Transactional
    public Permission createPermission(Permission permission) {
        if (permissionRepository.existsByCode(permission.getCode())) {
            throw new RuntimeException("权限编码已存在: " + permission.getCode());
        }
        return permissionRepository.save(permission);
    }

    /**
     * 批量创建权限
     */
    @Transactional
    public List<Permission> createPermissions(List<Permission> permissions) {
        return permissionRepository.saveAll(permissions);
    }

    /**
     * 删除权限
     */
    @Transactional
    public void deletePermission(Long id) {
        permissionRepository.deleteById(id);
    }
}
