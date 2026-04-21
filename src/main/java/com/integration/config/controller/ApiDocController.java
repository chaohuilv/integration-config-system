package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.annotation.RequirePermission;
import com.integration.config.service.ApiDocExportService;
import com.integration.config.service.ApiDocService;
import com.integration.config.vo.ResultVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API 文档 Controller
 * 提供系统内接口文档的展示
 */
@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "ApiDoc", description = "API 文档")
public class ApiDocController {

    private final ApiDocService apiDocService;
    private final ApiDocExportService apiDocExportService;

    /**
     * 获取所有接口文档（按分组）
     */
    @GetMapping("/list")
    @RequirePermission("api:view")
    @AuditLog(operateType = "QUERY", module = "API_DOC", description = "'查看 API 文档列表'")
    public ResultVO<List<ApiDocService.ApiDocGroup>> list() {
        return ResultVO.success(apiDocService.getAllGroupedDocs());
    }

    /**
     * 获取单个接口的完整文档
     */
    @GetMapping("/{id}")
    @RequirePermission("api:detail")
    @AuditLog(operateType = "QUERY", module = "API_DOC", description = "'查看接口文档: ' + #id", targetId = "#id")
    public ResultVO<ApiDocService.ApiDocDetail> detail(@PathVariable Long id) {
        return ResultVO.success(apiDocService.getDocDetail(id));
    }

    /**
     * 导出全部接口文档为 Word
     */
    @GetMapping("/export")
    @RequirePermission("api:export")
    @Operation(summary = "导出全部接口文档（Word）")
    @AuditLog(operateType = "EXPORT", module = "API_DOC", description = "'导出全部接口文档为'")
    public void exportAll(HttpServletResponse response) {
        byte[] doc = apiDocExportService.exportAll();
        writeDocx(response, doc, "接口文档_" + LocalDate.now() + ".docx");
    }

    /**
     * 导出指定分组的接口文档为 Word
     */
    @GetMapping("/export/group")
    @RequirePermission("api:export")
    @Operation(summary = "导出指定分组接口文档（Word）")
    @AuditLog(operateType = "EXPORT", module = "API_DOC", description = "'导出指定分组接口文档，groupName='+ #groupName", targetId = "#groupName")
    public void exportGroup(@RequestParam(required = false) String groupName, HttpServletResponse response) {
        byte[] doc = apiDocExportService.exportGroup(groupName);
        String filename = (groupName != null && !groupName.isEmpty())
                ? "接口文档_" + groupName + "_" + LocalDate.now() + ".docx"
                : "接口文档_" + LocalDate.now() + ".docx";
        writeDocx(response, doc, filename);
    }

    private void writeDocx(HttpServletResponse response, byte[] doc, String filename) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            response.setHeader("Content-Disposition", "attachment; filename=\"" +
                    URLEncoder.encode(filename, StandardCharsets.UTF_8) + "\"; filename*=UTF-8''" +
                    URLEncoder.encode(filename, StandardCharsets.UTF_8));
            response.setContentLength(doc.length);
            response.getOutputStream().write(doc);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new RuntimeException("输出 Word 文件失败: " + e.getMessage(), e);
        }
    }
}
