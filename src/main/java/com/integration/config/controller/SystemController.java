package com.integration.config.controller;

import com.integration.config.entity.config.Menu;
import com.integration.config.entity.config.Permission;
import com.integration.config.service.MenuService;
import com.integration.config.service.PermissionService;
import com.integration.config.vo.ResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "系统配置", description = "菜单管理和权限配置查询")
public class SystemController {

    private final MenuService menuService;
    private final PermissionService permissionService;

    @GetMapping("/menus")
    @Operation(summary = "获取所有菜单", description = "返回系统中所有菜单项（含禁用的），用于管理")
    public ResultVO<List<Menu>> getAllMenus() {
        List<Menu> menus = menuService.getAllMenus();
        return ResultVO.success(menus);
    }

    @GetMapping("/menus/active")
    @Operation(summary = "获取所有启用的菜单", description = "返回状态为启用的菜单列表，用于侧边栏显示")
    public ResultVO<List<Menu>> getActiveMenus() {
        List<Menu> menus = menuService.getActiveMenus();
        return ResultVO.success(menus);
    }

    @GetMapping("/menus/grouped")
    @Operation(summary = "按分组获取菜单", description = "返回按菜单分组（section）聚合的菜单树结构")
    public ResultVO<Map<String, List<Menu>>> getMenusGrouped() {
        Map<String, List<Menu>> grouped = menuService.getMenusGroupBySection();
        return ResultVO.success(grouped);
    }

    @GetMapping("/permissions")
    @Operation(summary = "获取所有权限", description = "返回系统中所有权限定义列表，用于权限管理")
    public ResultVO<List<Permission>> getAllPermissions() {
        List<Permission> permissions = permissionService.getAllPermissions();
        return ResultVO.success(permissions);
    }

    @GetMapping("/permissions/buttons")
    @Operation(summary = "获取按钮级权限", description = "返回所有按钮级别的权限定义，用于前端动态渲染按钮权限")
    public ResultVO<List<Permission>> getButtonPermissions() {
        List<Permission> permissions = permissionService.getButtonPermissions();
        return ResultVO.success(permissions);
    }
}
