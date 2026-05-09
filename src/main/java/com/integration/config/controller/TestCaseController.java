package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.annotation.RequirePermission;
import com.integration.config.dto.TestCaseDetailDTO;
import com.integration.config.dto.TestCaseListDTO;
import com.integration.config.dto.TestCaseSaveDTO;
import com.integration.config.dto.TestCaseStepDTO;
import com.integration.config.entity.config.TestCase;
import com.integration.config.service.TestCaseService;
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
 * 测试用例管理 Controller
 */
@Tag(name = "测试用例管理", description = "测试用例的增删改查及步骤管理")
@RestController
@RequestMapping("/api/test/case")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;

    /**
     * 分页查询测试用例
     */
    @Operation(summary = "分页查询测试用例")
    @GetMapping("/list")
    @RequirePermission("test:case:view")
    @AuditLog(operateType = "QUERY", module = "TEST_CASE", description = "'查询测试用例分页'", recordResult = false)
    public ResultVO<Page<TestCaseListDTO>> list(
            @Parameter(description = "分组名称") @RequestParam(required = false) String groupName,
            @Parameter(description = "状态：ACTIVE/INACTIVE") @RequestParam(required = false) String status,
            @Parameter(description = "关键词（编码/名称）") @RequestParam(required = false) String keyword,
            @Parameter(description = "标签") @RequestParam(required = false) String tags,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        Page<TestCaseListDTO> result = testCaseService.pageQuery(groupName, status, keyword, tags, page, size);
        return ResultVO.success(result);
    }

    /**
     * 获取用例详情
     */
    @Operation(summary = "获取用例详情")
    @GetMapping("/{id}")
    @RequirePermission("test:case:view")
    @AuditLog(operateType = "QUERY", module = "TEST_CASE", description = "'查询用例详情, ID: ' + #id", recordResult = false)
    public ResultVO<TestCaseDetailDTO> getDetail(
            @Parameter(description = "用例ID") @PathVariable Long id) {
        return ResultVO.success(testCaseService.getDetail(id));
    }

    /**
     * 根据编码获取详情
     */
    @Operation(summary = "根据编码获取用例详情")
    @GetMapping("/code/{code}")
    @RequirePermission("test:case:view")
    @AuditLog(operateType = "QUERY", module = "TEST_CASE", description = "'根据编码查询用例详情: ' + #code", recordResult = false)
    public ResultVO<TestCaseDetailDTO> getDetailByCode(
            @Parameter(description = "用例编码") @PathVariable String code) {
        return ResultVO.success(testCaseService.getDetailByCode(code));
    }

    /**
     * 创建测试用例
     */
    @Operation(summary = "创建测试用例")
    @PostMapping
    @RequirePermission("test:case:create")
    @AuditLog(operateType = "CREATE", module = "TEST_CASE", description = "'创建测试用例: ' + #dto.code + '/' + #dto.name", targetType = "TEST_CASE", recordParams = true)
    public ResultVO<TestCase> create(@Valid @RequestBody TestCaseSaveDTO dto) {
        return ResultVO.success(testCaseService.create(dto));
    }

    /**
     * 更新测试用例
     */
    @Operation(summary = "更新测试用例")
    @PutMapping("/{id}")
    @RequirePermission("test:case:update")
    @AuditLog(operateType = "UPDATE", module = "TEST_CASE", description = "'更新测试用例: ' + #dto.name", targetType = "TEST_CASE", targetId = "#id", recordParams = true)
    public ResultVO<TestCase> update(
            @Parameter(description = "用例ID") @PathVariable Long id,
            @Valid @RequestBody TestCaseSaveDTO dto) {
        return ResultVO.success(testCaseService.update(id, dto));
    }

    /**
     * 删除测试用例
     */
    @Operation(summary = "删除测试用例")
    @DeleteMapping("/{id}")
    @RequirePermission("test:case:delete")
    @AuditLog(operateType = "DELETE", module = "TEST_CASE", description = "'删除测试用例, ID: ' + #id", targetType = "TEST_CASE", targetId = "#id")
    public ResultVO<Void> delete(
            @Parameter(description = "用例ID") @PathVariable Long id) {
        testCaseService.delete(id);
        return ResultVO.success();
    }

    /**
     * 切换启用/禁用状态
     */
    @Operation(summary = "切换用例状态")
    @PostMapping("/{id}/toggle")
    @RequirePermission("test:case:update")
    @AuditLog(operateType = "UPDATE", module = "TEST_CASE", description = "'切换测试用例状态, ID: ' + #id", targetType = "TEST_CASE", targetId = "#id")
    public ResultVO<TestCase> toggleStatus(
            @Parameter(description = "用例ID") @PathVariable Long id) {
        return ResultVO.success(testCaseService.toggleStatus(id));
    }

    /**
     * 获取所有分组名称
     */
    @Operation(summary = "获取所有分组名称")
    @GetMapping("/groups")
    @RequirePermission("test:case:view")
    @AuditLog(operateType = "QUERY", module = "TEST_CASE", description = "'查询所有用例分组'", recordResult = false)
    public ResultVO<List<String>> getGroups() {
        return ResultVO.success(testCaseService.getGroups());
    }

    /**
     * 获取所有启用的用例（用于套件选择）
     */
    @Operation(summary = "获取所有启用的用例")
    @GetMapping("/active")
    @RequirePermission("test:case:view")
    @AuditLog(operateType = "QUERY", module = "TEST_CASE", description = "'查询所有启用用例'", recordResult = false)
    public ResultVO<List<TestCaseListDTO>> getActiveCases() {
        return ResultVO.success(testCaseService.getActiveCases());
    }

    /**
     * 保存用例步骤（全量替换）
     */
    @Operation(summary = "保存用例步骤")
    @PostMapping("/{id}/steps")
    @RequirePermission("test:case:update")
    @AuditLog(operateType = "UPDATE", module = "TEST_CASE", description = "'保存测试用例步骤, ID: ' + #id", targetType = "TEST_CASE", targetId = "#id", recordParams = true)
    public ResultVO<Void> saveSteps(
            @Parameter(description = "用例ID") @PathVariable Long id,
            @RequestBody List<TestCaseStepDTO> steps) {
        testCaseService.saveSteps(id, steps);
        return ResultVO.success();
    }

    /**
     * 获取用例步骤
     */
    @Operation(summary = "获取用例步骤")
    @GetMapping("/{id}/steps")
    @RequirePermission("test:case:view")
    @AuditLog(operateType = "QUERY", module = "TEST_CASE", description = "'查询用例步骤, ID: ' + #id", recordResult = false)
    public ResultVO<List<TestCaseStepDTO>> getSteps(
            @Parameter(description = "用例ID") @PathVariable Long id) {
        return ResultVO.success(testCaseService.getSteps(id));
    }
}
