package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.annotation.RequirePermission;
import com.integration.config.dto.TestSuiteDetailDTO;
import com.integration.config.dto.TestSuiteListDTO;
import com.integration.config.dto.TestSuiteSaveDTO;
import com.integration.config.entity.config.TestSuite;
import com.integration.config.service.TestSuiteService;
import com.integration.config.vo.ResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 测试套件管理 Controller
 */
@Tag(name = "测试套件管理", description = "测试套件的增删改查及用例关联管理")
@RestController
@RequestMapping("/api/test/suite")
@RequiredArgsConstructor
public class TestSuiteController {

    private final TestSuiteService testSuiteService;

    /**
     * 分页查询测试套件
     */
    @Operation(summary = "分页查询测试套件")
    @GetMapping("/list")
    @RequirePermission("test:suite:view")
    @AuditLog(operateType = "QUERY", module = "TEST_SUITE", description = "'查询测试套件分页'", recordResult = false)
    public ResultVO<Page<TestSuiteListDTO>> list(
            @Parameter(description = "分组名称") @RequestParam(required = false) String groupName,
            @Parameter(description = "状态：ACTIVE/INACTIVE") @RequestParam(required = false) String status,
            @Parameter(description = "关键词（编码/名称）") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        Page<TestSuiteListDTO> result = testSuiteService.pageQuery(groupName, status, keyword, page, size);
        return ResultVO.success(result);
    }

    /**
     * 获取套件详情
     */
    @Operation(summary = "获取套件详情")
    @GetMapping("/{id}")
    @RequirePermission("test:suite:view")
    @AuditLog(operateType = "QUERY", module = "TEST_SUITE", description = "'查询套件详情, ID: ' + #id", recordResult = false)
    public ResultVO<TestSuiteDetailDTO> getDetail(
            @Parameter(description = "套件ID") @PathVariable Long id) {
        return ResultVO.success(testSuiteService.getDetail(id));
    }

    /**
     * 根据编码获取详情
     */
    @Operation(summary = "根据编码获取套件详情")
    @GetMapping("/code/{code}")
    @RequirePermission("test:suite:view")
    @AuditLog(operateType = "QUERY", module = "TEST_SUITE", description = "'根据编码查询套件详情: ' + #code", recordResult = false)
    public ResultVO<TestSuiteDetailDTO> getDetailByCode(
            @Parameter(description = "套件编码") @PathVariable String code) {
        return ResultVO.success(testSuiteService.getDetailByCode(code));
    }

    /**
     * 创建测试套件
     */
    @Operation(summary = "创建测试套件")
    @PostMapping
    @RequirePermission("test:suite:create")
    @AuditLog(operateType = "CREATE", module = "TEST_SUITE", description = "'创建测试套件: ' + #dto.code + '/' + #dto.name", targetType = "TEST_SUITE", recordParams = true)
    public ResultVO<TestSuite> create(@Valid @RequestBody TestSuiteSaveDTO dto) {
        return ResultVO.success(testSuiteService.create(dto));
    }

    /**
     * 更新测试套件
     */
    @Operation(summary = "更新测试套件")
    @PutMapping("/{id}")
    @RequirePermission("test:suite:update")
    @AuditLog(operateType = "UPDATE", module = "TEST_SUITE", description = "'更新测试套件: ' + #dto.name", targetType = "TEST_SUITE", targetId = "#id", recordParams = true)
    public ResultVO<TestSuite> update(
            @Parameter(description = "套件ID") @PathVariable Long id,
            @Valid @RequestBody TestSuiteSaveDTO dto) {
        return ResultVO.success(testSuiteService.update(id, dto));
    }

    /**
     * 删除测试套件
     */
    @Operation(summary = "删除测试套件")
    @DeleteMapping("/{id}")
    @RequirePermission("test:suite:delete")
    @AuditLog(operateType = "DELETE", module = "TEST_SUITE", description = "'删除测试套件, ID: ' + #id", targetType = "TEST_SUITE", targetId = "#id")
    public ResultVO<Void> delete(
            @Parameter(description = "套件ID") @PathVariable Long id) {
        testSuiteService.delete(id);
        return ResultVO.success();
    }

    /**
     * 切换启用/禁用状态
     */
    @Operation(summary = "切换套件状态")
    @PostMapping("/{id}/toggle")
    @RequirePermission("test:suite:update")
    @AuditLog(operateType = "UPDATE", module = "TEST_SUITE", description = "'切换测试套件状态, ID: ' + #id", targetType = "TEST_SUITE", targetId = "#id")
    public ResultVO<TestSuite> toggleStatus(
            @Parameter(description = "套件ID") @PathVariable Long id) {
        return ResultVO.success(testSuiteService.toggleStatus(id));
    }

    /**
     * 获取所有分组名称
     */
    @Operation(summary = "获取所有分组名称")
    @GetMapping("/groups")
    @RequirePermission("test:suite:view")
    @AuditLog(operateType = "QUERY", module = "TEST_SUITE", description = "'查询所有套件分组'", recordResult = false)
    public ResultVO<List<String>> getGroups() {
        return ResultVO.success(testSuiteService.getGroups());
    }

    /**
     * 获取所有启用的套件
     */
    @Operation(summary = "获取所有启用的套件")
    @GetMapping("/active")
    @RequirePermission("test:suite:view")
    @AuditLog(operateType = "QUERY", module = "TEST_SUITE", description = "'查询所有启用套件'", recordResult = false)
    public ResultVO<List<TestSuiteListDTO>> getActiveSuites() {
        return ResultVO.success(testSuiteService.getActiveSuites());
    }

    /**
     * 保存套件-用例关联（全量替换）
     */
    @Operation(summary = "保存套件用例关联")
    @PostMapping("/{id}/cases")
    @RequirePermission("test:suite:update")
    @AuditLog(operateType = "UPDATE", module = "TEST_SUITE", description = "'保存套件用例关联, ID: ' + #id", targetType = "TEST_SUITE", targetId = "#id", recordParams = true)
    public ResultVO<Void> saveCases(
            @Parameter(description = "套件ID") @PathVariable Long id,
            @RequestBody List<Long> caseIds) {
        testSuiteService.saveCases(id, caseIds);
        return ResultVO.success();
    }

    /**
     * 获取套件下的用例ID列表
     */
    @Operation(summary = "获取套件用例列表")
    @GetMapping("/{id}/cases")
    @RequirePermission("test:suite:view")
    @AuditLog(operateType = "QUERY", module = "TEST_SUITE", description = "'查询套件用例列表, ID: ' + #id", recordResult = false)
    public ResultVO<List<Long>> getCaseIds(
            @Parameter(description = "套件ID") @PathVariable Long id) {
        return ResultVO.success(testSuiteService.getCaseIds(id));
    }
}
