package com.integration.config.controller;

import com.integration.config.dto.CreateUserDTO;
import com.integration.config.dto.LoginRequestDTO;
import com.integration.config.dto.UserDTO;
import com.integration.config.entity.config.User;
import com.integration.config.service.UserService;
import com.integration.config.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证控制器 - 登录/注销/用户管理
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;

    /**
     * 用户登录（使用用户编码）
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequestDTO dto, HttpServletRequest request) {
        User user = userService.login(dto.getUserCode(), dto.getPassword());
        if (user == null) {
            return Result.error("用户编码或密码错误，或账号已禁用");
        }

        // 更新登录信息
        String clientIp = getClientIp(request);
        userService.updateLoginInfo(user.getId(), clientIp);

        // 存入Session
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", user.getId());
        session.setAttribute("userCode", user.getUserCode());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("displayName", user.getDisplayName());
        session.setMaxInactiveInterval(24 * 60 * 60); // 24小时

        log.info("用户登录成功: {} ({}) from {}", user.getUserCode(), user.getUsername(), clientIp);

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("userCode", user.getUserCode());
        data.put("username", user.getUsername());
        data.put("displayName", user.getDisplayName());
        return Result.success(data);
    }

    /**
     * 用户注销
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String userCode = (String) session.getAttribute("userCode");
            log.info("用户注销: {}", userCode);
            session.invalidate();
        }
        return Result.success();
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/current")
    public Result<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            return Result.error("未登录");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", session.getAttribute("userId"));
        data.put("userCode", session.getAttribute("userCode"));
        data.put("username", session.getAttribute("username"));
        data.put("displayName", session.getAttribute("displayName"));
        return Result.success(data);
    }

    /**
     * 检查登录状态
     */
    @GetMapping("/check")
    public Result<Map<String, Object>> checkLogin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            return Result.error("未登录");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("loggedIn", true);
        data.put("id", session.getAttribute("userId"));
        data.put("userCode", session.getAttribute("userCode"));
        data.put("username", session.getAttribute("username"));
        data.put("displayName", session.getAttribute("displayName"));
        return Result.success(data);
    }

    // ==================== 用户管理接口 ====================

    /**
     * 创建用户（需要登录）
     */
    @PostMapping("/users")
    public Result<UserDTO> createUser(@RequestBody CreateUserDTO dto, HttpServletRequest request) {
        Long creatorId = getUserId(request);
        if (creatorId == null) {
            return Result.error("未登录");
        }

        try {
            User user = userService.create(dto, creatorId);
            return Result.success(userService.list("", 1, 10).getContent().stream()
                    .filter(u -> u.getId().equals(user.getId()))
                    .findFirst()
                    .orElse(null));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新用户
     */
    @PutMapping("/users/{id}")
    public Result<UserDTO> updateUser(@PathVariable Long id, @RequestBody CreateUserDTO dto) {
        try {
            userService.update(id, dto);
            return Result.success(userService.getById(id)
                    .map(u -> userService.list("", 1, 10).getContent().stream()
                            .filter(dto1 -> dto1.getId().equals(u.getId()))
                            .findFirst()
                            .orElse(null))
                    .orElse(null));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        Long currentUserId = getUserId(request);
        if (currentUserId != null && currentUserId.equals(id)) {
            return Result.error("不能删除当前登录用户");
        }

        userService.delete(id);
        return Result.success();
    }

    /**
     * 分页查询用户
     */
    @GetMapping("/users")
    public Result<Page<UserDTO>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(userService.list(keyword, page, size));
    }

    /**
     * 获取所有用户（下拉选择用）
     */
    @GetMapping("/users/all")
    public Result<List<UserDTO>> listAllUsers() {
        return Result.success(userService.listAll());
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/users/{id}")
    public Result<UserDTO> getUser(@PathVariable Long id) {
        return userService.getById(id)
                .map(u -> Result.success(userService.list("", 1, 10).getContent().stream()
                        .filter(dto -> dto.getId().equals(u.getId()))
                        .findFirst()
                        .orElse(null)))
                .orElse(Result.error("用户不存在"));
    }

    // ==================== 辅助方法 ====================

    private Long getUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null ? (Long) session.getAttribute("userId") : null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 取第一个IP（如果有多级代理）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
