package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.annotation.RequirePermission;
import com.integration.config.exception.BusinessException;
import com.integration.config.enums.ErrorCode;
import com.integration.config.vo.ResultVO;
import com.integration.config.entity.config.MockConfig;
import com.integration.config.service.MockConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Mock 配置管理 Controller
 */
@RestController
@RequestMapping("/api/mock")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Mock 配置", description = "Mock 服务的配置管理、匹配规则及响应模板")
public class MockConfigController {

    private final MockConfigService mockConfigService;

    // ==================== CRUD ====================

    @PostMapping
    @RequirePermission("mock:add")
    @AuditLog(operateType = "CREATE", module = "MOCK_CONFIG", description = "'创建Mock配置: ' + #config.code", targetType = "MOCK", targetId = "#result.data.code", recordParams = true)
    @Operation(summary = "创建Mock配置", description = "创建一个新的Mock配置，编码全局唯一")
    public ResultVO<MockConfig> create(@RequestBody MockConfig config, HttpServletRequest request) {
        config.setCreatedBy(getUserName(request));
        return ResultVO.success(mockConfigService.create(config));
    }

    @PutMapping("/{id}")
    @RequirePermission("mock:edit")
    @AuditLog(operateType = "UPDATE", module = "MOCK_CONFIG", description = "'更新Mock配置: ' + #config.code", targetType = "MOCK", targetId = "#config.code", recordParams = true)
    @Operation(summary = "更新Mock配置", description = "根据ID更新Mock配置信息")
    public ResultVO<MockConfig> update(@PathVariable Long id, @RequestBody MockConfig config, HttpServletRequest request) {
        config.setUpdatedBy(getUserName(request));
        return ResultVO.success(mockConfigService.update(id, config));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("mock:delete")
    @AuditLog(operateType = "DELETE", module = "MOCK_CONFIG", description = "'删除Mock配置ID: ' + #id", targetType = "MOCK", targetId = "#id", recordParams = true)
    @Operation(summary = "删除Mock配置", description = "根据ID删除Mock配置")
    public ResultVO<Void> delete(@PathVariable Long id) {
        mockConfigService.delete(id);
        return ResultVO.success();
    }

    @GetMapping("/{id}")
    @RequirePermission("mock:detail")
    @AuditLog(operateType = "QUERY", module = "MOCK_CONFIG", description = "'查询Mock配置详情ID: ' + #id", targetType = "MOCK", targetId = "#id")
    @Operation(summary = "获取Mock配置详情", description = "根据ID获取Mock配置的完整信息")
    public ResultVO<MockConfig> getById(@PathVariable Long id) {
        return mockConfigService.findById(id)
                .map(ResultVO::success)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Mock 配置不存在"));
    }

    @GetMapping("/code/{code}")
    @RequirePermission("mock:detail")
    @AuditLog(operateType = "QUERY", module = "MOCK_CONFIG", description = "'查询Mock配置编码: ' + #code", targetType = "MOCK", targetId = "#code")
    @Operation(summary = "根据编码查询Mock配置", description = "根据Mock编码精确查询配置")
    public ResultVO<MockConfig> getByCode(@PathVariable String code) {
        return mockConfigService.findByCode(code)
                .map(ResultVO::success)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Mock 配置不存在"));
    }

    // ==================== 列表与分页 ====================

    @GetMapping("/list")
    @RequirePermission("mock:view")
    @AuditLog(operateType = "QUERY", module = "MOCK_CONFIG", description = "'分页查询Mock配置列表'", recordResult = false)
    @Operation(summary = "分页查询Mock配置列表", description = "支持分组、启用状态、关键词筛选的分页查询，按优先级升序排列")
    public ResultVO<Page<MockConfig>> pageQuery(
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {

        // 前端页码从 1 开始，JPA PageRequest 从 0 开始，需转换
        // 空字符串转 null，避免 JPQL IS NULL 条件失效
        if (groupName != null && groupName.isBlank()) groupName = null;
        if (keyword != null && keyword.isBlank()) keyword = null;

        PageRequest pageRequest = PageRequest.of(Math.max(0, page - 1), size,
                Sort.by("priority").ascending().and(Sort.by("id").ascending()));
        return ResultVO.success(mockConfigService.pageQuery(groupName, enabled, keyword, pageRequest));
    }

    @GetMapping("/groups")
    @RequirePermission("mock:view")
    @AuditLog(operateType = "QUERY", module = "MOCK_CONFIG", description = "'查询Mock分组列表'", recordResult = false)
    @Operation(summary = "获取Mock分组列表", description = "返回所有已使用的分组名称，用于筛选下拉")
    public ResultVO<List<String>> getAllGroups() {
        return ResultVO.success(mockConfigService.getAllGroupNames());
    }

    // ==================== 操作 ====================

    @PostMapping("/{id}/toggle")
    @RequirePermission("mock:edit")
    @AuditLog(operateType = "ENABLE", module = "MOCK_CONFIG", description = "'切换Mock启用状态ID: ' + #id", targetType = "MOCK", targetId = "#id", recordParams = true)
    @Operation(summary = "切换Mock启用状态", description = "在启用/禁用之间切换")
    public ResultVO<Void> toggleEnabled(@PathVariable Long id) {
        mockConfigService.toggleEnabled(id);
        return ResultVO.success();
    }

    @PostMapping("/{id}/reset")
    @RequirePermission("mock:edit")
    @AuditLog(operateType = "OTHER", module = "MOCK_CONFIG", description = "'重置Mock命中统计ID: ' + #id", targetType = "MOCK", targetId = "#id", recordParams = true)
    @Operation(summary = "重置Mock命中统计", description = "将命中次数和最后命中时间重置为零")
    public ResultVO<Void> resetHitCount(@PathVariable Long id) {
        mockConfigService.resetHitCount(id);
        return ResultVO.success();
    }

    @GetMapping("/stats")
    @RequirePermission("mock:view")
    @AuditLog(operateType = "QUERY", module = "MOCK_CONFIG", description = "'查询Mock统计信息'", recordResult = false)
    @Operation(summary = "获取Mock统计信息", description = "返回启用配置数量等统计信息")
    public ResultVO<Map<String, Object>> getStats() {
        return ResultVO.success(Map.of(
                "enabledCount", mockConfigService.countEnabled()
        ));
    }

    // ==================== 辅助方法 ====================

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    private String getUserName(HttpServletRequest request) {
        return (String) request.getAttribute("username");
    }
}
