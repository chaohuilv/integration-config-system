package com.integration.config.controller;

import com.integration.config.annotation.RequirePermission;
import com.integration.config.dto.AuditSysLogDTO;
import com.integration.config.enums.ErrorCode;
import com.integration.config.exception.BusinessException;
import com.integration.config.service.AuditSysLogService;
import com.integration.config.vo.ResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计日志 Controller
 */
@RestController
@RequestMapping("/api/audit-log")
@RequiredArgsConstructor
@Tag(name = "审计日志", description = "审计日志的分页查询、批量删除、导出及详情查看")
public class AuditSysLogController {

    private final AuditSysLogService auditSysLogService;

    @GetMapping("/list")
    @RequirePermission("audit-log:view")
    @Operation(summary = "分页查询审计日志", description = "支持按用户、操作类型、模块、目标类型、结果、关键词、时间范围筛选")
    public ResultVO<Page<AuditSysLogDTO>> search(
            @RequestParam(required = false) String userCode,
            @RequestParam(required = false) String operateType,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AuditSysLogDTO> resultPage = auditSysLogService.search(
                userCode, operateType, module, targetType, result, keyword,
                startTime, endTime, page, size
        );
        return ResultVO.success(resultPage);
    }

    @PostMapping("/batch-delete")
    @RequirePermission("audit-log:delete")
    @Operation(summary = "批量删除审计日志", description = "根据传入的 ID 列表批量删除审计日志记录")
    public ResultVO<Integer> batchDelete(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "请选择要删除的记录");
        }
        int count = auditSysLogService.batchDelete(ids);
        return ResultVO.success("已删除 " + count + " 条记录", count);
    }

    @GetMapping("/export")
    @RequirePermission("audit-log:view")
    @Operation(summary = "导出审计日志", description = "按筛选条件导出审计日志列表（不分页），供前端转为 CSV/Excel")
    public ResultVO<List<AuditSysLogDTO>> export(
            @RequestParam(required = false) String userCode,
            @RequestParam(required = false) String operateType,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        List<AuditSysLogDTO> list = auditSysLogService.export(
                userCode, operateType, module, targetType, result, keyword, startTime, endTime
        );
        return ResultVO.success(list);
    }

    @GetMapping("/{id}")
    @RequirePermission("audit-log:detail")
    @Operation(summary = "获取审计日志详情", description = "根据 ID 获取单条审计日志的完整信息（含请求参数和响应结果）")
    public ResultVO<AuditSysLogDTO> getById(@PathVariable Long id) {
        AuditSysLogDTO dto = auditSysLogService.getById(id);
        if (dto == null) throw new BusinessException(ErrorCode.NOT_FOUND, "记录不存在");
        return ResultVO.success(dto);
    }
}
