package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.annotation.RequirePermission;
import com.integration.config.enums.AppConstants;
import com.integration.config.service.BackupService;
import com.integration.config.vo.ResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 备份恢复 Controller
 * 全量配置 JSON 导出/导入
 */
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "备份恢复", description = "系统配置全量备份与恢复")
public class BackupController {

    private final BackupService backupService;

    /**
     * 导出全量配置为 JSON
     */
    @GetMapping("/export")
    @RequirePermission("system:backup:export")
    @AuditLog(operateType = "EXPORT", module = "SYSTEM", description = "'导出全量配置备份'")
    @Operation(summary = "导出全量配置", description = "导出所有系统配置为 JSON 文件")
    public void exportAll(HttpServletRequest request, HttpServletResponse response) {
        String userCode = (String) request.getAttribute(AppConstants.REQ_ATTR_USER_CODE);
        String json = backupService.exportAll(userCode);
        
        String filename = "config_backup_" + LocalDate.now() + ".json";
        try {
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" +
                    URLEncoder.encode(filename, StandardCharsets.UTF_8) + "\"; filename*=UTF-8''" +
                    URLEncoder.encode(filename, StandardCharsets.UTF_8));
            response.setContentLength(json.getBytes(StandardCharsets.UTF_8).length);
            response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new RuntimeException("导出文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取导出统计信息
     */
    @GetMapping("/stats")
    @RequirePermission("system:backup:export")
    @Operation(summary = "获取导出统计", description = "获取当前配置数据的统计信息")
    public ResultVO<Map<String, Long>> getStats() {
        return ResultVO.success(backupService.getExportStats());
    }

    /**
     * 导入全量配置
     */
    @PostMapping("/import")
    @RequirePermission("system:backup:import")
    @AuditLog(operateType = "IMPORT", module = "SYSTEM", description = "'导入全量配置备份，清空=' + #clearExisting")
    @Operation(summary = "导入全量配置", description = "从 JSON 文件导入配置，清空现有数据后重新导入")
    public ResultVO<BackupService.ImportResult> importAll(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "clearExisting", defaultValue = "true") boolean clearExisting) {
        
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的备份文件");
        }
        
        try {
            String json = new String(file.getBytes(), StandardCharsets.UTF_8);
            BackupService.ImportResult result = backupService.importAll(json, clearExisting);
            return ResultVO.success(result);
        } catch (Exception e) {
            throw new RuntimeException("导入失败: " + e.getMessage(), e);
        }
    }
}
