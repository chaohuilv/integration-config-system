package com.integration.config.controller;

import com.integration.config.entity.config.Menu;
import com.integration.config.entity.config.Permission;
import com.integration.config.service.MenuService;
import com.integration.config.service.PermissionService;
import com.integration.config.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置 Controller
 * 菜单、权限管理
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SystemController {

    private final MenuService menuService;
    private final PermissionService permissionService;

    // ==================== 菜单管理 ====================

    /**
     * 获取所有菜单
     */
    @GetMapping("/menus")
    public Result<List<Menu>> getAllMenus() {
        List<Menu> menus = menuService.getAllMenus();
        return Result.success(menus);
    }

    /**
     * 获取所有启用的菜单
     */
    @GetMapping("/menus/active")
    public Result<List<Menu>> getActiveMenus() {
        List<Menu> menus = menuService.getActiveMenus();
        return Result.success(menus);
    }

    /**
     * 按分组获取菜单
     */
    @GetMapping("/menus/grouped")
    public Result<Map<String, List<Menu>>> getMenusGrouped() {
        Map<String, List<Menu>> grouped = menuService.getMenusGroupBySection();
        return Result.success(grouped);
    }

    // ==================== 权限管理 ====================

    /**
     * 获取所有权限
     */
    @GetMapping("/permissions")
    public Result<List<Permission>> getAllPermissions() {
        List<Permission> permissions = permissionService.getAllPermissions();
        return Result.success(permissions);
    }

    /**
     * 获取按钮权限
     */
    @GetMapping("/permissions/buttons")
    public Result<List<Permission>> getButtonPermissions() {
        List<Permission> permissions = permissionService.getButtonPermissions();
        return Result.success(permissions);
    }
}
