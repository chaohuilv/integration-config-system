package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.annotation.RequirePermission;
import com.integration.config.dto.ScenarioDTO;
import com.integration.config.dto.ScenarioExecuteRequestDTO;
import com.integration.config.dto.ScenarioExecuteResultDTO;
import com.integration.config.dto.ScenarioStepDTO;
import com.integration.config.entity.config.Scenario;
import com.integration.config.entity.config.ScenarioStep;
import com.integration.config.entity.log.ScenarioExecution;
import com.integration.config.entity.log.ScenarioStepLog;
import com.integration.config.enums.AppConstants;
import com.integration.config.repository.log.ScenarioExecutionRepository;
import com.integration.config.repository.log.ScenarioStepLogRepository;
import com.integration.config.service.ScenarioExecutionService;
import com.integration.config.service.ScenarioService;
import com.integration.config.vo.ResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 场景编排 Controller
 */
@RestController
@RequestMapping("/api/scenario")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "场景编排", description = "场景编排管理与执行")
public class ScenarioController {

    private final ScenarioService scenarioService;
    private final ScenarioExecutionService scenarioExecutionService;
    private final ScenarioExecutionRepository scenarioExecutionRepository;
    private final ScenarioStepLogRepository scenarioStepLogRepository;

    // ==================== 场景管理 ====================

    @GetMapping("/list")
    @Operation(summary = "分页查询场景列表")
    @RequirePermission("scenario:view")
    @AuditLog(operateType = "QUERY", module = "SCENARIO", description = "'分页查询场景列表'", recordResult = false)
    public ResultVO<Page<Scenario>> pageList(
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResultVO.success(scenarioService.pageQuery(groupName, status, keyword,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询场景详情")
    @RequirePermission("scenario:view")
    @AuditLog(operateType = "QUERY", module = "SCENARIO", description = "'查询场景详情ID: ' + #id", targetType = "SCENARIO", targetId = "#id", recordResult = false)
    public ResultVO<ScenarioDTO> getDetail(@PathVariable Long id) {
        return ResultVO.success(scenarioService.getDetail(id));
    }

    @GetMapping("/groups")
    @Operation(summary = "查询所有分组名称")
    @RequirePermission("scenario:view")
    @AuditLog(operateType = "QUERY", module = "SCENARIO", description = "'查询场景分组名称列表'", recordResult = false)
    public ResultVO<List<String>> getGroupNames() {
        return ResultVO.success(scenarioService.getGroupNames());
    }

    @GetMapping("/active")
    @Operation(summary = "查询所有启用的场景")
    @RequirePermission("scenario:view")
    @AuditLog(operateType = "QUERY", module = "SCENARIO", description = "'查询所有启用的场景'", recordResult = false)
    public ResultVO<List<Scenario>> getActiveScenarios() {
        return ResultVO.success(scenarioService.getActiveScenarios());
    }

    @PostMapping
    @Operation(summary = "创建场景")
    @RequirePermission("scenario:add")
    @AuditLog(operateType = "CREATE", module = "SCENARIO", description = "'创建场景: ' + #body['code']", targetType = "SCENARIO", targetId = "#result.data.id", recordParams = true)
    public ResultVO<Scenario> create(
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AppConstants.REQ_ATTR_USER_ID) Long userId,
            @RequestAttribute(AppConstants.REQ_ATTR_USER_CODE) String userCode) {
        ScenarioDTO dto = parseScenarioDTO(body);
        List<ScenarioStepDTO> steps = parseStepDTOs(body);
        Scenario created = scenarioService.create(dto, steps, userId, userCode);
        return ResultVO.success(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新场景")
    @RequirePermission("scenario:edit")
    @AuditLog(operateType = "UPDATE", module = "SCENARIO", description = "'更新场景ID: ' + #id", targetType = "SCENARIO", targetId = "#id", recordParams = true)
    public ResultVO<Scenario> update(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AppConstants.REQ_ATTR_USER_ID) Long userId,
            @RequestAttribute(AppConstants.REQ_ATTR_USER_CODE) String userCode) {
        ScenarioDTO dto = parseScenarioDTO(body);
        List<ScenarioStepDTO> steps = parseStepDTOs(body);
        Scenario updated = scenarioService.update(id, dto, steps, userId, userCode);
        return ResultVO.success(updated);
    }

    @PutMapping("/{id}/toggle")
    @Operation(summary = "切换场景状态")
    @RequirePermission("scenario:edit")
    @AuditLog(operateType = "UPDATE", module = "SCENARIO", description = "'切换场景状态: ' + #id", targetType = "SCENARIO", targetId = "#id", recordParams = false)
    public ResultVO<Scenario> toggleStatus(
            @PathVariable Long id,
            @RequestAttribute(AppConstants.REQ_ATTR_USER_ID) Long userId,
            @RequestAttribute(AppConstants.REQ_ATTR_USER_CODE) String userCode) {
        return ResultVO.success(scenarioService.toggleStatus(id, userId, userCode));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除场景")
    @RequirePermission("scenario:delete")
    @AuditLog(operateType = "DELETE", module = "SCENARIO", description = "'删除场景ID: ' + #id", targetType = "SCENARIO", targetId = "#id", recordParams = false)
    public ResultVO<Void> delete(@PathVariable Long id) {
        scenarioService.delete(id);
        return ResultVO.success(null);
    }

    // ==================== 步骤管理 ====================

    @GetMapping("/{scenarioId}/steps")
    @Operation(summary = "查询场景步骤列表")
    @RequirePermission("scenario:view")
    @AuditLog(operateType = "QUERY", module = "SCENARIO", description = "'查询场景步骤列表scenarioId: ' + #scenarioId", targetType = "SCENARIO", targetId = "#scenarioId", recordResult = false)
    public ResultVO<List<ScenarioStepDTO>> getSteps(@PathVariable Long scenarioId) {
        return ResultVO.success(scenarioService.getSteps(scenarioId));
    }

    @PostMapping("/{scenarioId}/steps")
    @Operation(summary = "添加场景步骤")
    @RequirePermission("scenario:edit")
    public ResultVO<ScenarioStep> addStep(
            @PathVariable Long scenarioId,
            @RequestBody ScenarioStepDTO dto) {
        return ResultVO.success(scenarioService.addStep(scenarioId, dto));
    }

    @PutMapping("/steps/{stepId}")
    @Operation(summary = "更新场景步骤")
    @RequirePermission("scenario:edit")
    public ResultVO<ScenarioStep> updateStep(
            @PathVariable Long stepId,
            @RequestBody ScenarioStepDTO dto) {
        return ResultVO.success(scenarioService.updateStep(stepId, dto));
    }

    @DeleteMapping("/steps/{stepId}")
    @Operation(summary = "删除场景步骤")
    @RequirePermission("scenario:edit")
    public ResultVO<Void> deleteStep(@PathVariable Long stepId) {
        scenarioService.deleteStep(stepId);
        return ResultVO.success(null);
    }

    // ==================== 场景执行 ====================

    @PostMapping("/execute")
    @Operation(summary = "执行场景")
    @RequirePermission("scenario:execute")
    @AuditLog(operateType = "OTHER", module = "SCENARIO", description = "'执行场景: ' + #request.scenarioCode", targetType = "SCENARIO", targetId = "#request.scenarioCode", recordParams = true)
    public ResultVO<ScenarioExecuteResultDTO> execute(
            @RequestBody ScenarioExecuteRequestDTO request,
            @RequestAttribute(AppConstants.REQ_ATTR_USER_ID) Long userId,
            @RequestAttribute(AppConstants.REQ_ATTR_USER_CODE) String userCode) {
        log.info("[ScenarioController] 执行场景: code={}, user={}", request.getScenarioCode(), userCode);
        ScenarioExecuteResultDTO result = scenarioExecutionService.execute(request, userCode);
        return ResultVO.success(result);
    }

    // ==================== 执行记录 ====================

    @GetMapping("/executions")
    @Operation(summary = "分页查询执行记录")
    @RequirePermission("scenario:view")
    @AuditLog(operateType = "QUERY", module = "SCENARIO", description = "'分页查询场景执行记录'", recordResult = false)
    public ResultVO<Page<ScenarioExecution>> pageExecutions(
            @RequestParam(required = false) Long scenarioId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scenarioCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResultVO.success(scenarioExecutionRepository.pageQuery(
                scenarioId, status, scenarioCode,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"))));
    }

    @GetMapping("/executions/{executionId}")
    @Operation(summary = "查询执行记录详情")
    @RequirePermission("scenario:view")
    @AuditLog(operateType = "QUERY", module = "SCENARIO", description = "'查询场景执行记录详情ID: ' + #executionId", targetType = "SCENARIO", targetId = "#executionId", recordResult = false)
    public ResultVO<ScenarioExecution> getExecution(@PathVariable Long executionId) {
        return ResultVO.success(scenarioExecutionRepository.findById(executionId)
                .orElse(null));
    }

    @GetMapping("/executions/{executionId}/steps")
    @Operation(summary = "查询执行步骤日志")
    @RequirePermission("scenario:view")
    @AuditLog(operateType = "QUERY", module = "SCENARIO", description = "'查询场景执行步骤日志executionId: ' + #executionId", targetType = "SCENARIO", targetId = "#executionId", recordResult = false)
    public ResultVO<List<ScenarioStepLog>> getExecutionSteps(@PathVariable Long executionId) {
        return ResultVO.success(scenarioStepLogRepository.findByExecutionIdOrderByStepOrder(executionId));
    }

    // ==================== 私有方法 ====================

    @SuppressWarnings("unchecked")
    private ScenarioDTO parseScenarioDTO(Map<String, Object> body) {
        return ScenarioDTO.builder()
                .code((String) body.get("code"))
                .name((String) body.get("name"))
                .description((String) body.get("description"))
                .groupName((String) body.get("groupName"))
                .failureStrategy((String) body.getOrDefault("failureStrategy", "STOP"))
                .timeoutSeconds(body.get("timeoutSeconds") != null ?
                        ((Number) body.get("timeoutSeconds")).intValue() : 300)
                .status((String) body.getOrDefault("status", "ACTIVE"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<ScenarioStepDTO> parseStepDTOs(Map<String, Object> body) {
        Object stepsObj = body.get("steps");
        if (stepsObj == null) return null;

        List<Map<String, Object>> stepsRaw = (List<Map<String, Object>>) stepsObj;
        List<ScenarioStepDTO> steps = new java.util.ArrayList<>();

        for (Map<String, Object> s : stepsRaw) {
            // 处理 inputMapping：可能是 String 或 Map
            String inputMapping = null;
            Object inputObj = s.get("inputMapping");
            if (inputObj != null) {
                if (inputObj instanceof String) {
                    inputMapping = (String) inputObj;
                } else {
                    inputMapping = com.integration.config.util.JsonUtil.toJson(inputObj);
                }
            }

            // 处理 outputMapping：可能是 String 或 Map
            String outputMapping = null;
            Object outputObj = s.get("outputMapping");
            if (outputObj != null) {
                if (outputObj instanceof String) {
                    outputMapping = (String) outputObj;
                } else {
                    outputMapping = com.integration.config.util.JsonUtil.toJson(outputObj);
                }
            }

            steps.add(ScenarioStepDTO.builder()
                    .stepCode((String) s.get("stepCode"))
                    .stepName((String) s.get("stepName"))
                    .stepOrder(s.get("stepOrder") != null ? ((Number) s.get("stepOrder")).intValue() : null)
                    .apiCode((String) s.get("apiCode"))
                    .inputMapping(inputMapping)
                    .outputMapping(outputMapping)
                    .conditionExpr((String) s.get("conditionExpr"))
                    .skipOnError(s.get("skipOnError") != null ? ((Number) s.get("skipOnError")).intValue() : 0)
                    .retryCount(s.get("retryCount") != null ? ((Number) s.get("retryCount")).intValue() : 0)
                    .enableCache(s.get("enableCache") != null ? (Boolean) s.get("enableCache") : false)
                    .cacheSeconds(s.get("cacheSeconds") != null ? ((Number) s.get("cacheSeconds")).intValue() : null)
                    .cacheKeys((String) s.get("cacheKeys"))
                    .build());
        }

        return steps;
    }
}
