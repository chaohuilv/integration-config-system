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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "接口配置", description = "接口配置的 CRUD、版本控制及 Curl 命令导入")
public class ApiConfigController {

    private final ApiConfigService apiConfigService;

    @PostMapping
    @RequirePermission("api:add")
    @AuditLog(operateType = "CREATE", module = "API_CONFIG", description = "'创建接口: ' + #dto.code", targetType = "API", targetId = "#result.data.code", recordParams = true)
    @Operation(summary = "创建接口配置", description = "创建一个新的接口配置，编码全局唯一")
    public ResultVO<ApiConfig> create(@Valid @RequestBody ApiConfigDTO dto, HttpServletRequest request) {
        Long userId = getUserId(request);
        String userName = getUserName(request);
        ApiConfig config = apiConfigService.create(dto, userId, userName);
        return ResultVO.success("CREATE_SUCCESS", config);
    }

    @PutMapping("/{id}")
    @RequirePermission("api:edit")
    @AuditLog(operateType = "UPDATE", module = "API_CONFIG", description = "'更新接口: ' + #dto.code", targetType = "API", targetId = "#dto.code", recordParams = true)
    @Operation(summary = "更新接口配置", description = "根据ID更新接口配置信息")
    public ResultVO<ApiConfig> update(@PathVariable Long id, @Valid @RequestBody ApiConfigDTO dto, HttpServletRequest request) {
        Long userId = getUserId(request);
        String userName = getUserName(request);
        ApiConfig config = apiConfigService.update(id, dto, userId, userName);
        return ResultVO.success("UPDATE_SUCCESS", config);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("api:delete")
    @AuditLog(operateType = "DELETE", module = "API_CONFIG", description = "'删除接口ID: ' + #id", targetType = "API", targetId = "#id", recordParams = true)
    @Operation(summary = "删除接口配置", description = "根据ID删除接口配置")
    public ResultVO<Void> delete(@PathVariable Long id) {
        apiConfigService.delete(id);
        return ResultVO.success("DELETE_SUCCESS");
    }

    @GetMapping("/{id}")
    @RequirePermission("api:detail")
    @AuditLog(operateType = "QUERY", module = "API_CONFIG", description = "'查询接口详情ID: ' + #id", targetType = "API", targetId = "#id")
    @Operation(summary = "获取接口详情", description = "根据ID获取接口的完整配置信息，含版本信息")
    public ResultVO<ApiConfigDetailDTO> getById(@PathVariable Long id) {
        ApiConfigDetailDTO dto = apiConfigService.getById(id);
        return ResultVO.success(dto);
    }

    @GetMapping("/code/{code}")
    @RequirePermission("api:detail")
    @AuditLog(operateType = "QUERY", module = "API_CONFIG", description = "'查询接口编码: ' + #code", targetType = "API", targetId = "#code")
    @Operation(summary = "根据编码查询接口", description = "根据接口编码（code）精确查询接口配置")
    public ResultVO<ApiConfig> getByCode(@PathVariable String code) {
        ApiConfig config = apiConfigService.getByCode(code);
        return ResultVO.success(config);
    }

    @GetMapping("/page")
    @RequirePermission("api:view")
    @AuditLog(operateType = "QUERY", module = "API_CONFIG", description = "'分页查询接口列表'", recordResult = false)
    @Operation(summary = "分页查询接口列表", description = "支持关键词、状态、版本筛选的分页查询")
    public ResultVO<PageResult<ApiConfig>> pageQuery(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) String version,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        PageResult<ApiConfig> result = apiConfigService.pageQuery(keyword, status, version, page, size);
        return ResultVO.success(result);
    }

    @GetMapping("/active")
    @RequirePermission("api:view")
    @AuditLog(operateType = "QUERY", module = "API_CONFIG", description = "'查询启用接口列表'", recordResult = false)
    @Operation(summary = "获取所有启用的接口", description = "返回所有状态为 ACTIVE 的接口列表（不分页）")
    public ResultVO<List<ApiConfig>> getAllActive() {
        List<ApiConfig> list = apiConfigService.getAllActive();
        return ResultVO.success(list);
    }

    @PostMapping("/{id}/toggle")
    @RequirePermission("api:edit")
    @AuditLog(operateType = "UPDATE", module = "API_CONFIG", description = "'切换接口状态: ' + #id", targetType = "API", targetId = "#id", recordParams = true)
    @Operation(summary = "切换接口状态", description = "在 ACTIVE / INACTIVE 之间切换")
    public ResultVO<Void> toggleStatus(@PathVariable Long id) {
        apiConfigService.toggleStatus(id);
        return ResultVO.success("STATUS_CHANGED");
    }

    @GetMapping("/simple-list")
    @RequirePermission("api:view")
    @AuditLog(operateType = "QUERY", module = "API_CONFIG", description = "'查询接口简化列表'", recordResult = false)
    @Operation(summary = "获取接口简化列表", description = "返回接口的精简信息列表（id/code/name/method/url），用于下拉选择")
    public ResultVO<List> getSimpleList() {
        List list = apiConfigService.getSimpleList();
        return ResultVO.success(list);
    }

    // ==================== 版本控制 ====================

    @PostMapping("/{id}/version")
    @RequirePermission("api:version")
    @AuditLog(operateType = "CREATE", module = "API_CONFIG", description = "'创建新版本，源接口: ' + #sourceId", targetType = "API", targetId = "#result.data?.code", recordParams = true)
    @Operation(summary = "创建新版本", description = "基于现有接口复制生成新版本，自动分配版本号（v1→v2）")
    public ResultVO<ApiConfig> createNewVersion(@PathVariable("id") Long sourceId,
                                                @RequestBody CreateVersionDTO dto,
                                                HttpServletRequest request) {
        Long userId = getUserId(request);
        String userName = getUserName(request);
        ApiConfig config = apiConfigService.createNewVersion(sourceId, dto, userId, userName);
        return ResultVO.success("VERSION_CREATED", config);
    }

    @GetMapping("/{id}/versions")
    @RequirePermission("api:detail")
    @AuditLog(operateType = "QUERY", module = "API_CONFIG", description = "'查询接口版本列表: ' + #id", targetType = "API", targetId = "#id", recordResult = false)
    @Operation(summary = "获取接口的所有版本", description = "根据 baseCode 分组，返回某接口的所有版本列表")
    public ResultVO<List<ApiConfig>> getAllVersions(@PathVariable Long id) {
        ApiConfig entity = apiConfigService.getByIdForEntity(id);
        List<ApiConfig> versions = apiConfigService.getAllVersions(entity.getBaseCode());
        return ResultVO.success(versions);
    }

    @PostMapping("/{id}/set-latest")
    @RequirePermission("api:version")
    @AuditLog(operateType = "UPDATE", module = "API_CONFIG", description = "'设置最新版本: ' + #id", targetType = "API", targetId = "#id", recordParams = true)
    @Operation(summary = "设为推荐版本", description = "将指定版本设为主版本，同组其他版本的 latestVersion 自动取消")
    public ResultVO<Void> setLatestVersion(@PathVariable Long id, HttpServletRequest request) {
        apiConfigService.setLatestVersion(id);
        return ResultVO.success("SET_AS_LATEST");
    }

    @PostMapping("/{id}/deprecate")
    @RequirePermission("api:deprecate")
    @AuditLog(operateType = "UPDATE", module = "API_CONFIG", description = "'切换废弃状态: ' + #id", targetType = "API", targetId = "#id", recordParams = true)
    @Operation(summary = "切换废弃状态", description = "废弃接口使其不再被推荐使用，或恢复已废弃的接口")
    public ResultVO<Void> toggleDeprecated(@PathVariable Long id, HttpServletRequest request) {
        apiConfigService.toggleDeprecated(id);
        return ResultVO.success("OPERATION_SUCCESS");
    }

    // ==================== Curl 导入 ====================

    @PostMapping("/import/curl")
    @RequirePermission("api:add")
    @AuditLog(operateType = "IMPORT", module = "API_CONFIG", description = "'Curl导入接口'", recordParams = true)
    @Operation(summary = "Curl 命令导入", description = "解析标准 curl 命令，自动提取 URL、Method、Headers、Body，生成接口配置")
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

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    private String getUserName(HttpServletRequest request) {
        return (String) request.getAttribute("username");
    }
}
