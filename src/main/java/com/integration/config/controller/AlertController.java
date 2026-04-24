package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.annotation.RequirePermission;
import com.integration.config.dto.AlertRuleDTO;
import com.integration.config.entity.config.AlertRecord;
import com.integration.config.entity.config.AlertRule;
import com.integration.config.enums.AppConstants;
import com.integration.config.service.AlertRuleService;
import com.integration.config.vo.ResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警管理 Controller
 */
@RestController
@RequestMapping("/api/alert")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "告警管理", description = "告警规则与告警记录管理")
public class AlertController {

    private final AlertRuleService alertRuleService;

    // ==================== 告警规则 ====================

    @GetMapping("/rules")
    @Operation(summary = "分页查询告警规则")
    @RequirePermission("alert:view")
    @AuditLog(operateType = "QUERY", module = "ALERT_RULE", description = "'分页查询告警规则'", recordResult = false)
    public ResultVO<Page<AlertRule>> pageRules(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResultVO.success(alertRuleService.pageQuery(keyword, alertType, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/rules/active")
    @Operation(summary = "查询所有启用的告警规则")
    @RequirePermission("alert:view")
    @AuditLog(operateType = "QUERY", module = "ALERT_RULE", description = "'查询所有启用的告警规则'", recordResult = false)
    public ResultVO<List<AlertRule>> getActiveRules() {
        return ResultVO.success(alertRuleService.getAllActive());
    }

    @GetMapping("/rules/{id}")
    @Operation(summary = "查询告警规则详情")
    @RequirePermission("alert:view")
    @AuditLog(operateType = "QUERY", module = "ALERT_RULE", description = "'查询告警规则详情ID: ' + #id", targetType = "ALERT_RULE", targetId = "#id", recordResult = false)
    public ResultVO<AlertRule> getRule(@PathVariable Long id) {
        return ResultVO.success(alertRuleService.getById(id));
    }

    @PostMapping("/rules")
    @Operation(summary = "创建告警规则")
    @RequirePermission("alert:add")
    @AuditLog(operateType = "CREATE", module = "ALERT_RULE", description = "'创建告警规则: ' + #dto.ruleName", targetType = "ALERT_RULE", targetId = "#result.data.id", recordParams = true)
    public ResultVO<AlertRule> createRule(@RequestBody AlertRuleDTO dto,
                                          @RequestAttribute(AppConstants.REQ_ATTR_USER_ID) Long userId) {
        log.info("[AlertController] 创建告警规则: {}", dto.getRuleName());
        AlertRule created = alertRuleService.create(dto, userId);
        return ResultVO.success(created);
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "更新告警规则")
    @RequirePermission("alert:edit")
    @AuditLog(operateType = "UPDATE", module = "ALERT_RULE", description = "'更新告警规则ID: ' + #id", targetType = "ALERT_RULE", targetId = "#id", recordParams = true)
    public ResultVO<AlertRule> updateRule(@PathVariable Long id, @RequestBody AlertRuleDTO dto) {
        return ResultVO.success(alertRuleService.update(id, dto));
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "删除告警规则")
    @RequirePermission("alert:delete")
    @AuditLog(operateType = "DELETE", module = "ALERT_RULE", description = "'删除告警规则ID: ' + #id", targetType = "ALERT_RULE", targetId = "#id", recordParams = false)
    public ResultVO<Void> deleteRule(@PathVariable Long id) {
        alertRuleService.delete(id);
        return ResultVO.success();
    }

    @PostMapping("/rules/{id}/toggle")
    @Operation(summary = "启用/停用告警规则")
    @RequirePermission("alert:edit")
    @AuditLog(operateType = "UPDATE", module = "ALERT_RULE", description = "'切换告警规则状态: ' + #id", targetType = "ALERT_RULE", targetId = "#id", recordParams = false)
    public ResultVO<AlertRule> toggleRule(@PathVariable Long id) {
        return ResultVO.success(alertRuleService.toggleStatus(id));
    }

    @PostMapping("/rules/{id}/test")
    @Operation(summary = "测试告警规则")
    @RequirePermission("alert:edit")
    public ResultVO<Void> testRule(@PathVariable Long id) {
        alertRuleService.testAlert(id);
        return ResultVO.success("测试告警已记录，可前往告警记录页面查看");
    }

    @PostMapping("/rules/{id}/evaluate")
    @Operation(summary = "手动触发规则立即评估")
    @RequirePermission("alert:edit")
    public ResultVO<Map<String, Object>> evaluateNow(@PathVariable Long id) {
        boolean triggered = alertRuleService.evaluateNow(id);
        Map<String, Object> map = new HashMap<>();
        map.put("ruleId", id);
        map.put("triggered", triggered);
        map.put("message", triggered ? "告警已触发" : "当前指标正常，未触发告警");
        return ResultVO.success(map);
    }

    // ==================== 告警记录 ====================

    @GetMapping("/records")
    @Operation(summary = "分页查询告警记录")
    @RequirePermission("alert:view")
    @AuditLog(operateType = "QUERY", module = "ALERT_RULE", description = "'分页查询告警记录'", recordResult = false)
    public ResultVO<Page<AlertRecord>> pageRecords(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResultVO.success(alertRuleService.pageQueryRecords(keyword, status, alertType, startTime, endTime,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "alertTime"))));
    }

    @PostMapping("/records/{id}/acknowledge")
    @Operation(summary = "确认告警")
    @RequirePermission("alert:edit")
    public ResultVO<Void> acknowledge(@PathVariable Long id,
                                    @RequestAttribute(AppConstants.REQ_ATTR_USER_CODE) String userCode) {
        alertRuleService.acknowledge(id, userCode);
        return ResultVO.success();
    }

    @PostMapping("/records/{id}/resolve")
    @Operation(summary = "标记告警为已解决")
    @RequirePermission("alert:edit")
    public ResultVO<Void> resolve(@PathVariable Long id) {
        alertRuleService.resolve(id);
        return ResultVO.success();
    }

    // ==================== 概览统计 ====================

    @GetMapping("/overview")
    @Operation(summary = "告警概览统计")
    @RequirePermission("alert:view")
    @AuditLog(operateType = "QUERY", module = "ALERT_RULE", description = "'查询告警概览统计'", recordResult = false)
    public ResultVO<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();
        data.put("firingCount", alertRuleService.countFiring());
        data.put("activeRules", alertRuleService.getAllActive().size());
        return ResultVO.success(data);
    }
}
