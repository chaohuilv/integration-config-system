package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.annotation.RequirePermission;
import com.integration.config.dto.PageResult;
import com.integration.config.dto.RoleDTO;
import com.integration.config.entity.config.Role;
import com.integration.config.enums.ErrorCode;
import com.integration.config.exception.BusinessException;
import com.integration.config.service.RoleService;
import com.integration.config.vo.ResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "角色管理", description = "角色 CRUD 及角色与用户/接口/菜单/权限的关联管理")
public class RoleController {

    private final RoleService roleService;

    // ==================== 基础查询 ====================

    @GetMapping("/active")
    @RequirePermission("role:view")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询启用角色列表'", recordResult = false)
    @Operation(summary = "获取所有启用的角色", description = "返回状态为启用的角色列表，用于下拉选择框")
    public ResultVO<List<Role>> getActiveRoles() {
        List<Role> roles = roleService.getAllActiveRoles();
        return ResultVO.success(roles);
    }

    @GetMapping
    @RequirePermission("role:view")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询所有角色'", recordResult = false)
    @Operation(summary = "获取所有角色", description = "返回全部角色列表（不分页），用于管理")
    public ResultVO<List<Role>> getAllRoles() {
        List<Role> roles = roleService.getAllRoles();
        return ResultVO.success(roles);
    }

    @GetMapping("/page")
    @RequirePermission("role:view")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'分页查询角色'", recordResult = false)
    @Operation(summary = "分页查询角色", description = "支持关键词搜索，返回角色列表及关联统计信息")
    public ResultVO<PageResult<RoleDTO>> getRolePage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<RoleDTO> result = roleService.getRolePage(keyword, page, size);
        return ResultVO.success(result);
    }

    @GetMapping("/{id}")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询角色详情ID: ' + #id", targetType = "ROLE", targetId = "#id")
    @Operation(summary = "获取角色详情", description = "根据ID获取单个角色的完整信息")
    public ResultVO<Role> getRole(@PathVariable Long id) {
        return roleService.getRoleById(id)
                .map(ResultVO::success)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
    }

    @PostMapping
    @RequirePermission("role:add")
    @AuditLog(operateType = "CREATE", module = "ROLE", description = "'创建角色: ' + #role.name", targetType = "ROLE", recordParams = true)
    @Operation(summary = "创建角色", description = "创建一个新角色，名称不可重复")
    public ResultVO<Role> createRole(@RequestBody Role role) {
        try {
            Role created = roleService.createRole(role);
            return ResultVO.success(created);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "ROLE", description = "'更新角色: ' + #role.name", targetType = "ROLE", recordParams = true)
    @Operation(summary = "更新角色", description = "根据ID更新角色信息")
    public ResultVO<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        try {
            Role updated = roleService.updateRole(id, role);
            return ResultVO.success(updated);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @RequirePermission("role:delete")
    @AuditLog(operateType = "DELETE", module = "ROLE", description = "'删除角色: ' + #id", targetType = "ROLE", recordParams = false)
    @Operation(summary = "删除角色", description = "根据ID删除角色")
    public ResultVO<Void> deleteRole(@PathVariable Long id) {
        try {
            roleService.deleteRole(id);
            return ResultVO.success(null);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, e.getMessage());
        }
    }

    // ==================== 用户角色关联 ====================

    @GetMapping("/user/{userId}")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询用户角色: userId=' + #userId", targetType = "USER", targetId = "#userId")
    @Operation(summary = "获取用户的角色列表", description = "根据用户ID查询该用户拥有的所有角色")
    public ResultVO<List<Role>> getUserRoles(@PathVariable Long userId) {
        List<Role> roles = roleService.getUserRoles(userId);
        return ResultVO.success(roles);
    }

    @GetMapping("/user/{userId}/ids")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询用户角色ID: userId=' + #userId", targetType = "USER", targetId = "#userId")
    @Operation(summary = "获取用户的角色ID列表", description = "根据用户ID查询该用户拥有的角色ID集合")
    public ResultVO<List<Long>> getUserRoleIds(@PathVariable Long userId) {
        List<Long> roleIds = roleService.getUserRoleIds(userId);
        return ResultVO.success(roleIds);
    }

    @GetMapping("/{id}/users/ids")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询角色用户ID: roleId=' + #id", targetType = "ROLE", targetId = "#id")
    @Operation(summary = "获取角色的用户ID列表", description = "根据角色ID查询拥有该角色的所有用户ID")
    public ResultVO<List<Long>> getRoleUserIds(@PathVariable Long id) {
        List<Long> userIds = roleService.getRoleUserIds(id);
        return ResultVO.success(userIds);
    }

    @PostMapping("/{id}/users")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "ROLE_USER", description = "'设置角色用户: roleId=' + #id", targetType = "ROLE", recordParams = true)
    @Operation(summary = "设置角色的用户列表", description = "覆盖式设置某角色下的所有用户，传入 userIds 列表")
    public ResultVO<Void> setRoleUsers(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> userIds = body.get("userIds");
        if (userIds == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "缺少 userIds 参数");
        }
        roleService.setRoleUsers(id, userIds);
        return ResultVO.success(null);
    }

    @PostMapping("/user/{userId}")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "USER_ROLE", description = "'设置用户角色: userId=' + #userId", targetType = "USER", recordParams = true)
    @Operation(summary = "设置用户的角色", description = "覆盖式设置某用户的所有角色，传入 roleIds 列表")
    public ResultVO<Void> setUserRoles(@PathVariable Long userId, @RequestBody Map<String, List<Long>> body) {
        List<Long> roleIds = body.get("roleIds");
        if (roleIds == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "缺少 roleIds 参数");
        }
        roleService.setUserRoles(userId, roleIds);
        return ResultVO.success(null);
    }

    // ==================== 接口角色关联 ====================

    @GetMapping("/api/{apiId}")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询接口角色: apiId=' + #apiId", targetType = "API", targetId = "#apiId")
    @Operation(summary = "获取接口的角色列表", description = "根据接口ID查询可访问该接口的所有角色")
    public ResultVO<List<Role>> getApiRoles(@PathVariable Long apiId) {
        List<Role> roles = roleService.getApiRoles(apiId);
        return ResultVO.success(roles);
    }

    @GetMapping("/api/{apiId}/ids")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询接口角色ID: apiId=' + #apiId", targetType = "API", targetId = "#apiId")
    @Operation(summary = "获取接口的角色ID列表", description = "根据接口ID查询可访问该接口的角色ID集合")
    public ResultVO<List<Long>> getApiRoleIds(@PathVariable Long apiId) {
        List<Long> roleIds = roleService.getApiRoleIds(apiId);
        return ResultVO.success(roleIds);
    }

    @GetMapping("/{id}/apis/ids")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询角色接口ID: roleId=' + #id", targetType = "ROLE", targetId = "#id")
    @Operation(summary = "获取角色的接口ID列表", description = "根据角色ID查询该角色可访问的所有接口ID")
    public ResultVO<List<Long>> getRoleApiIds(@PathVariable Long id) {
        List<Long> apiIds = roleService.getRoleApiIds(id);
        return ResultVO.success(apiIds);
    }

    @PostMapping("/{id}/apis")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "ROLE_API", description = "'设置角色接口: roleId=' + #id", targetType = "ROLE", recordParams = true)
    @Operation(summary = "设置角色的接口列表", description = "覆盖式设置某角色可访问的接口，传入 apiIds 列表")
    public ResultVO<Void> setRoleApis(@PathVariable Long id, @RequestBody Map<String, List<Long>> body, HttpServletRequest request) {
        List<Long> apiIds = body.get("apiIds");
        if (apiIds == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "缺少 apiIds 参数");
        }
        Long userId = (Long) request.getAttribute("userId");
        roleService.setRoleApis(id, apiIds, userId);
        return ResultVO.success(null);
    }

    @PostMapping("/api/{apiId}")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "API_ROLE", description = "'设置接口角色权限: apiId=' + #apiId", targetType = "API", recordParams = true)
    @Operation(summary = "设置接口的角色", description = "覆盖式设置某接口的允许访问角色，传入 roleIds 列表")
    public ResultVO<Void> setApiRoles(@PathVariable Long apiId, @RequestBody Map<String, List<Long>> body, HttpServletRequest request) {
        List<Long> roleIds = body.get("roleIds");
        if (roleIds == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "缺少 roleIds 参数");
        }
        Long userId = (Long) request.getAttribute("userId");
        roleService.setApiRoles(apiId, roleIds, userId);
        return ResultVO.success(null);
    }

    @GetMapping("/accessible-apis")
    @RequirePermission("role:view")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询用户可访问接口列表'", recordResult = false)
    @Operation(summary = "获取当前用户可访问的接口ID列表", description = "根据当前登录用户的角色，返回其可访问的所有接口ID")
    public ResultVO<List<Long>> getAccessibleApis(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Long> apiIds = roleService.getUserAccessibleApiIds(userId);
        return ResultVO.success(apiIds);
    }

    // ==================== 角色菜单关联 ====================

    @GetMapping("/{id}/menus")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询角色菜单: roleId=' + #id", targetType = "ROLE", targetId = "#id")
    @Operation(summary = "获取角色的菜单ID列表", description = "根据角色ID查询该角色拥有的菜单ID集合")
    public ResultVO<List<Long>> getRoleMenuIds(@PathVariable Long id) {
        List<Long> menuIds = roleService.getRoleMenuIds(id);
        return ResultVO.success(menuIds);
    }

    @PostMapping("/{id}/menus")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "ROLE_MENU", description = "'设置角色菜单: roleId=' + #id", targetType = "ROLE", recordParams = true)
    @Operation(summary = "设置角色的菜单", description = "覆盖式设置某角色的菜单权限，传入 menuIds 列表")
    public ResultVO<Void> setRoleMenus(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> menuIds = body.get("menuIds");
        if (menuIds == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "缺少 menuIds 参数");
        }
        roleService.setRoleMenus(id, menuIds);
        return ResultVO.success(null);
    }

    // ==================== 角色权限关联 ====================

    @GetMapping("/{id}/permissions")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询角色权限: roleId=' + #id", targetType = "ROLE", targetId = "#id")
    @Operation(summary = "获取角色的权限ID列表", description = "根据角色ID查询该角色拥有的权限ID集合")
    public ResultVO<List<Long>> getRolePermissionIds(@PathVariable Long id) {
        List<Long> permissionIds = roleService.getRolePermissionIds(id);
        return ResultVO.success(permissionIds);
    }

    @PostMapping("/{id}/permissions")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "ROLE_PERM", description = "'设置角色权限: roleId=' + #id", targetType = "ROLE", recordParams = true)
    @Operation(summary = "设置角色的权限", description = "覆盖式设置某角色的细粒度权限，传入 permissionIds 列表")
    public ResultVO<Void> setRolePermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> permissionIds = body.get("permissionIds");
        if (permissionIds == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "缺少 permissionIds 参数");
        }
        roleService.setRolePermissions(id, permissionIds);
        return ResultVO.success(null);
    }

    @PostMapping("/admin/assign-all-permissions")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "ROLE_PERM", description = "'给 ADMIN 角色分配所有权限'", targetType = "ROLE", recordParams = false)
    @Operation(summary = "给 ADMIN 角色分配所有权限", description = "一键将系统所有菜单和权限授予 ADMIN 角色，用于初始化或修复")
    public ResultVO<Void> assignAllPermissionsToAdmin() {
        roleService.assignAllPermissionsToAdmin();
        return ResultVO.success(null);
    }
}
