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
    @RequirePermission("role:view")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询启用角色列表'", recordResult = false)
    public ResultVO<List<Role>> getActiveRoles() {
        List<Role> roles = roleService.getAllActiveRoles();
        return ResultVO.success(roles);
    }

    /**
     * 获取所有角色（管理用）
     */
    @GetMapping
    @RequirePermission("role:view")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询所有角色'", recordResult = false)
    public ResultVO<List<Role>> getAllRoles() {
        List<Role> roles = roleService.getAllRoles();
        return ResultVO.success(roles);
    }

    /**
     * 分页查询角色（含统计信息：用户数、接口数、菜单数、权限数）
     */
    @GetMapping("/page")
    @RequirePermission("role:view")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'分页查询角色'", recordResult = false)
    public ResultVO<PageResult<RoleDTO>> getRolePage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<RoleDTO> result = roleService.getRolePage(keyword, page, size);
        return ResultVO.success(result);
    }

    /**
     * 获取单个角色
     */
    @GetMapping("/{id}")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询角色详情ID: ' + #id", targetType = "ROLE", targetId = "#id")
    public ResultVO<Role> getRole(@PathVariable Long id) {
        return roleService.getRoleById(id)
                .map(ResultVO::success)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
    }

    /**
     * 创建角色
     */
    @PostMapping
    @RequirePermission("role:add")
    @AuditLog(operateType = "CREATE", module = "ROLE", description = "'创建角色: ' + #role.name", targetType = "ROLE", recordParams = true)
        public ResultVO<Role> createRole(@RequestBody Role role) {
        try {
            Role created = roleService.createRole(role);
            return ResultVO.success(created);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, e.getMessage());
        }
    }


    /**
     * 更新角色
     */
    @PutMapping("/{id}")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "ROLE", description = "'更新角色: ' + #role.name", targetType = "ROLE", recordParams = true)
    public ResultVO<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        try {
            Role updated = roleService.updateRole(id, role);
            return ResultVO.success(updated);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, e.getMessage());
        }
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    @RequirePermission("role:delete")
    @AuditLog(operateType = "DELETE", module = "ROLE", description = "'删除角色: ' + #id", targetType = "ROLE", recordParams = false)
    public ResultVO<Void> deleteRole(@PathVariable Long id) {
        try {
            roleService.deleteRole(id);
            return ResultVO.success(null);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, e.getMessage());
        }
    }

    // ==================== 用户角色关联 ====================

    /**
     * 获取用户的角色列表
     */
    @GetMapping("/user/{userId}")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询用户角色: userId=' + #userId", targetType = "USER", targetId = "#userId")
    public ResultVO<List<Role>> getUserRoles(@PathVariable Long userId) {
        List<Role> roles = roleService.getUserRoles(userId);
        return ResultVO.success(roles);
    }

    /**
     * 获取用户的角色ID列表
     */
    @GetMapping("/user/{userId}/ids")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询用户角色ID: userId=' + #userId", targetType = "USER", targetId = "#userId")
    public ResultVO<List<Long>> getUserRoleIds(@PathVariable Long userId) {
        List<Long> roleIds = roleService.getUserRoleIds(userId);
        return ResultVO.success(roleIds);
    }

    /**
     * 获取角色下的用户ID列表
     */
    @GetMapping("/{id}/users/ids")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询角色用户ID: roleId=' + #id", targetType = "ROLE", targetId = "#id")
    public ResultVO<List<Long>> getRoleUserIds(@PathVariable Long id) {
        List<Long> userIds = roleService.getRoleUserIds(id);
        return ResultVO.success(userIds);
    }

    /**
     * 设置角色的用户列表（覆盖原有）
     */
    @PostMapping("/{id}/users")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "ROLE_USER", description = "'设置角色用户: roleId=' + #id", targetType = "ROLE", recordParams = true)
    public ResultVO<Void> setRoleUsers(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> userIds = body.get("userIds");
        if (userIds == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "缺少 userIds 参数");
        }
        roleService.setRoleUsers(id, userIds);
        return ResultVO.success(null);
    }

    /**
     * 设置用户的角色
     */
    @PostMapping("/user/{userId}")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "USER_ROLE", description = "'设置用户角色: userId=' + #userId", targetType = "USER", recordParams = true)
    public ResultVO<Void> setUserRoles(@PathVariable Long userId, @RequestBody Map<String, List<Long>> body) {
        List<Long> roleIds = body.get("roleIds");
        if (roleIds == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "缺少 roleIds 参数");
        }
        roleService.setUserRoles(userId, roleIds);
        return ResultVO.success(null);
    }

    // ==================== 接口角色关联 ====================

    /**
     * 获取接口的角色列表
     */
    @GetMapping("/api/{apiId}")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询接口角色: apiId=' + #apiId", targetType = "API", targetId = "#apiId")
    public ResultVO<List<Role>> getApiRoles(@PathVariable Long apiId) {
        List<Role> roles = roleService.getApiRoles(apiId);
        return ResultVO.success(roles);
    }

    /**
     * 获取接口的角色ID列表
     */
    @GetMapping("/api/{apiId}/ids")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询接口角色ID: apiId=' + #apiId", targetType = "API", targetId = "#apiId")
    public ResultVO<List<Long>> getApiRoleIds(@PathVariable Long apiId) {
        List<Long> roleIds = roleService.getApiRoleIds(apiId);
        return ResultVO.success(roleIds);
    }

    /**
     * 获取角色的接口ID列表
     */
    @GetMapping("/{id}/apis/ids")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询角色接口ID: roleId=' + #id", targetType = "ROLE", targetId = "#id")
    public ResultVO<List<Long>> getRoleApiIds(@PathVariable Long id) {
        List<Long> apiIds = roleService.getRoleApiIds(id);
        return ResultVO.success(apiIds);
    }

    /**
     * 设置角色的接口列表（覆盖原有）
     */
    @PostMapping("/{id}/apis")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "ROLE_API", description = "'设置角色接口: roleId=' + #id", targetType = "ROLE", recordParams = true)
    public ResultVO<Void> setRoleApis(@PathVariable Long id, @RequestBody Map<String, List<Long>> body, HttpServletRequest request) {
        List<Long> apiIds = body.get("apiIds");
        if (apiIds == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "缺少 apiIds 参数");
        }
        Long userId = (Long) request.getAttribute("userId");
        roleService.setRoleApis(id, apiIds, userId);
        return ResultVO.success(null);
    }

    /**
     * 设置接口的角色
     */
    @PostMapping("/api/{apiId}")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "API_ROLE", description = "'设置接口角色权限: apiId=' + #apiId", targetType = "API", recordParams = true)
    public ResultVO<Void> setApiRoles(@PathVariable Long apiId, @RequestBody Map<String, List<Long>> body, HttpServletRequest request) {
        List<Long> roleIds = body.get("roleIds");
        if (roleIds == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "缺少 roleIds 参数");
        }
        Long userId = (Long) request.getAttribute("userId");
        roleService.setApiRoles(apiId, roleIds, userId);
        return ResultVO.success(null);
    }

    /**
     * 获取当前用户可访问的接口ID列表
     */
    @GetMapping("/accessible-apis")
    @RequirePermission("role:view")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询用户可访问接口列表'", recordResult = false)
    public ResultVO<List<Long>> getAccessibleApis(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Long> apiIds = roleService.getUserAccessibleApiIds(userId);
        return ResultVO.success(apiIds);
    }

    // ==================== 角色菜单关联 ====================

    /**
     * 获取角色的菜单ID列表
     */
    @GetMapping("/{id}/menus")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询角色菜单: roleId=' + #id", targetType = "ROLE", targetId = "#id")
    public ResultVO<List<Long>> getRoleMenuIds(@PathVariable Long id) {
        List<Long> menuIds = roleService.getRoleMenuIds(id);
        return ResultVO.success(menuIds);
    }

    /**
     * 设置角色的菜单
     */
    @PostMapping("/{id}/menus")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "ROLE_MENU", description = "'设置角色菜单: roleId=' + #id", targetType = "ROLE", recordParams = true)
    public ResultVO<Void> setRoleMenus(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> menuIds = body.get("menuIds");
        if (menuIds == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "缺少 menuIds 参数");
        }
        roleService.setRoleMenus(id, menuIds);
        return ResultVO.success(null);
    }

    // ==================== 角色权限关联 ====================

    /**
     * 获取角色的权限ID列表
     */
    @GetMapping("/{id}/permissions")
    @RequirePermission("role:detail")
    @AuditLog(operateType = "QUERY", module = "ROLE", description = "'查询角色权限: roleId=' + #id", targetType = "ROLE", targetId = "#id")
    public ResultVO<List<Long>> getRolePermissionIds(@PathVariable Long id) {
        List<Long> permissionIds = roleService.getRolePermissionIds(id);
        return ResultVO.success(permissionIds);
    }

    /**
     * 设置角色的权限
     */
    @PostMapping("/{id}/permissions")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "ROLE_PERM", description = "'设置角色权限: roleId=' + #id", targetType = "ROLE", recordParams = true)
    public ResultVO<Void> setRolePermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> permissionIds = body.get("permissionIds");
        if (permissionIds == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "缺少 permissionIds 参数");
        }
        roleService.setRolePermissions(id, permissionIds);
        return ResultVO.success(null);
    }

    /**
     * 给 ADMIN 角色分配所有权限
     * 用于初始化或修复权限数据
     */
    @PostMapping("/admin/assign-all-permissions")
    @RequirePermission("role:edit")
    @AuditLog(operateType = "UPDATE", module = "ROLE_PERM", description = "'给 ADMIN 角色分配所有权限'", targetType = "ROLE", recordParams = false)
    public ResultVO<Void> assignAllPermissionsToAdmin() {
        roleService.assignAllPermissionsToAdmin();
        return ResultVO.success(null);
    }
}
