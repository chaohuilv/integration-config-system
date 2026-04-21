package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.dto.CreateUserDTO;
import com.integration.config.dto.LoginRequestDTO;
import com.integration.config.dto.UserDTO;
import com.integration.config.entity.config.Menu;
import com.integration.config.entity.config.User;
import com.integration.config.enums.ErrorCode;
import com.integration.config.exception.BusinessException;
import com.integration.config.service.MenuService;
import com.integration.config.service.RoleService;
import com.integration.config.service.TokenService;
import com.integration.config.service.UserService;
import com.integration.config.enums.AppConstants;
import com.integration.config.vo.ResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认证控制器 - 登录/注销/用户管理 (Bearer Token 模式)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "认证管理", description = "用户登录、注销、当前用户信息及用户 CRUD")
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;
    private final RoleService roleService;
    private final MenuService menuService;

    /**
     * 用户登录
     * 返回 access_token，后续请求通过 Authorization: Bearer <token> 认证
     */
    @PostMapping("/login")
    @AuditLog(operateType = "LOGIN", module = "AUTH", description = "'用户登录: ' + #dto.userCode", targetType = "USER", recordParams = true)
    @Operation(summary = "用户登录", description = "传入 userCode 和 password，返回 access_token，后续请求通过 Authorization: Bearer <token> 携带")
    public ResultVO<Map<String, Object>> login(@RequestBody LoginRequestDTO dto, HttpServletRequest request) {
        User user = userService.login(dto.getUserCode(), dto.getPassword());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户编码或密码错误，或账号已禁用");
        }

        // 更新登录信息
        String clientIp = getClientIp(request);
        userService.updateLoginInfo(user.getId(), clientIp);

        // 生成 access_token
        String accessToken = tokenService.createToken(
                user.getId(),
                user.getUserCode(),
                user.getUsername(),
                user.getDisplayName(),
                clientIp
        );

        log.info("用户登录成功: {} ({}) from {}", user.getUserCode(), user.getUsername(), clientIp);

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("userCode", user.getUserCode());
        data.put("username", user.getUsername());
        data.put("displayName", user.getDisplayName());
        data.put("access_token", accessToken);  // 新增：返回 access_token
        return ResultVO.success(data);
    }

    /**
     * 用户注销
     * 从 Authorization header 提取 token 并撤销
     */
    @PostMapping("/logout")
    @AuditLog(operateType = "LOGOUT", module = "AUTH", description = "'用户注销'")
    @Operation(summary = "用户注销", description = "撤销当前 Token，使 Token 失效")
    public ResultVO<Void> logout(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (token != null) {
            tokenService.revokeToken(token);
            log.info("用户注销: token={}", token.substring(0, 8) + "...");
        }
        return ResultVO.success();
    }

    /**
     * 获取当前登录用户信息
     * 从 Request Attribute 读取（由 LoginFilter 设置）
     */
    @GetMapping("/current")
    @Operation(summary = "获取当前用户信息", description = "从请求上下文读取当前登录用户的基本信息")
    public ResultVO<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", userId);
        data.put("userCode", request.getAttribute("userCode"));
        data.put("username", request.getAttribute("username"));
        data.put("displayName", request.getAttribute("displayName"));
        return ResultVO.success(data);
    }

    /**
     * 检查登录状态
     */
    @GetMapping("/check")
    @Operation(summary = "检查登录状态", description = "验证 Token 有效性，返回登录状态和用户基本信息，含 isAdmin 标识")
    public ResultVO<Map<String, Object>> checkLogin(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("loggedIn", true);
        data.put("id", userId);
        data.put("userCode", request.getAttribute("userCode"));
        data.put("username", request.getAttribute("username"));
        data.put("displayName", request.getAttribute("displayName"));
        
        // 添加角色信息
        boolean isAdmin = roleService.isAdmin(userId);
        data.put("isAdmin", isAdmin);
        
        return ResultVO.success(data);
    }

    /**
     * 获取当前用户的菜单和页面路由映射
     * 
     * 返回数据结构：
     * - menus: 用户可访问的列表页菜单（用于侧边栏显示）
     * - allMenus: 所有菜单（包括列表页和表单页，用于前端路由映射）
     */
    @GetMapping("/menus")
    @Operation(summary = "获取当前用户菜单", description = "返回用户可访问的侧边栏菜单列表及所有页面路由映射（pageMap）")
    public ResultVO<Map<String, Object>> getUserMenus(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }

        List<Menu> listMenus;
        
        // 管理员返回所有列表页菜单
        if (roleService.isAdmin(userId)) {
            log.info("[AuthController] 用户 {} 是管理员，返回所有菜单", userId);
            listMenus = menuService.getListMenus().stream()
                    .filter(m -> AppConstants.USER_STATUS_ACTIVE.equals(m.getStatus()))
                    .collect(Collectors.toList());
        } else {
            // 非管理员根据角色获取菜单（只获取列表页），使用缓存
            List<Long> roleIds = roleService.getUserRoleIds(userId);
            log.info("[AuthController] 用户 {} 的角色ID列表: {}", userId, roleIds);
            
            listMenus = menuService.getUserMenus(userId, roleIds).stream()
                    .filter(m -> "LIST".equals(m.getPageType()) || m.getPageType() == null)
                    .collect(Collectors.toList());
            log.info("[AuthController] 用户 {} 可访问的菜单: {}", userId, 
                    listMenus.stream().map(Menu::getCode).toList());
        }
        
        // 获取 pageMap（使用 Redis 全局缓存）
        Map<String, String> pageMap = menuService.getPageMap();
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("menus", listMenus);       // 用户可访问的列表页菜单
        result.put("pageMap", pageMap);       // 所有页面路由映射
        
        return ResultVO.success(result);
    }

    /**
     * 获取当前用户的权限编码列表
     */
    @GetMapping("/permissions")
    @Operation(summary = "获取当前用户权限列表", description = "返回当前用户拥有的所有权限编码，用于前端按钮级别权限判断")
    public ResultVO<List<String>> getUserPermissions(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }

        List<String> permissions = roleService.getUserPermissionCodes(userId);
        return ResultVO.success(permissions);
    }

    // ==================== 用户管理接口 ====================

    /**
     * 创建用户（需要登录）
     */
    @PostMapping("/users")
    @AuditLog(operateType = "CREATE", module = "USER", description = "'创建用户: ' + #dto.userCode", targetType = "USER", targetId = "#result.data.id", recordParams = true)
    @Operation(summary = "创建用户", description = "创建一个新用户，用户编码全局唯一")
    public ResultVO<UserDTO> createUser(@RequestBody CreateUserDTO dto, HttpServletRequest request) {
        Long creatorId = (Long) request.getAttribute("userId");
        if (creatorId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }

        try {
            User user = userService.create(dto, creatorId);
            return ResultVO.success(userService.list("", 1, 10).getContent().stream()
                    .filter(u -> u.getId().equals(user.getId()))
                    .findFirst()
                    .orElse(null));
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, e.getMessage());
        }
    }

    /**
     * 更新用户
     */
    @PutMapping("/users/{id}")
    @AuditLog(operateType = "UPDATE", module = "USER", description = "'更新用户: ' + #dto.userCode", targetType = "USER", targetId = "#id", recordParams = true)
    @Operation(summary = "更新用户", description = "根据ID更新用户信息")
    public ResultVO<UserDTO> updateUser(@PathVariable Long id, @RequestBody CreateUserDTO dto) {
        try {
            userService.update(id, dto);
            return ResultVO.success(userService.getById(id)
                    .map(u -> userService.list("", 1, 10).getContent().stream()
                            .filter(dto1 -> dto1.getId().equals(u.getId()))
                            .findFirst()
                            .orElse(null))
                    .orElse(null));
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, e.getMessage());
        }
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/users/{id}")
    @AuditLog(operateType = "DELETE", module = "USER", description = "'删除用户ID: ' + #id", targetType = "USER", targetId = "#id", recordParams = true)
    @Operation(summary = "删除用户", description = "根据ID删除用户，不能删除当前登录用户")
    public ResultVO<Void> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId != null && currentUserId.equals(id)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "不能删除当前登录用户");
        }

        userService.delete(id);
        return ResultVO.success();
    }

    /**
     * 分页查询用户
     */
    @GetMapping("/users")
    @AuditLog(operateType = "QUERY", module = "USER", description = "'查询用户列表'", recordResult = false)
    @Operation(summary = "分页查询用户列表", description = "支持关键词搜索的分页查询")
    public ResultVO<Page<UserDTO>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResultVO.success(userService.list(keyword, page, size));
    }

    /**
     * 获取所有用户（下拉选择用）
     */
    @GetMapping("/users/all")
    @AuditLog(operateType = "QUERY", module = "USER", description = "'查询所有用户'", recordResult = false)
    @Operation(summary = "获取所有用户", description = "返回全部用户列表（不分页），用于下拉选择框")
    public ResultVO<List<UserDTO>> listAllUsers() {
        return ResultVO.success(userService.listAll());
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/users/{id}")
    @AuditLog(operateType = "QUERY", module = "USER", description = "'查询用户详情ID: ' + #id", targetType = "USER", targetId = "#id")
    @Operation(summary = "获取用户详情", description = "根据ID获取用户的详细信息")
    public ResultVO<UserDTO> getUser(@PathVariable Long id) {
        return userService.getById(id)
                .map(u -> ResultVO.success(userService.list("", 1, 10).getContent().stream()
                        .filter(dto -> dto.getId().equals(u.getId()))
                        .findFirst()
                        .orElse(null)))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    // ==================== 辅助方法 ====================

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 从 Authorization header 提取 Bearer token
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}
