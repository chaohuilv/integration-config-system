package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.dto.AuditSysLogDTO;
import com.integration.config.service.AuditSysLogService;
import com.integration.config.util.Result;
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
public class AuditSysLogController {

    private final AuditSysLogService auditSysLogService;

    /**
     * 分页查询 + 联合筛选
     */
    @GetMapping("/list")
    //@AuditLog(operateType = "QUERY", module = "AUDIT_LOG", description = "'查询审计日志列表'", recordResult = false)
    public Result<Page<AuditSysLogDTO>> search(
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
        return Result.success(resultPage);
    }

    /**
     * 批量删除
     */
    @PostMapping("/batch-delete")
    //@AuditLog(operateType = "DELETE", module = "AUDIT_LOG", description = "'批量删除审计日志'", recordParams = true)
    public Result<Integer> batchDelete(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要删除的记录");
        }
        int count = auditSysLogService.batchDelete(ids);
        return Result.success("已删除 " + count + " 条记录", count);
    }

    /**
     * 导出（返回列表，可前端转 CSV/Excel）
     */
    @GetMapping("/export")
    //@AuditLog(operateType = "EXPORT", module = "AUDIT_LOG", description = "'导出审计日志'", recordResult = false)
    public Result<List<AuditSysLogDTO>> export(
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
        return Result.success(list);
    }

    /**
     * 详情
     */
    @GetMapping("/{id}")
    //@AuditLog(operateType = "QUERY", module = "AUDIT_LOG", description = "'查询审计日志详情ID: ' + #id", targetId = "#id")
    public Result<AuditSysLogDTO> getById(@PathVariable Long id) {
        AuditSysLogDTO dto = auditSysLogService.getById(id);
        if (dto == null) return Result.error("记录不存在");
        return Result.success(dto);
    }
}
