package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.dto.PageResult;
import com.integration.config.dto.RoleDTO;
import com.integration.config.entity.config.Role;
import com.integration.config.service.RoleService;
import com.integration.config.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色管理 Controller
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RoleController {

    private final RoleService roleService;

    /**
     * 获取所有启用的角色（下拉选择用）
     */
    @GetMapping("/active")
    public Result<List<Role>> getActiveRoles() {
        List<Role> roles = roleService.getAllActiveRoles();
        return Result.success(roles);
    }

    /**
     * 获取所有角色（管理用）
     */
    @GetMapping
    public Result<List<Role>> getAllRoles() {
        List<Role> roles = roleService.getAllRoles();
        return Result.success(roles);
    }

    /**
     * 分页查询角色（含统计信息：用户数、接口数、菜单数、权限数）
     */
    @GetMapping("/page")
    public Result<PageResult<RoleDTO>> getRolePage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<RoleDTO> result = roleService.getRolePage(keyword, page, size);
        return Result.success(result);
    }

    /**
     * 获取单个角色
     */
    @GetMapping("/{id}")
    public Result<Role> getRole(@PathVariable Long id) {
        return roleService.getRoleById(id)
                .map(Result::success)
                .orElse(Result.of(404, "角色不存在", null));
    }

    /**
     * 创建角色
     */
    @PostMapping
    @AuditLog(operateType = "CREATE", module = "ROLE", description = "'创建角色: ' + #role.name", targetType = "ROLE", recordParams = true)
    public Result<Role> createRole(@RequestBody Role role) {
        try {
            Role created = roleService.createRole(role);
            return Result.success(created);
        } catch (Exception e) {
            return Result.of(400, e.getMessage(), null);
        }
    }

    /**
     * 更新角色
     */
    @PutMapping("/{id}")
    @AuditLog(operateType = "UPDATE", module = "ROLE", description = "'更新角色: ' + #role.name", targetType = "ROLE", recordParams = true)
    public Result<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        try {
            Role updated = roleService.updateRole(id, role);
            return Result.success(updated);
        } catch (Exception e) {
            return Result.of(400, e.getMessage(), null);
        }
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    @AuditLog(operateType = "DELETE", module = "ROLE", description = "'删除角色: ' + #id", targetType = "ROLE", recordParams = false)
    public Result<Void> deleteRole(@PathVariable Long id) {
        try {
            roleService.deleteRole(id);
            return Result.success(null);
        } catch (Exception e) {
            return Result.of(400, e.getMessage(), null);
        }
    }

    // ==================== 用户角色关联 ====================

    /**
     * 获取用户的角色列表
     */
    @GetMapping("/user/{userId}")
    public Result<List<Role>> getUserRoles(@PathVariable Long userId) {
        List<Role> roles = roleService.getUserRoles(userId);
        return Result.success(roles);
    }

    /**
     * 获取用户的角色ID列表
     */
    @GetMapping("/user/{userId}/ids")
    public Result<List<Long>> getUserRoleIds(@PathVariable Long userId) {
        List<Long> roleIds = roleService.getUserRoleIds(userId);
        return Result.success(roleIds);
    }

    /**
     * 获取角色下的用户ID列表
     */
    @GetMapping("/{id}/users/ids")
    public Result<List<Long>> getRoleUserIds(@PathVariable Long id) {
        List<Long> userIds = roleService.getRoleUserIds(id);
        return Result.success(userIds);
    }

    /**
     * 设置角色的用户列表（覆盖原有）
     */
    @PostMapping("/{id}/users")
    @AuditLog(operateType = "UPDATE", module = "ROLE_USER", description = "'设置角色用户: roleId=' + #id", targetType = "ROLE", recordParams = true)
    public Result<Void> setRoleUsers(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> userIds = body.get("userIds");
        if (userIds == null) {
            return Result.of(400, "缺少 userIds 参数", null);
        }
        roleService.setRoleUsers(id, userIds);
        return Result.success(null);
    }

    /**
     * 设置用户的角色
     */
    @PostMapping("/user/{userId}")
    @AuditLog(operateType = "UPDATE", module = "USER_ROLE", description = "'设置用户角色: userId=' + #userId", targetType = "USER", recordParams = true)
    public Result<Void> setUserRoles(@PathVariable Long userId, @RequestBody Map<String, List<Long>> body) {
        List<Long> roleIds = body.get("roleIds");
        if (roleIds == null) {
            return Result.of(400, "缺少 roleIds 参数", null);
        }
        roleService.setUserRoles(userId, roleIds);
        return Result.success(null);
    }

    // ==================== 接口角色关联 ====================

    /**
     * 获取接口的角色列表
     */
    @GetMapping("/api/{apiId}")
    public Result<List<Role>> getApiRoles(@PathVariable Long apiId) {
        List<Role> roles = roleService.getApiRoles(apiId);
        return Result.success(roles);
    }

    /**
     * 获取接口的角色ID列表
     */
    @GetMapping("/api/{apiId}/ids")
    public Result<List<Long>> getApiRoleIds(@PathVariable Long apiId) {
        List<Long> roleIds = roleService.getApiRoleIds(apiId);
        return Result.success(roleIds);
    }

    /**
     * 获取角色的接口ID列表
     */
    @GetMapping("/{id}/apis/ids")
    public Result<List<Long>> getRoleApiIds(@PathVariable Long id) {
        List<Long> apiIds = roleService.getRoleApiIds(id);
        return Result.success(apiIds);
    }

    /**
     * 设置角色的接口列表（覆盖原有）
     */
    @PostMapping("/{id}/apis")
    @AuditLog(operateType = "UPDATE", module = "ROLE_API", description = "'设置角色接口: roleId=' + #id", targetType = "ROLE", recordParams = true)
    public Result<Void> setRoleApis(@PathVariable Long id, @RequestBody Map<String, List<Long>> body, HttpServletRequest request) {
        List<Long> apiIds = body.get("apiIds");
        if (apiIds == null) {
            return Result.of(400, "缺少 apiIds 参数", null);
        }
        Long userId = (Long) request.getAttribute("userId");
        roleService.setRoleApis(id, apiIds, userId);
        return Result.success(null);
    }

    /**
     * 设置接口的角色
     */
    @PostMapping("/api/{apiId}")
    @AuditLog(operateType = "UPDATE", module = "API_ROLE", description = "'设置接口角色权限: apiId=' + #apiId", targetType = "API", recordParams = true)
    public Result<Void> setApiRoles(@PathVariable Long apiId, @RequestBody Map<String, List<Long>> body, HttpServletRequest request) {
        List<Long> roleIds = body.get("roleIds");
        if (roleIds == null) {
            return Result.of(400, "缺少 roleIds 参数", null);
        }
        Long userId = (Long) request.getAttribute("userId");
        roleService.setApiRoles(apiId, roleIds, userId);
        return Result.success(null);
    }

    /**
     * 获取当前用户可访问的接口ID列表
     */
    @GetMapping("/accessible-apis")
    public Result<List<Long>> getAccessibleApis(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Long> apiIds = roleService.getUserAccessibleApiIds(userId);
        return Result.success(apiIds);
    }

    // ==================== 角色菜单关联 ====================

    /**
     * 获取角色的菜单ID列表
     */
    @GetMapping("/{id}/menus")
    public Result<List<Long>> getRoleMenuIds(@PathVariable Long id) {
        List<Long> menuIds = roleService.getRoleMenuIds(id);
        return Result.success(menuIds);
    }

    /**
     * 设置角色的菜单
     */
    @PostMapping("/{id}/menus")
    @AuditLog(operateType = "UPDATE", module = "ROLE_MENU", description = "'设置角色菜单: roleId=' + #id", targetType = "ROLE", recordParams = true)
    public Result<Void> setRoleMenus(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> menuIds = body.get("menuIds");
        if (menuIds == null) {
            return Result.of(400, "缺少 menuIds 参数", null);
        }
        roleService.setRoleMenus(id, menuIds);
        return Result.success(null);
    }

    // ==================== 角色权限关联 ====================

    /**
     * 获取角色的权限ID列表
     */
    @GetMapping("/{id}/permissions")
    public Result<List<Long>> getRolePermissionIds(@PathVariable Long id) {
        List<Long> permissionIds = roleService.getRolePermissionIds(id);
        return Result.success(permissionIds);
    }

    /**
     * 设置角色的权限
     */
    @PostMapping("/{id}/permissions")
    @AuditLog(operateType = "UPDATE", module = "ROLE_PERM", description = "'设置角色权限: roleId=' + #id", targetType = "ROLE", recordParams = true)
    public Result<Void> setRolePermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> permissionIds = body.get("permissionIds");
        if (permissionIds == null) {
            return Result.of(400, "缺少 permissionIds 参数", null);
        }
        roleService.setRolePermissions(id, permissionIds);
        return Result.success(null);
    }

    /**
     * 给 ADMIN 角色分配所有权限
     * 用于初始化或修复权限数据
     */
    @PostMapping("/admin/assign-all-permissions")
    @AuditLog(operateType = "UPDATE", module = "ROLE_PERM", description = "'给 ADMIN 角色分配所有权限'", targetType = "ROLE", recordParams = false)
    public Result<Void> assignAllPermissionsToAdmin() {
        roleService.assignAllPermissionsToAdmin();
        return Result.success(null);
    }
}
