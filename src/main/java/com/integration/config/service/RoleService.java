package com.integration.config.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.integration.config.dto.PageResult;
import com.integration.config.dto.RoleDTO;
import com.integration.config.entity.config.Role;
import com.integration.config.entity.config.UserRole;
import com.integration.config.entity.config.ApiRole;
import com.integration.config.entity.config.RoleMenu;
import com.integration.config.entity.config.RolePermission;
import com.integration.config.entity.config.Permission;
import com.integration.config.enums.AppConstants;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
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

    /** Redis Key 前缀：用户权限缓存 */
    private static final String CACHE_PREFIX = AppConstants.REDIS_USER_PERMISSIONS_PREFIX;
    /** Redis Key 前缀：用户角色缓存（用于 isAdmin 判断） */
    private static final String CACHE_ROLE_PREFIX = AppConstants.REDIS_USER_ROLES_PREFIX;
    /** Redis Key 前缀：用户可访问接口ID缓存 */
    private static final String CACHE_API_PREFIX = AppConstants.REDIS_USER_APIS_PREFIX;
    /** 缓存过期时间（秒） */
    private static final int CACHE_TTL = AppConstants.CACHE_TTL_SECONDS;

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ApiRoleRepository apiRoleRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final ApiConfigRepository apiConfigRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

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
     * 分页查询角色（含统计信息）
     */
    public PageResult<RoleDTO> getRolePage(String keyword, int page, int size) {
        Page<Role> rolePage = roleRepository.findPage(keyword, PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "sortOrder")));

        List<RoleDTO> dtos = rolePage.getContent().stream().map(role -> {
            int userCount = userRoleRepository.findByRoleId(role.getId()).size();
            int apiCount = apiRoleRepository.findByRoleId(role.getId()).size();
            int menuCount = roleMenuRepository.findByRoleId(role.getId()).size();
            int permCount = rolePermissionRepository.findByRoleId(role.getId()).size();

            return RoleDTO.builder()
                    .id(role.getId())
                    .name(role.getName())
                    .code(role.getCode())
                    .description(role.getDescription())
                    .status(role.getStatus())
                    .sortOrder(role.getSortOrder())
                    .isSystem(role.getIsSystem())
                    .userCount(userCount)
                    .apiCount(apiCount)
                    .menuCount(menuCount)
                    .permissionCount(permCount)
                    .build();
        }).toList();

        return PageResult.of(dtos, rolePage.getTotalElements(), page, size);
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
     * 权限变更后清除该角色下所有用户的缓存
     */
    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("角色不存在: " + id));

        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new RuntimeException("系统预置角色不能删除: " + role.getCode());
        }

        // 记录该角色下的用户，用于清除缓存
        List<Long> userIds = userRoleRepository.findUserIdsByRoleId(id);

        // 删除关联
        userRoleRepository.deleteByRoleId(id);
        apiRoleRepository.deleteByRoleId(id);

        roleRepository.deleteById(id);

        // 清除所有受影响用户的缓存
        userIds.forEach(this::clearUserPermissionCache);
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
     * 角色成员变更后，清除被移除和新加入的所有用户的权限缓存
     */
    @Transactional
    public void setRoleUsers(Long roleId, List<Long> userIds) {
        // 记录原有用户列表，用于清除缓存
        List<Long> oldUserIds = userRoleRepository.findUserIdsByRoleId(roleId);

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

        // 清除所有受影响用户的缓存（旧用户 + 新用户）
        java.util.Set<Long> affectedIds = new java.util.HashSet<>(oldUserIds);
        affectedIds.addAll(userIds);
        affectedIds.forEach(this::clearUserPermissionCache);
    }

    /**
     * 设置用户的角色（覆盖原有）
     * 权限变更后清除该用户的权限缓存
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

        // 清除该用户的权限和角色缓存
        clearUserPermissionCache(userId);
    }

    /**
     * 检查用户是否有指定角色
     * 先查 Redis 缓存，miss 时查 DB 并回写缓存
     */
    public boolean userHasRole(Long userId, String roleCode) {
        Optional<Role> role = roleRepository.findByCode(roleCode);
        if (role.isEmpty()) {
            log.warn("[RoleService] 角色不存在: {}", roleCode);
            return false;
        }

        Long roleId = role.get().getId();
        List<Long> cachedRoleIds = getCachedUserRoles(userId);
        boolean hasRole = cachedRoleIds.contains(roleId);
        log.debug("[RoleService] userHasRole: userId={}, roleCode={}, roleId={}, hasRole={}, fromCache={}", 
                userId, roleCode, roleId, hasRole, !cachedRoleIds.isEmpty());
        return hasRole;
    }

    /**
     * 检查用户是否是管理员
     */
    public boolean isAdmin(Long userId) {
        boolean admin = userHasRole(userId, AppConstants.ROLE_ADMIN_CODE);
        log.debug("[RoleService] isAdmin: userId={}, result={}", userId, admin);
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
     * 接口权限变更后清除该角色下所有用户的接口缓存
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

        // 清除该角色下所有用户的接口缓存
        clearRoleUsersApiCache(roleId);
    }

    /**
     * 设置接口的角色（覆盖原有）
     * 接口权限变更后清除该接口关联的所有角色下的用户缓存
     */
    @Transactional
    public void setApiRoles(Long apiId, List<Long> roleIds, Long createdBy) {
        // 记录旧的角色列表，用于清除缓存
        List<Long> oldRoleIds = apiRoleRepository.findRoleIdsByApiId(apiId);

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

        // 清除旧+新角色下所有用户的接口缓存
        java.util.Set<Long> affectedRoleIds = new java.util.HashSet<>(oldRoleIds);
        affectedRoleIds.addAll(roleIds);
        for (Long roleId : affectedRoleIds) {
            clearRoleUsersApiCache(roleId);
        }
    }

    /**
     * 检查用户是否有权限访问接口
     * 使用 Redis 缓存，避免每次查 DB
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

        // 从缓存获取用户可访问的 API ID 集合
        java.util.Set<Long> accessibleIds = getCachedUserApiIds(userId);
        return accessibleIds.contains(apiId);
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
     * 使用 Redis 缓存，避免每次查 DB
     */
    public List<Long> getUserAccessibleApiIds(Long userId) {
        if (isAdmin(userId)) {
            // 管理员可访问所有接口
            return apiConfigRepository.findAll().stream()
                    .map(ApiConfig::getId)
                    .toList();
        }
        return new java.util.ArrayList<>(getCachedUserApiIds(userId));
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
                    role.setStatus(AppConstants.USER_STATUS_ACTIVE); // 确保状态为 ACTIVE
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
        createOrUpdateRole(AppConstants.ROLE_ADMIN_CODE, "管理员", "拥有所有权限，可管理用户、角色、接口配置", true, 1);
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
            role.setStatus(AppConstants.USER_STATUS_ACTIVE);
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
     * 菜单变更后清除该角色下所有用户的缓存
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

        // 清除该角色下所有用户的缓存
        clearRoleUsersPermissionCache(roleId);
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
     * 先查 Redis 缓存，miss 时查 DB 并回写
     */
    public List<String> getUserPermissionCodes(Long userId) {
        // 1. 先查 Redis 缓存
        List<String> cached = getCachedUserPermissions(userId);
        if (cached != null) {
            log.debug("[RoleService] getUserPermissionCodes: userId={}, 命中缓存, size={}", userId, cached.size());
            return cached;
        }

        // 2. 缓存未命中，查数据库
        List<String> permissions = rolePermissionRepository.findPermissionCodesByUserId(userId);
        log.debug("[RoleService] getUserPermissionCodes: userId={}, 查DB, size={}", userId, permissions.size());

        // 3. 回写缓存
        setUserPermissionCache(userId, permissions);
        return permissions;
    }

    /**
     * 设置角色的权限（覆盖原有）
     * 权限变更后清除该角色下所有用户的缓存
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

        // 清除该角色下所有用户的缓存
        clearRoleUsersPermissionCache(roleId);
    }

    /**
     * 检查用户是否有指定权限
     * 使用 Redis 缓存，避免每次查 DB
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
     * 权限变更后清除 ADMIN 角色下所有用户的缓存
     */
    @Transactional
    public void assignAllPermissionsToAdmin() {
        Role adminRole = roleRepository.findByCode(AppConstants.ROLE_ADMIN_CODE)
                .orElseThrow(() -> new RuntimeException("ADMIN 角色不存在"));

        List<Long> allPermissionIds = permissionRepository.findAll().stream()
                .map(Permission::getId)
                .toList();

        setRolePermissions(adminRole.getId(), allPermissionIds);
        log.info("[RoleService] ADMIN 角色已分配所有 {} 个权限", allPermissionIds.size());
    }

    // ==================== Redis 缓存方法 ====================

    /**
     * 从 Redis 获取用户的权限编码缓存
     * @return 缓存的权限列表，null 表示缓存未命中
     */
    private List<String> getCachedUserPermissions(Long userId) {
        try {
            String key = CACHE_PREFIX + userId;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {
            log.warn("[RoleService] 读取用户权限缓存失败: userId={}, error={}", userId, e.getMessage());
        }
        return null;
    }

    /**
     * 将用户权限编码写入 Redis 缓存
     */
    private void setUserPermissionCache(Long userId, List<String> permissions) {
        try {
            String key = CACHE_PREFIX + userId;
            String json = objectMapper.writeValueAsString(permissions);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(CACHE_TTL));
            log.debug("[RoleService] 写入用户权限缓存: userId={}, size={}, ttl={}s", userId, permissions.size(), CACHE_TTL);
        } catch (Exception e) {
            log.warn("[RoleService] 写入用户权限缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 从 Redis 获取用户的角色ID缓存
     * @return 缓存的角色ID列表，空列表表示缓存命中但用户无角色
     */
    private List<Long> getCachedUserRoles(Long userId) {
        try {
            String key = CACHE_ROLE_PREFIX + userId;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
            }
        } catch (Exception e) {
            log.warn("[RoleService] 读取用户角色缓存失败: userId={}, error={}", userId, e.getMessage());
        }
        // 缓存未命中，查 DB 并回写
        List<Long> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        try {
            String key = CACHE_ROLE_PREFIX + userId;
            String json = objectMapper.writeValueAsString(roleIds);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(CACHE_TTL));
        } catch (Exception e) {
            log.warn("[RoleService] 写入用户角色缓存失败: userId={}, error={}", userId, e.getMessage());
        }
        return roleIds;
    }

    /**
     * 清除指定用户的权限、角色、接口缓存
     */
    private void clearUserPermissionCache(Long userId) {
        try {
            redisTemplate.delete(CACHE_PREFIX + userId);
            redisTemplate.delete(CACHE_ROLE_PREFIX + userId);
            redisTemplate.delete(CACHE_API_PREFIX + userId);
            redisTemplate.delete(AppConstants.REDIS_USER_MENUS_PREFIX + userId);
            log.debug("[RoleService] 已清除用户缓存: userId={}", userId);
        } catch (Exception e) {
            log.warn("[RoleService] 清除用户缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 清除指定角色下所有用户的缓存（权限+角色+接口）
     */
    private void clearRoleUsersPermissionCache(Long roleId) {
        try {
            List<Long> userIds = userRoleRepository.findUserIdsByRoleId(roleId);
            for (Long userId : userIds) {
                clearUserPermissionCache(userId);
            }
            log.debug("[RoleService] 已清除角色下所有用户缓存: roleId={}, userCount={}", roleId, userIds.size());
        } catch (Exception e) {
            log.warn("[RoleService] 清除角色用户缓存失败: roleId={}, error={}", roleId, e.getMessage());
        }
    }

    // ==================== 接口权限 Redis 缓存 ====================

    /**
     * 从 Redis 获取用户可访问的 API ID 集合
     * miss 时查 DB 并回写缓存
     */
    private java.util.Set<Long> getCachedUserApiIds(Long userId) {
        try {
            String key = CACHE_API_PREFIX + userId;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                List<Long> ids = objectMapper.readValue(json, new TypeReference<List<Long>>() {});
                log.debug("[RoleService] 用户接口缓存命中: userId={}, size={}", userId, ids.size());
                return new java.util.HashSet<>(ids);
            }
        } catch (Exception e) {
            log.warn("[RoleService] 读取用户接口缓存失败: userId={}, error={}", userId, e.getMessage());
        }

        // 缓存未命中，查 DB 并回写
        List<Long> apiIds = apiRoleRepository.findApiIdsByUserId(userId);
        try {
            String key = CACHE_API_PREFIX + userId;
            String json = objectMapper.writeValueAsString(apiIds);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(CACHE_TTL));
            log.debug("[RoleService] 写入用户接口缓存: userId={}, size={}, ttl={}s", userId, apiIds.size(), CACHE_TTL);
        } catch (Exception e) {
            log.warn("[RoleService] 写入用户接口缓存失败: userId={}, error={}", userId, e.getMessage());
        }
        return new java.util.HashSet<>(apiIds);
    }

    /**
     * 清除指定用户的接口权限缓存
     */
    private void clearUserApiCache(Long userId) {
        try {
            redisTemplate.delete(CACHE_API_PREFIX + userId);
            log.debug("[RoleService] 已清除用户接口缓存: userId={}", userId);
        } catch (Exception e) {
            log.warn("[RoleService] 清除用户接口缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 清除指定角色下所有用户的接口缓存
     */
    private void clearRoleUsersApiCache(Long roleId) {
        try {
            List<Long> userIds = userRoleRepository.findUserIdsByRoleId(roleId);
            for (Long userId : userIds) {
                clearUserApiCache(userId);
            }
            log.debug("[RoleService] 已清除角色下所有用户的接口缓存: roleId={}, userCount={}", roleId, userIds.size());
        } catch (Exception e) {
            log.warn("[RoleService] 清除角色用户接口缓存失败: roleId={}, error={}", roleId, e.getMessage());
        }
    }
}
