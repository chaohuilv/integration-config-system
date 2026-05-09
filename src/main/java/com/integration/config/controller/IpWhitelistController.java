package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.annotation.RequirePermission;
import com.integration.config.dto.IpWhitelistRuleDTO;
import com.integration.config.dto.PageResult;
import com.integration.config.entity.config.IpWhitelistRule;
import com.integration.config.enums.ErrorCode;
import com.integration.config.exception.BusinessException;
import com.integration.config.service.IpWhitelistService;
import com.integration.config.util.SnowflakeUtil;
import com.integration.config.vo.ResultVO;
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
import java.util.stream.Collectors;

/**
 * IP 白名单管理 Controller
 */
@RestController
@RequestMapping("/api/ip-whitelist")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "IP 白名单", description = "IP 白名单/黑名单规则管理，支持单IP、CIDR、IP段匹配")
public class IpWhitelistController {

    private final IpWhitelistService ipWhitelistService;

    @GetMapping("/active")
    @RequirePermission("ipwhite:view")
    @Operation(summary = "获取所有启用的规则", description = "返回所有启用的 IP 白名单规则")
    public ResultVO<List<IpWhitelistRule>> getActiveRules() {
        List<IpWhitelistRule> rules = ipWhitelistService.findAllActive();
        return ResultVO.success(rules);
    }

    @GetMapping
    @RequirePermission("ipwhite:view")
    @AuditLog(operateType = "QUERY", module = "IP_WHITE", description = "'查询IP白名单规则列表'", recordResult = false)
    @Operation(summary = "分页查询 IP 白名单规则")
    public ResultVO<PageResult<IpWhitelistRuleDTO>> getPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        // 前端 Pagination 组件回调 page 从 1 开始，后端需要 0-based
        List<IpWhitelistRule> all = ipWhitelistService.findAll();

        List<IpWhitelistRule> filtered = all.stream()
                .filter(r -> keyword == null || keyword.isEmpty()
                        || r.getRuleName().contains(keyword)
                        || r.getRuleCode().contains(keyword)
                        || (r.getDescription() != null && r.getDescription().contains(keyword)))
                .filter(r -> scope == null || scope.isEmpty() || scope.equals(r.getScope()))
                .filter(r -> status == null || status.isEmpty() || status.equals(r.getStatus()))
                .collect(Collectors.toList());

        int total = filtered.size();
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);
        List<IpWhitelistRule> pageList = start < total ? filtered.subList(start, end) : List.of();

        List<IpWhitelistRuleDTO> dtoList = pageList.stream().map(this::toDTO).collect(Collectors.toList());

        PageResult<IpWhitelistRuleDTO> result = new PageResult<>();
        result.setRecords(dtoList);
        result.setTotal((long) total);
        result.setPage(page);
        result.setSize(size);

        return ResultVO.success(result);
    }

    @GetMapping("/{id}")
    @RequirePermission("ipwhite:view")
    @AuditLog(operateType = "QUERY", module = "IP_WHITE", description = "'查询IP白名单规则详情ID: ' + #id", targetType = "IP_WHITE", targetId = "#id")
    @Operation(summary = "获取规则详情")
    public ResultVO<IpWhitelistRule> getById(@PathVariable Long id) {
        return ipWhitelistService.findById(id)
                .map(ResultVO::success)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "规则不存在"));
    }

    @PostMapping
    @RequirePermission("ipwhite:add")
    @AuditLog(operateType = "CREATE", module = "IP_WHITE", description = "'创建IP白名单规则: ' + #rule.ruleName", targetType = "IP_WHITE", recordParams = true)
    @Operation(summary = "创建规则", description = "创建新的 IP 白名单/黑名单规则")
    public ResultVO<IpWhitelistRule> create(
            @RequestBody IpWhitelistRule rule,
            HttpServletRequest request) {

        validateRule(rule, null);

        // 自动生成编码
        if (rule.getRuleCode() == null || rule.getRuleCode().isEmpty()) {
            rule.setRuleCode("IPR_" + SnowflakeUtil.nextId());
        }

        // 设置创建人
        String createdBy = (String) request.getAttribute("username");
        rule.setCreatedBy(createdBy);

        IpWhitelistRule saved = ipWhitelistService.save(rule);
        return ResultVO.success(saved);
    }

    @PutMapping("/{id}")
    @RequirePermission("ipwhite:edit")
    @AuditLog(operateType = "UPDATE", module = "IP_WHITE", description = "'更新IP白名单规则ID: ' + #id", targetType = "IP_WHITE", targetId = "#id", recordParams = true)
    @Operation(summary = "更新规则")
    public ResultVO<IpWhitelistRule> update(
            @PathVariable Long id,
            @RequestBody IpWhitelistRule rule) {

        IpWhitelistRule existing = ipWhitelistService.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "规则不存在"));

        // 系统内置规则不允许修改
        if (Boolean.TRUE.equals(existing.getCreatedBy()) && "SYSTEM".equals(existing.getCreatedBy())) {
            // 保留状态字段：只允许更新 status
            if (!"ACTIVE".equals(rule.getStatus()) && !"INACTIVE".equals(rule.getStatus())) {
                existing.setStatus(rule.getStatus());
            } else {
                existing.setStatus(rule.getStatus());
            }
        } else {
            // 普通规则：全部字段可改
            if (rule.getRuleName() != null) existing.setRuleName(rule.getRuleName());
            if (rule.getDescription() != null) existing.setDescription(rule.getDescription());
            if (rule.getScope() != null) existing.setScope(rule.getScope());
            if (rule.getApiCodes() != null) existing.setApiCodes(rule.getApiCodes());
            if (rule.getIpList() != null) existing.setIpList(rule.getIpList());
            if (rule.getMatchMode() != null) existing.setMatchMode(rule.getMatchMode());
            if (rule.getStatus() != null) existing.setStatus(rule.getStatus());
            if (rule.getPriority() != null) existing.setPriority(rule.getPriority());
        }

        IpWhitelistRule saved = ipWhitelistService.save(existing);
        return ResultVO.success(saved);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("ipwhite:delete")
    @AuditLog(operateType = "DELETE", module = "IP_WHITE", description = "'删除IP白名单规则ID: ' + #id", targetType = "IP_WHITE", targetId = "#id")
    @Operation(summary = "删除规则")
    public ResultVO<Void> delete(@PathVariable Long id) {
        IpWhitelistRule rule = ipWhitelistService.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "规则不存在"));
        ipWhitelistService.deleteById(id);
        return ResultVO.success(null);
    }

    @PutMapping("/{id}/toggle")
    @RequirePermission("ipwhite:edit")
    @AuditLog(operateType = "UPDATE", module = "IP_WHITE", description = "'切换IP白名单规则状态ID: ' + #id", targetType = "IP_WHITE", targetId = "#id")
    @Operation(summary = "启用/停用规则")
    public ResultVO<IpWhitelistRule> toggleStatus(@PathVariable Long id) {
        IpWhitelistRule rule = ipWhitelistService.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "规则不存在"));

        rule.setStatus("ACTIVE".equals(rule.getStatus()) ? "INACTIVE" : "ACTIVE");
        IpWhitelistRule saved = ipWhitelistService.save(rule);
        return ResultVO.success(saved);
    }

    @PostMapping("/validate")
    @RequirePermission("ipwhite:view")
    @Operation(summary = "校验 IP 列表格式")
    public ResultVO<Map<String, String>> validateIpList(@RequestBody Map<String, String> body) {
        String ipList = body.get("ipList");
        String error = ipWhitelistService.validateIpList(ipList);
        return ResultVO.success(Map.of("valid", error == null ? "true" : "false", "error", error != null ? error : ""));
    }

    // ==================== 私有方法 ====================

    private void validateRule(IpWhitelistRule rule, Long excludeId) {
        if (rule.getRuleName() == null || rule.getRuleName().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "规则名称不能为空");
        }
        if (rule.getScope() == null || rule.getScope().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "作用范围不能为空");
        }
        if (!"GLOBAL".equals(rule.getScope()) && !"API".equals(rule.getScope())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "作用范围只能是 GLOBAL 或 API");
        }
        if ("API".equals(rule.getScope()) && (rule.getApiCodes() == null || rule.getApiCodes().isEmpty())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "API 级别规则必须指定关联接口");
        }
        if (rule.getIpList() == null || rule.getIpList().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "IP 列表不能为空");
        }
        String validationError = ipWhitelistService.validateIpList(rule.getIpList());
        if (validationError != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, validationError);
        }
        if (!"WHITELIST".equals(rule.getMatchMode()) && !"BLACKLIST".equals(rule.getMatchMode())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "匹配模式只能是 WHITELIST 或 BLACKLIST");
        }
        // 编码唯一性
        if (rule.getRuleCode() != null && !rule.getRuleCode().isEmpty()) {
            boolean exists = excludeId != null
                    ? ipWhitelistService.existsByCodeAndIdNot(rule.getRuleCode(), excludeId)
                    : ipWhitelistService.existsByCode(rule.getRuleCode());
            if (exists) {
                throw new BusinessException(ErrorCode.ALREADY_EXISTS, "规则编码已存在: " + rule.getRuleCode());
            }
        }
    }

    private IpWhitelistRuleDTO toDTO(IpWhitelistRule rule) {
        IpWhitelistRuleDTO dto = new IpWhitelistRuleDTO();
        dto.setId(rule.getId());
        dto.setRuleCode(rule.getRuleCode());
        dto.setRuleName(rule.getRuleName());
        dto.setDescription(rule.getDescription());
        dto.setScope(rule.getScope());
        dto.setApiCodes(rule.getApiCodes());
        dto.setIpList(rule.getIpList());
        dto.setMatchMode(rule.getMatchMode());
        dto.setStatus(rule.getStatus());
        dto.setPriority(rule.getPriority());
        dto.setCreatedBy(rule.getCreatedBy());
        if (rule.getCreatedAt() != null) dto.setCreatedAt(rule.getCreatedAt().toString());
        if (rule.getUpdatedAt() != null) dto.setUpdatedAt(rule.getUpdatedAt().toString());

        // 统计关联接口数
        if (rule.getApiCodes() != null && !rule.getApiCodes().isEmpty()) {
            dto.setApiCount(rule.getApiCodes().split("[,;]").length);
        } else {
            dto.setApiCount(0);
        }
        return dto;
    }
}
