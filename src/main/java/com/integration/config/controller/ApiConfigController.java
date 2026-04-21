package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.annotation.RequirePermission;
import com.integration.config.dto.*;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.enums.ErrorCode;
import com.integration.config.enums.Status;
import com.integration.config.exception.BusinessException;
import com.integration.config.service.ApiConfigService;
import com.integration.config.vo.ResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

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
    @RequirePermission("api:add")
    @AuditLog(operateType = "CREATE", module = "API_CONFIG", description = "'创建接口: ' + #dto.code", targetType = "API", targetId = "#result.data.code", recordParams = true)
    public ResultVO<ApiConfig> create(@Valid @RequestBody ApiConfigDTO dto, HttpServletRequest request) {
        Long userId = getUserId(request);
        String userName = getUserName(request);
        ApiConfig config = apiConfigService.create(dto, userId, userName);
        return ResultVO.success("CREATE_SUCCESS", config);
    }

    /**
     * 更新接口配置
     */
    @PutMapping("/{id}")
    @RequirePermission("api:edit")
    @AuditLog(operateType = "UPDATE", module = "API_CONFIG", description = "'更新接口: ' + #dto.code", targetType = "API", targetId = "#dto.code", recordParams = true)
    public ResultVO<ApiConfig> update(@PathVariable Long id, @Valid @RequestBody ApiConfigDTO dto, HttpServletRequest request) {
        Long userId = getUserId(request);
        String userName = getUserName(request);
        ApiConfig config = apiConfigService.update(id, dto, userId, userName);
        return ResultVO.success("UPDATE_SUCCESS", config);
    }

    /**
     * 删除接口配置
     */
    @DeleteMapping("/{id}")
    @RequirePermission("api:delete")
    @AuditLog(operateType = "DELETE", module = "API_CONFIG", description = "'删除接口ID: ' + #id", targetType = "API", targetId = "#id", recordParams = true)
    public ResultVO<Void> delete(@PathVariable Long id) {
        apiConfigService.delete(id);
        return ResultVO.success("DELETE_SUCCESS");
    }

    /**
     * 根据ID查询详情
     */
    @GetMapping("/{id}")
    @RequirePermission("api:detail")
    @AuditLog(operateType = "QUERY", module = "API_CONFIG", description = "'查询接口详情ID: ' + #id", targetType = "API", targetId = "#id")
    public ResultVO<ApiConfigDetailDTO> getById(@PathVariable Long id) {
        ApiConfigDetailDTO dto = apiConfigService.getById(id);
        return ResultVO.success(dto);
    }

    /**
     * 根据编码查询
     */
    @GetMapping("/code/{code}")
    @RequirePermission("api:detail")
    @AuditLog(operateType = "QUERY", module = "API_CONFIG", description = "'查询接口编码: ' + #code", targetType = "API", targetId = "#code")
    public ResultVO<ApiConfig> getByCode(@PathVariable String code) {
        ApiConfig config = apiConfigService.getByCode(code);
        return ResultVO.success(config);
    }

    /**
     * 分页查询
     */
    @GetMapping("/page")
    @RequirePermission("api:view")
    @AuditLog(operateType = "QUERY", module = "API_CONFIG", description = "'分页查询接口列表'", recordResult = false)
    public ResultVO<PageResult<ApiConfig>> pageQuery(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) String version,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        PageResult<ApiConfig> result = apiConfigService.pageQuery(keyword, status, version, page, size);
        return ResultVO.success(result);
    }

    /**
     * 获取所有启用的接口列表
     */
    @GetMapping("/active")
    @RequirePermission("api:view")
    @AuditLog(operateType = "QUERY", module = "API_CONFIG", description = "'查询启用接口列表'", recordResult = false)
    public ResultVO<List<ApiConfig>> getAllActive() {
        List<ApiConfig> list = apiConfigService.getAllActive();
        return ResultVO.success(list);
    }

    /**
     * 切换状态
     */
    @PostMapping("/{id}/toggle")
    @RequirePermission("api:edit")
    @AuditLog(operateType = "UPDATE", module = "API_CONFIG", description = "'切换接口状态: ' + #id", targetType = "API", targetId = "#id", recordParams = true)
    public ResultVO<Void> toggleStatus(@PathVariable Long id) {
        apiConfigService.toggleStatus(id);
        return ResultVO.success("STATUS_CHANGED");
    }

    /**
     * 获取接口列表（简化信息）
     */
    @GetMapping("/simple-list")
    @RequirePermission("api:view")
    @AuditLog(operateType = "QUERY", module = "API_CONFIG", description = "'查询接口简化列表'", recordResult = false)
    public ResultVO<List> getSimpleList() {
        List list = apiConfigService.getSimpleList();
        return ResultVO.success(list);
    }

    // ==================== 版本控制 ====================

    /**
     * 创建新版本（基于现有接口复制）
     * @param sourceId 源接口ID
     * @param dto 新版本配置（只需填写 name/description/url 等需修改的字段，版本信息自动生成）
     */
    @PostMapping("/{id}/version")
    @RequirePermission("api:version")
    @AuditLog(operateType = "CREATE", module = "API_CONFIG", description = "'创建新版本，源接口: ' + #sourceId", targetType = "API", targetId = "#result.data?.code", recordParams = true)
    public ResultVO<ApiConfig> createNewVersion(@PathVariable("id") Long sourceId,
                                                @RequestBody CreateVersionDTO dto,
                                                HttpServletRequest request) {
        Long userId = getUserId(request);
        String userName = getUserName(request);
        ApiConfig config = apiConfigService.createNewVersion(sourceId, dto, userId, userName);
        return ResultVO.success("VERSION_CREATED", config);
    }

    /**
     * 获取某接口的所有版本列表
     */
    @GetMapping("/{id}/versions")
    @RequirePermission("api:detail")
    @AuditLog(operateType = "QUERY", module = "API_CONFIG", description = "'查询接口版本列表: ' + #id", targetType = "API", targetId = "#id", recordResult = false)
    public ResultVO<List<ApiConfig>> getAllVersions(@PathVariable Long id) {
        ApiConfig entity = apiConfigService.getByIdForEntity(id);
        List<ApiConfig> versions = apiConfigService.getAllVersions(entity.getBaseCode());
        return ResultVO.success(versions);
    }

    /**
     * 设置某版本为最新推荐版本
     */
    @PostMapping("/{id}/set-latest")
    @RequirePermission("api:version")
    @AuditLog(operateType = "UPDATE", module = "API_CONFIG", description = "'设置最新版本: ' + #id", targetType = "API", targetId = "#id", recordParams = true)
    public ResultVO<Void> setLatestVersion(@PathVariable Long id, HttpServletRequest request) {
        apiConfigService.setLatestVersion(id);
        return ResultVO.success("SET_AS_LATEST");
    }

    /**
     * 废弃/恢复某版本
     */
    @PostMapping("/{id}/deprecate")
    @RequirePermission("api:deprecate")
    @AuditLog(operateType = "UPDATE", module = "API_CONFIG", description = "'切换废弃状态: ' + #id", targetType = "API", targetId = "#id", recordParams = true)
    public ResultVO<Void> toggleDeprecated(@PathVariable Long id, HttpServletRequest request) {
        apiConfigService.toggleDeprecated(id);
        return ResultVO.success("OPERATION_SUCCESS");
    }

    // ==================== Curl 一键导入 ====================

    /**
     * Curl 命令一键导入接口配置
     * 支持标准 curl 命令格式，自动提取 URL、方法、请求头、请求体
     */
    @PostMapping("/import/curl")
    @RequirePermission("api:add")
    @AuditLog(operateType = "IMPORT", module = "API_CONFIG", description = "'Curl导入接口'", recordParams = true)
    public ResultVO<String> importFromCurl(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String curl = body.get("curl");
        if (curl == null || curl.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "curl 命令不能为空");
        }
        try {
            Long userId = getUserId(request);
            String userName = getUserName(request);
            ApiConfigDTO dto = apiConfigService.importFromCurl(curl, userId, userName);
            return ResultVO.success("IMPORT_SUCCESS", dto.getCode());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, e.getMessage());
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
