package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.annotation.RequirePermission;
import com.integration.config.dto.TestBatchRunDTO;
import com.integration.config.dto.TestRunRequestDTO;
import com.integration.config.dto.TestStepResultDTO;
import com.integration.config.entity.log.TestExecution;
import com.integration.config.enums.AppConstants;
import com.integration.config.service.TestExecutionService;
import com.integration.config.vo.ResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 测试执行 Controller
 * 负责用例执行、套件执行、批量执行、执行记录查询
 */
@Tag(name = "测试执行", description = "测试用例执行、套件执行、执行记录查询")
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final TestExecutionService testExecutionService;

    /**
     * 执行单个测试用例
     */
    @Operation(summary = "执行单个测试用例")
    @PostMapping("/execute/case")
    @RequirePermission("test:execute")
    @AuditLog(operateType = "EXECUTE", module = "TEST_EXECUTION", description = "'执行测试用例: ' + #request.caseCode", targetType = "TEST_EXECUTION", recordParams = true)
    public ResultVO<TestExecution> executeCase(
            @RequestBody TestRunRequestDTO request,
            @RequestAttribute(AppConstants.REQ_ATTR_USER_CODE) String userCode) {
        TestExecution execution = testExecutionService.executeCase(request, userCode);
        return ResultVO.success(execution);
    }

    /**
     * 执行测试套件
     */
    @Operation(summary = "执行测试套件")
    @PostMapping("/execute/suite/{suiteId}")
    @RequirePermission("test:execute")
    @AuditLog(operateType = "EXECUTE", module = "TEST_EXECUTION", description = "'执行测试套件, ID: ' + #suiteId", targetType = "TEST_EXECUTION", targetId = "#suiteId")
    public ResultVO<List<TestExecution>> executeSuite(
            @Parameter(description = "套件ID") @PathVariable Long suiteId,
            @RequestAttribute(AppConstants.REQ_ATTR_USER_CODE) String userCode) {
        List<TestExecution> executions = testExecutionService.executeSuite(suiteId, userCode);
        return ResultVO.success(executions);
    }

    /**
     * 批量执行
     */
    @Operation(summary = "批量执行测试")
    @PostMapping("/execute/batch")
    @RequirePermission("test:execute")
    @AuditLog(operateType = "EXECUTE", module = "TEST_EXECUTION", description = "'批量执行测试'", targetType = "TEST_EXECUTION", recordParams = true)
    public ResultVO<List<TestExecution>> executeBatch(
            @RequestBody TestBatchRunDTO request,
            @RequestAttribute(AppConstants.REQ_ATTR_USER_CODE) String userCode) {
        List<TestExecution> executions = testExecutionService.executeBatch(request, userCode);
        return ResultVO.success(executions);
    }

    /**
     * 分页查询执行记录
     */
    @Operation(summary = "分页查询执行记录")
    @GetMapping("/execution/list")
    @RequirePermission("test:execution:view")
    @AuditLog(operateType = "QUERY", module = "TEST_EXECUTION", description = "'查询执行记录分页'", recordResult = false)
    public ResultVO<Page<TestExecution>> listExecutions(
            @Parameter(description = "用例ID") @RequestParam(required = false) Long testCaseId,
            @Parameter(description = "套件ID") @RequestParam(required = false) Long testSuiteId,
            @Parameter(description = "状态：PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/SKIPPED") @RequestParam(required = false) String status,
            @Parameter(description = "触发来源：MANUAL/SCHEDULED/API") @RequestParam(required = false) String triggerSource,
            @Parameter(description = "关键词（用例编码）") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        Page<TestExecution> result = testExecutionService.pageQuery(testCaseId, testSuiteId, status, 
                triggerSource, keyword, page, size);
        return ResultVO.success(result);
    }

    /**
     * 获取执行详情
     */
    @Operation(summary = "获取执行详情")
    @GetMapping("/execution/{id}")
    @RequirePermission("test:execution:view")
    @AuditLog(operateType = "QUERY", module = "TEST_EXECUTION", description = "'查询执行详情, ID: ' + #id", recordResult = false)
    public ResultVO<TestExecution> getExecution(
            @Parameter(description = "执行记录ID") @PathVariable Long id) {
        return ResultVO.success(testExecutionService.getExecution(id));
    }

    /**
     * 获取执行步骤结果
     */
    @Operation(summary = "获取执行步骤结果")
    @GetMapping("/execution/{id}/steps")
    @RequirePermission("test:execution:view")
    @AuditLog(operateType = "QUERY", module = "TEST_EXECUTION", description = "'查询执行步骤结果, ID: ' + #id", recordResult = false)
    public ResultVO<List<TestStepResultDTO>> getStepResults(
            @Parameter(description = "执行记录ID") @PathVariable Long id) {
        return ResultVO.success(testExecutionService.getStepResults(id));
    }

    /**
     * 获取用例最近执行记录
     */
    @Operation(summary = "获取用例最近执行记录")
    @GetMapping("/execution/case/{caseId}/recent")
    @RequirePermission("test:execution:view")
    @AuditLog(operateType = "QUERY", module = "TEST_EXECUTION", description = "'查询用例最近执行记录, caseId: ' + #caseId", recordResult = false)
    public ResultVO<List<TestExecution>> getRecentExecutionsByCase(
            @Parameter(description = "用例ID") @PathVariable Long caseId,
            @Parameter(description = "限制数量") @RequestParam(defaultValue = "10") int limit) {
        return ResultVO.success(testExecutionService.getRecentExecutionsByCase(caseId, limit));
    }

    /**
     * 删除执行记录
     */
    @Operation(summary = "删除执行记录")
    @DeleteMapping("/execution/{id}")
    @RequirePermission("test:execution:delete")
    @AuditLog(operateType = "DELETE", module = "TEST_EXECUTION", description = "'删除执行记录, ID: ' + #id", targetType = "TEST_EXECUTION", targetId = "#id")
    public ResultVO<Void> deleteExecution(
            @Parameter(description = "执行记录ID") @PathVariable Long id) {
        testExecutionService.deleteExecution(id);
        return ResultVO.success();
    }
}
