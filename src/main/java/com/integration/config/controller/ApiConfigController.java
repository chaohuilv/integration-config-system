package com.integration.config.controller;

import com.integration.config.dto.*;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.enums.Status;
import com.integration.config.service.ApiConfigService;
import com.integration.config.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 接口配置管理 Controller
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiConfigController {

    private final ApiConfigService apiConfigService;

    /**
     * 创建接口配置
     */
    @PostMapping
    public Result<ApiConfig> create(@Valid @RequestBody ApiConfigDTO dto, HttpServletRequest request) {
        Long userId = getUserId(request);
        String userName = getUserName(request);
        ApiConfig config = apiConfigService.create(dto, userId, userName);
        return Result.success("创建成功", config);
    }

    /**
     * 更新接口配置
     */
    @PutMapping("/{id}")
    public Result<ApiConfig> update(@PathVariable Long id, @Valid @RequestBody ApiConfigDTO dto, HttpServletRequest request) {
        Long userId = getUserId(request);
        String userName = getUserName(request);
        ApiConfig config = apiConfigService.update(id, dto, userId, userName);
        return Result.success("更新成功", config);
    }

    /**
     * 删除接口配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        apiConfigService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 根据ID查询详情
     */
    @GetMapping("/{id}")
    public Result<ApiConfigDetailDTO> getById(@PathVariable Long id) {
        ApiConfigDetailDTO dto = apiConfigService.getById(id);
        return Result.success(dto);
    }

    /**
     * 根据编码查询
     */
    @GetMapping("/code/{code}")
    public Result<ApiConfig> getByCode(@PathVariable String code) {
        ApiConfig config = apiConfigService.getByCode(code);
        return Result.success(config);
    }

    /**
     * 分页查询
     */
    @GetMapping("/page")
    public Result<PageResult<ApiConfig>> pageQuery(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        PageResult<ApiConfig> result = apiConfigService.pageQuery(keyword, status, page, size);
        return Result.success(result);
    }

    /**
     * 获取所有启用的接口列表
     */
    @GetMapping("/active")
    public Result<List<ApiConfig>> getAllActive() {
        List<ApiConfig> list = apiConfigService.getAllActive();
        return Result.success(list);
    }

    /**
     * 切换状态
     */
    @PostMapping("/{id}/toggle")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        apiConfigService.toggleStatus(id);
        return Result.success("状态切换成功", null);
    }

    /**
     * 获取接口列表（简化信息）
     */
    @GetMapping("/simple-list")
    public Result<List> getSimpleList() {
        List list = apiConfigService.getSimpleList();
        return Result.success(list);
    }

    // ==================== Curl 一键导入 ====================

    /**
     * Curl 命令一键导入接口配置
     * 支持标准 curl 命令格式，自动提取 URL、方法、请求头、请求体
     */
    @PostMapping("/import/curl")
    public Result<String> importFromCurl(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String curl = body.get("curl");
        if (curl == null || curl.trim().isEmpty()) {
            return Result.fail("curl 命令不能为空");
        }
        try {
            Long userId = getUserId(request);
            String userName = getUserName(request);
            ApiConfigDTO dto = apiConfigService.importFromCurl(curl, userId, userName);
            return Result.success("导入成功，接口编码：" + dto.getCode(), dto.getCode());
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 Request Attribute 获取用户ID（由 LoginFilter 设置）
     */
    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    /**
     * 从 Request Attribute 获取用户名（由 LoginFilter 设置）
     */
    private String getUserName(HttpServletRequest request) {
        return (String) request.getAttribute("username");
    }
}
