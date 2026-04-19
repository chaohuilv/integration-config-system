package com.integration.config.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.integration.config.entity.config.Menu;
import com.integration.config.entity.config.Permission;
import com.integration.config.entity.config.Role;
import com.integration.config.entity.config.User;
import com.integration.config.entity.config.UserRole;
import com.integration.config.enums.AppConstants;
import com.integration.config.repository.config.MenuRepository;
import com.integration.config.repository.config.PermissionRepository;
import com.integration.config.repository.config.RoleRepository;
import com.integration.config.repository.config.UserRepository;
import com.integration.config.repository.config.UserRoleRepository;
import com.integration.config.service.RoleService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 数据初始化器
 * 应用启动时从 JSON 配置文件动态初始化菜单、权限、角色数据
 * 支持增量更新：按 code 唯一校验，不存在则创建，存在则更新
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleService roleService;
    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) {
        log.info("[DataInitializer] 开始初始化系统数据...");
        
        // 从 JSON 初始化菜单
        initMenusFromJson();
        
        // 从 JSON 初始化权限
        initPermissionsFromJson();
        
        // 从 JSON 初始化角色
        initRolesFromJson();
        
        // 初始化管理员账户
        initAdminUserFromJson();
        
        log.info("[DataInitializer] 系统数据初始化完成");
    }

    // ==================== 菜单初始化 ====================

    @Data
    public static class MenuConfigJson {
        private List<MenuDefinition> menus;
    }

    @Data
    public static class MenuDefinition {
        private String code;
        private String name;
        private String icon;
        private String path;
        private String pageFile;
        private String section;
        private Integer sortOrder;
        private String pageType;
    }

    private void initMenusFromJson() {
        log.info("[DataInitializer] 从 JSON 初始化菜单数据...");

        MenuConfigJson config = loadJsonConfig("config/menu-config.json", MenuConfigJson.class);
        if (config == null || config.getMenus() == null || config.getMenus().isEmpty()) {
            log.error("[DataInitializer] 无法加载菜单配置文件");
            return;
        }

        int created = 0, updated = 0;
        for (MenuDefinition def : config.getMenus()) {
            Optional<Menu> existing = menuRepository.findByCode(def.getCode());
            
            if (existing.isPresent()) {
                // 更新现有菜单
                Menu menu = existing.get();
                menu.setName(def.getName());
                menu.setIcon(def.getIcon());
                menu.setPath(def.getPath());
                menu.setPageFile(def.getPageFile());
                menu.setSection(def.getSection());
                menu.setSortOrder(def.getSortOrder());
                menu.setPageType(def.getPageType() != null ? def.getPageType() : "LIST");
                menuRepository.save(menu);
                updated++;
            } else {
                // 创建新菜单
                Menu menu = Menu.builder()
                        .code(def.getCode())
                        .name(def.getName())
                        .icon(def.getIcon())
                        .path(def.getPath())
                        .pageFile(def.getPageFile())
                        .section(def.getSection())
                        .sortOrder(def.getSortOrder())
                        .pageType(def.getPageType() != null ? def.getPageType() : "LIST")
                        .build();
                menuRepository.save(menu);
                created++;
            }
        }

        log.info("[DataInitializer] 菜单初始化完成: 新增 {} 个, 更新 {} 个, 总计 {} 个", 
                created, updated, config.getMenus().size());
    }

    // ==================== 权限初始化 ====================

    @Data
    public static class PermissionConfigJson {
        private List<PermissionDefinition> permissions;
    }

    @Data
    public static class PermissionDefinition {
        private String code;
        private String name;
        private String description;
        private String module;
        private String type;
        private Integer sortOrder;
    }

    private void initPermissionsFromJson() {
        log.info("[DataInitializer] 从 JSON 初始化权限数据...");

        PermissionConfigJson config = loadJsonConfig("config/permission-config.json", PermissionConfigJson.class);
        if (config == null || config.getPermissions() == null || config.getPermissions().isEmpty()) {
            log.error("[DataInitializer] 无法加载权限配置文件");
            return;
        }

        int created = 0, updated = 0;
        for (PermissionDefinition def : config.getPermissions()) {
            Optional<Permission> existing = permissionRepository.findByCode(def.getCode());
            
            if (existing.isPresent()) {
                // 更新现有权限
                Permission permission = existing.get();
                permission.setName(def.getName());
                permission.setDescription(def.getDescription());
                permission.setModule(def.getModule());
                permission.setType(def.getType() != null ? def.getType() : "BUTTON");
                permission.setSortOrder(def.getSortOrder() != null ? def.getSortOrder() : 0);
                permissionRepository.save(permission);
                updated++;
            } else {
                // 创建新权限
                Permission permission = Permission.builder()
                        .code(def.getCode())
                        .name(def.getName())
                        .description(def.getDescription())
                        .module(def.getModule())
                        .type(def.getType() != null ? def.getType() : "BUTTON")
                        .sortOrder(def.getSortOrder() != null ? def.getSortOrder() : 0)
                        .build();
                permissionRepository.save(permission);
                created++;
            }
        }

        log.info("[DataInitializer] 权限初始化完成: 新增 {} 个, 更新 {} 个, 总计 {} 个", 
                created, updated, config.getPermissions().size());
    }

    // ==================== 角色初始化 ====================

    @Data
    public static class RoleConfigJson {
        private List<RoleDefinition> roles;
        private AdminUserDefinition adminUser;
    }

    @Data
    public static class RoleDefinition {
        private String code;
        private String name;
        private String description;
        private Boolean isSystem;
        private Integer sortOrder;
        private List<String> menus;        // 菜单 code 列表
        private List<String> permissions;  // 权限 code 列表
    }

    @Data
    public static class AdminUserDefinition {
        private String userCode;
        private String username;
        private String password;
        private String displayName;
    }

    private void initRolesFromJson() {
        log.info("[DataInitializer] 从 JSON 初始化角色数据...");

        RoleConfigJson config = loadJsonConfig("config/role-config.json", RoleConfigJson.class);
        if (config == null || config.getRoles() == null || config.getRoles().isEmpty()) {
            log.error("[DataInitializer] 无法加载角色配置文件");
            return;
        }

        int created = 0, updated = 0;
        for (RoleDefinition def : config.getRoles()) {
            Optional<Role> existing = roleRepository.findByCode(def.getCode());
            Role role;
            
            if (existing.isPresent()) {
                // 更新现有角色
                role = existing.get();
                role.setName(def.getName());
                role.setDescription(def.getDescription());
                role.setIsSystem(def.getIsSystem() != null ? def.getIsSystem() : false);
                role.setSortOrder(def.getSortOrder() != null ? def.getSortOrder() : 0);
                roleRepository.save(role);
                updated++;
            } else {
                // 创建新角色
                role = Role.builder()
                        .code(def.getCode())
                        .name(def.getName())
                        .description(def.getDescription())
                        .isSystem(def.getIsSystem() != null ? def.getIsSystem() : false)
                        .sortOrder(def.getSortOrder() != null ? def.getSortOrder() : 0)
                        .build();
                roleRepository.save(role);
                created++;
            }

            // 分配菜单（通过 code 查找 ID）
            if (def.getMenus() != null && !def.getMenus().isEmpty()) {
                List<Long> menuIds = new ArrayList<>();
                for (String menuCode : def.getMenus()) {
                    menuRepository.findByCode(menuCode).ifPresent(m -> menuIds.add(m.getId()));
                }
                if (!menuIds.isEmpty()) {
                    roleService.setRoleMenus(role.getId(), menuIds);
                    log.info("[DataInitializer] 角色 {} 分配 {} 个菜单", def.getCode(), menuIds.size());
                }
            }

            // 分配权限（通过 code 查找 ID）
            if (def.getPermissions() != null && !def.getPermissions().isEmpty()) {
                List<Long> permissionIds = new ArrayList<>();
                for (String permCode : def.getPermissions()) {
                    permissionRepository.findByCode(permCode).ifPresent(p -> permissionIds.add(p.getId()));
                }
                if (!permissionIds.isEmpty()) {
                    roleService.setRolePermissions(role.getId(), permissionIds);
                    log.info("[DataInitializer] 角色 {} 分配 {} 个权限", def.getCode(), permissionIds.size());
                }
            }
        }

        log.info("[DataInitializer] 角色初始化完成: 新增 {} 个, 更新 {} 个, 总计 {} 个", 
                created, updated, config.getRoles().size());

        // ADMIN 角色特殊处理：分配所有菜单和权限
        assignAdminAllAccess();
    }

    /**
     * 为 ADMIN 角色分配所有菜单和权限
     */
    private void assignAdminAllAccess() {
        Role adminRole = roleRepository.findByCode(AppConstants.ROLE_ADMIN_CODE).orElse(null);
        if (adminRole == null) {
            log.warn("[DataInitializer] ADMIN 角色不存在，跳过全量权限分配");
            return;
        }

        List<Long> allMenuIds = menuRepository.findAll().stream()
                .map(Menu::getId)
                .toList();
        roleService.setRoleMenus(adminRole.getId(), allMenuIds);

        List<Long> allPermissionIds = permissionRepository.findAll().stream()
                .map(Permission::getId)
                .toList();
        roleService.setRolePermissions(adminRole.getId(), allPermissionIds);

        log.info("[DataInitializer] ADMIN 角色已分配所有菜单({})和权限({})", 
                allMenuIds.size(), allPermissionIds.size());
    }

    // ==================== 管理员账户初始化 ====================

    private void initAdminUserFromJson() {
        log.info("[DataInitializer] 初始化管理员账户...");

        RoleConfigJson config = loadJsonConfig("config/role-config.json", RoleConfigJson.class);
        if (config == null || config.getAdminUser() == null) {
            log.error("[DataInitializer] 无法加载管理员账户配置");
            return;
        }

        AdminUserDefinition adminDef = config.getAdminUser();
        Role adminRole = roleRepository.findByCode(AppConstants.ROLE_ADMIN_CODE)
                .orElseThrow(() -> new RuntimeException("ADMIN 角色不存在"));

        // 查找或创建 admin 用户
        User admin = userRepository.findByUserCode(adminDef.getUserCode()).orElse(null);
        
        if (admin != null) {
            // 更新现有用户信息
            admin.setUsername(adminDef.getUsername());
            admin.setDisplayName(adminDef.getDisplayName());
            // 密码不更新（保持用户修改后的密码）
            userRepository.save(admin);
            log.info("[DataInitializer] 管理员账户已存在，更新信息: {}", adminDef.getUserCode());
        } else {
            // 创建新用户
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            admin = User.builder()
                    .userCode(adminDef.getUserCode())
                    .username(adminDef.getUsername())
                    .password(encoder.encode(adminDef.getPassword()))
                    .displayName(adminDef.getDisplayName())
                    .status(AppConstants.USER_STATUS_ACTIVE)
                    .build();
            userRepository.save(admin);
            log.info("[DataInitializer] 管理员账户创建完成: {}", adminDef.getUserCode());
        }

        // 确保绑定 ADMIN 角色
        boolean hasAdminRole = userRoleRepository.existsByUserIdAndRoleId(admin.getId(), adminRole.getId());
        if (!hasAdminRole) {
            UserRole userRole = UserRole.builder()
                    .userId(admin.getId())
                    .roleId(adminRole.getId())
                    .build();
            userRoleRepository.save(userRole);
            log.info("[DataInitializer] 已为 {} 绑定 ADMIN 角色", adminDef.getUserCode());
        }

        log.info("[DataInitializer] 管理员账户初始化完成: {} / {}", adminDef.getUserCode(), adminDef.getPassword());
    }

    // ==================== 工具方法 ====================

    private <T> T loadJsonConfig(String path, Class<T> clazz) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                log.warn("[DataInitializer] 配置文件 {} 不存在", path);
                return null;
            }
            return objectMapper.readValue(resource.getInputStream(), clazz);
        } catch (IOException e) {
            log.error("[DataInitializer] 读取配置文件 {} 失败: {}", path, e.getMessage());
            return null;
        }
    }
}
