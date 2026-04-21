package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.annotation.RequirePermission;
import com.integration.config.dto.EnvironmentDTO;
import com.integration.config.service.EnvironmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 环境配置 REST API
 */
@RestController
@RequestMapping("/api/environment")
@RequiredArgsConstructor
public class EnvironmentController {

    private final EnvironmentService environmentService;

    /**
     * 创建环境配置
     */
    @PostMapping
    @RequirePermission("env:add")
    @AuditLog(operateType = "CREATE", module = "ENVIRONMENT", description = "'创建环境: ' + #dto.systemName + '/' + #dto.envName", targetType = "ENVIRONMENT", recordParams = true)
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody EnvironmentDTO dto) {
        try {
            EnvironmentDTO result = environmentService.create(dto);
            return ResponseEntity.ok(Map.of("code", 200, "message", "创建成功", "data", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "创建失败: " + e.getMessage()));
        }
    }

    /**
     * 更新环境配置
     */
    @PutMapping("/{id}")
    @RequirePermission("env:edit")
    @AuditLog(operateType = "UPDATE", module = "ENVIRONMENT", description = "'更新环境: ' + #dto.systemName + '/' + #dto.envName", targetType = "ENVIRONMENT", targetId = "#id", recordParams = true)
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody EnvironmentDTO dto) {
        try {
            EnvironmentDTO result = environmentService.update(id, dto);
            return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功", "data", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "更新失败: " + e.getMessage()));
        }
    }

    /**
     * 删除环境配置
     */
    @DeleteMapping("/{id}")
    @RequirePermission("env:delete")
    @AuditLog(operateType = "DELETE", module = "ENVIRONMENT", description = "'删除环境ID: ' + #id", targetType = "ENVIRONMENT", targetId = "#id", recordParams = true)
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        try {
            environmentService.delete(id);
            return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "删除失败: " + e.getMessage()));
        }
    }

    /**
     * 根据ID获取环境配置
     */
    @GetMapping("/{id}")
    @RequirePermission("env:detail")
    @AuditLog(operateType = "QUERY", module = "ENVIRONMENT", description = "'查询环境详情ID: ' + #id", targetType = "ENVIRONMENT", targetId = "#id")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        try {
            EnvironmentDTO result = environmentService.getById(id);
            return ResponseEntity.ok(Map.of("code", 200, "data", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "获取失败: " + e.getMessage()));
        }
    }

    /**
     * 分页查询环境配置
     */
    @GetMapping("/list")
    @RequirePermission("env:view")
    @AuditLog(operateType = "QUERY", module = "ENVIRONMENT", description = "'查询环境列表'", recordResult = false)
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String systemName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        try {
            Page<EnvironmentDTO> result = environmentService.list(systemName, status, PageRequest.of(page - 1, size));

            Map<String, Object> pageData = new HashMap<>();
            pageData.put("content", result.getContent());
            pageData.put("totalElements", result.getTotalElements());
            pageData.put("totalPages", result.getTotalPages());
            pageData.put("pageNumber", page);
            pageData.put("pageSize", size);

            return ResponseEntity.ok(Map.of("code", 200, "data", pageData));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "查询失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有系统名称列表（去重，用于接口配置分组下拉）
     */
    @GetMapping("/systems")
    @RequirePermission("env:view")
    @AuditLog(operateType = "QUERY", module = "ENVIRONMENT", description = "'查询系统名称列表'", recordResult = false)
    public ResponseEntity<Map<String, Object>> getAllSystems() {
        try {
            List<String> systems = environmentService.getAllSystemNames();
            return ResponseEntity.ok(Map.of("code", 200, "data", systems));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "查询失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定系统下的所有环境
     */
    @GetMapping("/by-system/{systemName}")
    @RequirePermission("env:view")
    @AuditLog(operateType = "QUERY", module = "ENVIRONMENT", description = "'查询系统环境: ' + #systemName", targetId = "#systemName")
    public ResponseEntity<Map<String, Object>> getBySystem(@PathVariable String systemName) {
        try {
            List<EnvironmentDTO> envs = environmentService.getBySystemName(systemName);
            return ResponseEntity.ok(Map.of("code", 200, "data", envs));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "查询失败: " + e.getMessage()));
        }
    }
}
