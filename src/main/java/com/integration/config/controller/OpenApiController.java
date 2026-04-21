package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.dto.ApiConfigDTO;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.enums.ErrorCode;
import com.integration.config.exception.BusinessException;
import com.integration.config.service.ApiConfigService;
import com.integration.config.service.OpenApiService;
import com.integration.config.vo.ResultVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OpenAPI / Swagger 导入导出 Controller
 */
@RestController
@RequestMapping("/api/openapi")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OpenApiController {

    private final OpenApiService openApiService;
    private final ApiConfigService apiConfigService;

    /**
     * 解析 OpenAPI/Swagger JSON，返回预览列表
     */
    @PostMapping("/parse")
    @AuditLog(operateType = "IMPORT", module = "API_CONFIG", description = "'OpenAPI导入预览，共 ' + (#list?.size() ?: 0) + ' 个接口'", recordResult = false)
    public ResultVO<List<ApiConfigDTO>> parseOpenApi(@RequestBody Map<String, String> body) {
        String json = body.get("json");
        if (json == null || json.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "JSON 内容不能为空");
        }
        try {
            List<ApiConfigDTO> list = openApiService.parseOpenApi(json);
            return ResultVO.success("解析成功，共 " + list.size() + " 个接口", list);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "解析失败: " + e.getMessage());
        }
    }

    /**
     * 上传文件解析（支持 JSON）
     */
    @PostMapping("/upload")
    @AuditLog(operateType = "IMPORT", module = "API_CONFIG", description = "'上传文件解析 OpenAPI'", recordResult = false)
    public ResultVO<List<ApiConfigDTO>> uploadOpenApi(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "请选择文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".json"))) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "仅支持 .json 文件");
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<ApiConfigDTO> list = openApiService.parseOpenApi(content);
            return ResultVO.success("PARSE_SUCCESS", list);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "解析失败: " + e.getMessage());
        }
    }

    /**
     * 批量导入（预览后确认）
     */
    @PostMapping("/import")
    @AuditLog(operateType = "IMPORT", module = "API_CONFIG", description = "'批量导入 OpenAPI，共 ' + (#dtos?.size() ?: 0) + ' 个接口'", recordParams = true)
    public ResultVO<Map<String, Object>> batchImport(@RequestBody List<ApiConfigDTO> dtos, HttpServletRequest request) {
        if (dtos == null || dtos.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "没有要导入的接口");
        }
        Long userId = getUserId(request);
        String userName = getUserName(request);

        int success = 0;
        int skipped = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (ApiConfigDTO dto : dtos) {
            try {
                if (apiConfigService.existsByCode(dto.getCode())) {
                    // 编码冲突：追加时间戳后缀
                    dto.setCode(dto.getCode() + "-" + (System.currentTimeMillis() % 100000));
                    skipped++;
                }
                apiConfigService.create(dto, userId, userName);
                success++;
            } catch (Exception e) {
                errors.add(dto.getCode() + ": " + e.getMessage());
            }
        }

        return ResultVO.success(Map.of(
                "total", dtos.size(),
                "success", success,
                "skipped", skipped,
                "errors", errors
        ));
    }

    /**
     * 导出全部启用接口为 OpenAPI 3.0 JSON
     */
    @GetMapping("/export")
    @AuditLog(operateType = "EXPORT", module = "API_CONFIG", description = "'导出 OpenAPI 3.0（全部启用接口）'")
    public ResultVO<Map<String, String>> exportAll(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String serverUrl) {
        try {
            List<ApiConfig> apis = apiConfigService.getAllActive();
            String json = openApiService.exportToOpenApi3(apis, title, version, serverUrl);
            return ResultVO.success(Map.of("json", json));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出失败: " + e.getMessage());
        }
    }

    /**
     * 批量导出指定接口为 OpenAPI 3.0 JSON
     */
    @PostMapping("/export")
    @AuditLog(operateType = "EXPORT", module = "API_CONFIG", description = "'批量导出 OpenAPI（指定接口）'")
    public ResultVO<Map<String, String>> exportSelected(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) body.get("ids");
            String title = (String) body.getOrDefault("title", "API Export");
            String ver = (String) body.getOrDefault("version", "1.0.0");
            String srvUrl = (String) body.get("serverUrl");

            List<ApiConfig> apis;
            if (ids != null && !ids.isEmpty()) {
                apis = ids.stream()
                        .map(id -> {
                            try { return apiConfigService.getByIdForEntity(id); }
                            catch (Exception e) { return null; }
                        })
                        .filter(a -> a != null)
                        .collect(Collectors.toList());
            } else {
                apis = apiConfigService.getAllActive();
            }

            String json = openApiService.exportToOpenApi3(apis, title, ver, srvUrl);
            return ResultVO.success(Map.of("json", json));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出失败: " + e.getMessage());
        }
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        return uid instanceof Long ? (Long) uid : null;
    }

    private String getUserName(HttpServletRequest request) {
        Object uname = request.getAttribute("userName");
        return uname instanceof String ? (String) uname : "system";
    }
}
