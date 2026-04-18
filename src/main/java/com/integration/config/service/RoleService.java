package com.integration.config.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.integration.config.entity.config.Role;
import com.integration.config.entity.config.UserRole;
import com.integration.config.entity.config.ApiRole;
import com.integration.config.entity.config.RoleMenu;
import com.integration.config.entity.config.RolePermission;
import com.integration.config.entity.config.Permission;
import com.integration.config.repository.config.RoleRepository;
import com.integration.config.repository.config.UserRoleRepository;
import com.integration.config.repository.config.ApiRoleRepository;
import com.integration.config.repository.config.RoleMenuRepository;
import com.integration.config.repository.config.RolePermissionRepository;
import com.integration.config.repository.config.PermissionRepository;
import com.integration.config.repository.config.ApiConfigRepository;
import com.integration.config.entity.config.ApiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 角色服务
 * 管理角色、用户角色关联、接口角色关联、菜单权限关联
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ApiRoleRepository apiRoleRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final ApiConfigRepository apiConfigRepository;

    // ==================== 角色管理 ====================

    /**
     * 获取所有启用的角色
     */
    public List<Role> getAllActiveRoles() {
        return roleRepository.findAllActive();
    }

    /**
     * 获取所有角色
     */
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    /**
     * 根据ID获取角色
     */
    public Optional<Role> getRoleById(Long id) {
        return roleRepository.findById(id);
    }

    /**
     * 根据编码获取角色
     */
    public Optional<Role> getRoleByCode(String code) {
        return roleRepository.findByCode(code);
    }

    /**
     * 创建角色
     */
    @Transactional
    public Role createRole(Role role) {
        if (roleRepository.existsByCode(role.getCode())) {
            throw new RuntimeException("角色编码已存在: " + role.getCode());
        }
        return roleRepository.save(role);
    }

    /**
     * 更新角色
     */
    @Transactional
    public Role updateRole(Long id, Role role) {
        Role existing = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("角色不存在: " + id));

        // 检查编码是否重复
        if (roleRepository.existsByCodeAndIdNot(role.getCode(), id)) {
            throw new RuntimeException("角色编码已存在: " + role.getCode());
        }

        existing.setCode(role.getCode());
        existing.setName(role.getName());
        existing.setDescription(role.getDescription());
        existing.setStatus(role.getStatus());
        existing.setSortOrder(role.getSortOrder());

        return roleRepository.save(existing);
    }

    /**
     * 删除角色
     */
    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("角色不存在: " + id));

        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new RuntimeException("系统预置角色不能删除: " + role.getCode());
        }

        // 删除关联
        userRoleRepository.deleteByRoleId(id);
        apiRoleRepository.deleteByRoleId(id);

        roleRepository.deleteById(id);
    }

    // ==================== 用户角色关联 ====================

    /**
     * 获取用户的角色列表
     */
    public List<Role> getUserRoles(Long userId) {
        return roleRepository.findByUserId(userId);
    }

    /**
     * 获取用户的角色ID列表
     */
    public List<Long> getUserRoleIds(Long userId) {
        return userRoleRepository.findRoleIdsByUserId(userId);
    }

    /**
     * 获取角色下的用户ID列表
     */
    public List<Long> getRoleUserIds(Long roleId) {
        return userRoleRepository.findUserIdsByRoleId(roleId);
    }

    /**
     * 设置角色的用户列表（覆盖原有）
     */
    @Transactional
    public void setRoleUsers(Long roleId, List<Long> userIds) {
        // 删除原有的用户关联
        userRoleRepository.deleteByRoleId(roleId);

        // 添加新的用户关联
        for (Long userId : userIds) {
            UserRole ur = UserRole.builder()
                    .userId(userId)
                    .roleId(roleId)
                    .build();
            userRoleRepository.save(ur);
        }
    }

    /**
     * 设置用户的角色（覆盖原有）
     */
    @Transactional
    public void setUserRoles(Long userId, List<Long> roleIds) {
        // 删除原有角色
        userRoleRepository.deleteByUserId(userId);

        // 添加新角色
        for (Long roleId : roleIds) {
            UserRole ur = UserRole.builder()
                    .userId(userId)
                    .roleId(roleId)
                    .build();
            userRoleRepository.save(ur);
        }
    }

    /**
     * 检查用户是否有指定角色
     */
    public boolean userHasRole(Long userId, String roleCode) {
        Optional<Role> role = roleRepository.findByCode(roleCode);
        if (role.isEmpty()) {
            log.warn("[RoleService] 角色不存在: {}", roleCode);
            return false;
        }
        boolean hasRole = userRoleRepository.existsByUserIdAndRoleId(userId, role.get().getId());
        log.info("[RoleService] userHasRole检查: userId={}, roleCode={}, roleId={}, hasRole={}", 
                userId, roleCode, role.get().getId(), hasRole);
        return hasRole;
    }

    /**
     * 检查用户是否是管理员
     */
    public boolean isAdmin(Long userId) {
        boolean admin = userHasRole(userId, "ADMIN");
        log.info("[RoleService] isAdmin检查: userId={}, result={}", userId, admin);
        return admin;
    }

    // ==================== 接口角色关联 ====================

    /**
     * 获取接口的角色列表
     */
    public List<Role> getApiRoles(Long apiId) {
        return roleRepository.findByApiId(apiId);
    }

    /**
     * 获取接口的角色ID列表
     */
    public List<Long> getApiRoleIds(Long apiId) {
        return apiRoleRepository.findRoleIdsByApiId(apiId);
    }

    /**
     * 获取角色的接口ID列表
     */
    public List<Long> getRoleApiIds(Long roleId) {
        return apiRoleRepository.findApiIdsByRoleId(roleId);
    }

    /**
     * 设置角色的接口列表（覆盖原有）
     */
    @Transactional
    public void setRoleApis(Long roleId, List<Long> apiIds, Long createdBy) {
        // 删除原有的接口关联
        apiRoleRepository.deleteByRoleId(roleId);

        // 添加新的接口关联
        for (Long apiId : apiIds) {
            ApiRole ar = ApiRole.builder()
                    .apiId(apiId)
                    .roleId(roleId)
                    .createdBy(createdBy)
                    .build();
            apiRoleRepository.save(ar);
        }
    }

    /**
     * 设置接口的角色（覆盖原有）
     */
    @Transactional
    public void setApiRoles(Long apiId, List<Long> roleIds, Long createdBy) {
        // 删除原有角色
        apiRoleRepository.deleteByApiId(apiId);

        // 添加新角色
        for (Long roleId : roleIds) {
            ApiRole ar = ApiRole.builder()
                    .apiId(apiId)
                    .roleId(roleId)
                    .createdBy(createdBy)
                    .build();
            apiRoleRepository.save(ar);
        }
    }

    /**
     * 检查用户是否有权限访问接口
     * 
     * @param userId 用户ID
     * @param apiId 接口ID
     * @return true=有权限，false=无权限
     */
    public boolean hasApiAccess(Long userId, Long apiId) {
        // 管理员拥有所有权限
        if (isAdmin(userId)) {
            return true;
        }

        // 检查接口是否配置了角色限制
        List<Long> apiRoleIds = apiRoleRepository.findRoleIdsByApiId(apiId);
        
        // 如果接口没有配置任何角色，则所有人都不可以访问（开放接口）
        if (apiRoleIds.isEmpty()) {
            return false;
        }

        // 检查用户是否有接口所需的任一角色
        return apiRoleRepository.hasAccessToApi(userId, apiId);
    }

    /**
     * 检查用户是否有权限访问接口（通过接口编码）
     */
    public boolean hasApiAccess(Long userId, String apiCode) {
        ApiConfig apiConfig = apiConfigRepository.findByCode(apiCode)
                .orElse(null);
        if (apiConfig == null) {
            return false;
        }
        return hasApiAccess(userId, apiConfig.getId());
    }

    /**
     * 获取用户可访问的所有接口ID
     */
    public List<Long> getUserAccessibleApiIds(Long userId) {
        if (isAdmin(userId)) {
            // 管理员可访问所有接口
            return apiConfigRepository.findAll().stream()
                    .map(ApiConfig::getId)
                    .toList();
        }
        return apiRoleRepository.findApiIdsByUserId(userId);
    }

    // ==================== 初始化预置角色 ====================

    /**
     * 初始化系统预置角色（从 JSON 文件读取）
     * 如果角色已存在则更新，不存在则创建
     */
    @Transactional
    public void initSystemRoles() {
        log.info("[RoleService] 从 JSON 初始化系统预置角色...");

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ClassPathResource resource = new ClassPathResource("config/role-config.json");
            
            if (!resource.exists()) {
                log.warn("[RoleService] 配置文件 config/role-config.json 不存在，使用默认角色");
                initDefaultRoles();
                return;
            }

            Map<String, Object> config = objectMapper.readValue(resource.getInputStream(), Map.class);
            List<Map<String, Object>> roles = (List<Map<String, Object>>) config.get("roles");

            int created = 0, updated = 0;
            for (Map<String, Object> roleDef : roles) {
                String code = (String) roleDef.get("code");
                Optional<Role> existing = roleRepository.findByCode(code);

                if (existing.isPresent()) {
                    // 更新现有角色
                    Role role = existing.get();
                    role.setName((String) roleDef.get("name"));
                    role.setDescription((String) roleDef.get("description"));
                    role.setIsSystem((Boolean) roleDef.get("isSystem"));
                    role.setSortOrder(roleDef.get("sortOrder") != null ? ((Number) roleDef.get("sortOrder")).intValue() : 0);
                    role.setStatus("ACTIVE"); // 确保状态为 ACTIVE
                    roleRepository.save(role);
                    updated++;
                } else {
                    // 创建新角色
                    Role role = Role.builder()
                            .code(code)
                            .name((String) roleDef.get("name"))
                            .description((String) roleDef.get("description"))
                            .isSystem((Boolean) roleDef.get("isSystem"))
                            .sortOrder(roleDef.get("sortOrder") != null ? ((Number) roleDef.get("sortOrder")).intValue() : 0)
                            .build();
                    roleRepository.save(role);
                    created++;
                }
            }

            log.info("[RoleService] 系统预置角色初始化完成: 新增 {} 个, 更新 {} 个", created, updated);

        } catch (Exception e) {
            log.error("[RoleService] 读取角色配置失败: {}", e.getMessage());
            initDefaultRoles();
        }
    }

    /**
     * 默认角色初始化（配置文件不存在时使用）
     */
    private void initDefaultRoles() {
        log.info("[RoleService] 使用默认角色初始化...");

        // 管理员
        createOrUpdateRole("ADMIN", "管理员", "拥有所有权限，可管理用户、角色、接口配置", true, 1);
        // 开发者
        createOrUpdateRole("DEVELOPER", "开发者", "可创建、编辑、调用接口配置", true, 2);
        // 只读
        createOrUpdateRole("READONLY", "只读", "只能查看和调用已授权的接口", true, 3);

        log.info("[RoleService] 系统预置角色初始化完成");
    }

    /**
     * 创建或更新角色
     */
    private void createOrUpdateRole(String code, String name, String description, boolean isSystem, int sortOrder) {
        Optional<Role> existing = roleRepository.findByCode(code);
        if (existing.isPresent()) {
            Role role = existing.get();
            role.setName(name);
            role.setDescription(description);
            role.setIsSystem(isSystem);
            role.setSortOrder(sortOrder);
            role.setStatus("ACTIVE");
            roleRepository.save(role);
        } else {
            Role role = Role.builder()
                    .code(code)
                    .name(name)
                    .description(description)
                    .isSystem(isSystem)
                    .sortOrder(sortOrder)
                    .build();
            roleRepository.save(role);
        }
    }

    // ==================== 角色菜单关联 ====================

    /**
     * 获取角色的菜单ID列表
     */
    public List<Long> getRoleMenuIds(Long roleId) {
        return roleMenuRepository.findMenuIdsByRoleId(roleId);
    }

    /**
     * 设置角色的菜单（覆盖原有）
     */
    @Transactional
    public void setRoleMenus(Long roleId, List<Long> menuIds) {
        // 删除原有菜单关联
        roleMenuRepository.deleteByRoleId(roleId);

        // 添加新菜单关联
        for (Long menuId : menuIds) {
            RoleMenu rm = RoleMenu.builder()
                    .roleId(roleId)
                    .menuId(menuId)
                    .build();
            roleMenuRepository.save(rm);
        }
    }

    // ==================== 角色权限关联 ====================

    /**
     * 获取角色的权限ID列表
     */
    public List<Long> getRolePermissionIds(Long roleId) {
        return rolePermissionRepository.findPermissionIdsByRoleId(roleId);
    }

    /**
     * 获取角色的权限编码列表
     */
    public List<String> getRolePermissionCodes(Long roleId) {
        return rolePermissionRepository.findPermissionCodesByRoleId(roleId);
    }

    /**
     * 获取用户的权限编码列表（通过所有角色汇总）
     */
    public List<String> getUserPermissionCodes(Long userId) {
        return rolePermissionRepository.findPermissionCodesByUserId(userId);
    }

    /**
     * 设置角色的权限（覆盖原有）
     */
    @Transactional
    public void setRolePermissions(Long roleId, List<Long> permissionIds) {
        // 删除原有权限关联
        rolePermissionRepository.deleteByRoleId(roleId);

        // 添加新权限关联
        for (Long permissionId : permissionIds) {
            RolePermission rp = RolePermission.builder()
                    .roleId(roleId)
                    .permissionId(permissionId)
                    .build();
            rolePermissionRepository.save(rp);
        }
    }

    /**
     * 检查用户是否有指定权限
     */
    public boolean hasPermission(Long userId, String permissionCode) {
        // 管理员拥有所有权限
        if (isAdmin(userId)) {
            return true;
        }
        
        List<String> permissions = getUserPermissionCodes(userId);
        return permissions.contains(permissionCode);
    }

    /**
     * 给 ADMIN 角色分配所有权限
     * 用于初始化或修复权限数据
     */
    @Transactional
    public void assignAllPermissionsToAdmin() {
        Role adminRole = roleRepository.findByCode("ADMIN")
                .orElseThrow(() -> new RuntimeException("ADMIN 角色不存在"));

        List<Long> allPermissionIds = permissionRepository.findAll().stream()
                .map(Permission::getId)
                .toList();

        setRolePermissions(adminRole.getId(), allPermissionIds);
        log.info("[RoleService] ADMIN 角色已分配所有 {} 个权限", allPermissionIds.size());
    }
}
