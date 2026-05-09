package com.integration.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.integration.config.converter.TestAssertionEngine;
import com.integration.config.dto.*;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.entity.config.MockConfig;
import com.integration.config.entity.config.TestCase;
import com.integration.config.entity.config.TestCaseStep;
import com.integration.config.entity.config.TestSuite;
import com.integration.config.entity.config.TestSuiteCase;
import com.integration.config.entity.log.TestExecution;
import com.integration.config.entity.log.TestStepResult;
import com.integration.config.enums.ErrorCode;
import com.integration.config.enums.Status;
import com.integration.config.enums.TestExecutionStatus;
import com.integration.config.exception.BusinessException;
import com.integration.config.repository.config.TestCaseRepository;
import com.integration.config.repository.config.TestCaseStepRepository;
import com.integration.config.repository.config.TestSuiteCaseRepository;
import com.integration.config.repository.config.TestSuiteRepository;
import com.integration.config.repository.log.TestExecutionRepository;
import com.integration.config.repository.log.TestStepResultRepository;
import com.integration.config.util.SnowflakeUtil;
import com.integration.config.util.TraceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 测试执行引擎 Service
 * 支持单用例执行、套件执行、批量执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestExecutionService {

    private final TestExecutionRepository testExecutionRepository;
    private final TestStepResultRepository testStepResultRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestCaseStepRepository testCaseStepRepository;
    private final TestSuiteRepository testSuiteRepository;
    private final TestSuiteCaseRepository testSuiteCaseRepository;
    private final TestAssertionEngine assertionEngine;
    private final HttpInvokeService httpInvokeService;
    private final ScenarioExecutionService scenarioExecutionService;
    private final MockConfigService mockConfigService;
    private final ApiConfigService apiConfigService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_TEXT_LENGTH = 20000; // 截断长度

    /**
     * 执行单个测试用例
     */
    @Transactional
    public TestExecution executeCase(TestRunRequestDTO request, String triggerUser) {
        // 1. 加载用例
        TestCase testCase = loadTestCase(request.getCaseId(), request.getCaseCode());
        
        // 2. 创建执行记录
        TestExecution execution = createExecution(testCase, null, triggerUser);
        
        // 3. 执行用例
        executeTestCase(testCase, execution, request.getParams());
        
        return execution;
    }

    /**
     * 执行测试套件
     */
    @Transactional
    public List<TestExecution> executeSuite(Long suiteId, String triggerUser) {
        TestSuite testSuite = testSuiteRepository.findById(suiteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试套件不存在: " + suiteId));
        
        if (testSuite.getStatus() != Status.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "测试套件未启用");
        }
        
        // 获取套件下的用例
        List<TestSuiteCase> suiteCases = testSuiteCaseRepository.findByTestSuiteIdOrderByCaseOrderAsc(suiteId);
        if (suiteCases.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "测试套件没有关联用例");
        }
        
        List<TestExecution> executions = new ArrayList<>();
        boolean stopOnFirstFailure = Boolean.TRUE.equals(testSuite.getStopOnFirstFailure());
        
        for (TestSuiteCase suiteCase : suiteCases) {
            Optional<TestCase> caseOpt = testCaseRepository.findById(suiteCase.getTestCaseId());
            if (caseOpt.isEmpty()) {
                log.warn("用例不存在: {}", suiteCase.getTestCaseId());
                continue;
            }
            
            TestCase testCase = caseOpt.get();
            if (testCase.getStatus() != Status.ACTIVE) {
                log.info("跳过未启用用例: {}", testCase.getCode());
                continue;
            }
            
            // 创建执行记录
            TestExecution execution = createExecution(testCase, testSuite, triggerUser);
            
            // 执行用例
            executeTestCase(testCase, execution, null);
            executions.add(execution);
            
            // 检查是否需要停止
            if (stopOnFirstFailure && execution.getStatus() == TestExecutionStatus.FAILED) {
                log.info("套件 {} 第一个用例失败，停止执行", testSuite.getCode());
                break;
            }
        }
        
        return executions;
    }

    /**
     * 批量执行
     */
    @Transactional
    public List<TestExecution> executeBatch(TestBatchRunDTO request, String triggerUser) {
        List<TestExecution> executions = new ArrayList<>();
        boolean stopOnFirstFailure = Boolean.TRUE.equals(request.getStopOnFirstFailure());
        
        // 1. 按用例ID执行
        if (request.getCaseIds() != null && !request.getCaseIds().isEmpty()) {
            for (Long caseId : request.getCaseIds()) {
                Optional<TestCase> caseOpt = testCaseRepository.findById(caseId);
                if (caseOpt.isEmpty()) {
                    log.warn("用例不存在: {}", caseId);
                    continue;
                }
                
                TestCase testCase = caseOpt.get();
                if (testCase.getStatus() != Status.ACTIVE) {
                    continue;
                }
                
                TestExecution execution = createExecution(testCase, null, triggerUser);
                executeTestCase(testCase, execution, null);
                executions.add(execution);
                
                if (stopOnFirstFailure && execution.getStatus() == TestExecutionStatus.FAILED) {
                    break;
                }
            }
        }
        
        // 2. 按套件ID执行
        if (request.getSuiteIds() != null && !request.getSuiteIds().isEmpty()) {
            for (Long suiteId : request.getSuiteIds()) {
                List<TestExecution> suiteExecutions = executeSuite(suiteId, triggerUser);
                executions.addAll(suiteExecutions);
                
                if (stopOnFirstFailure) {
                    boolean hasFailed = suiteExecutions.stream()
                            .anyMatch(e -> e.getStatus() == TestExecutionStatus.FAILED);
                    if (hasFailed) {
                        break;
                    }
                }
            }
        }
        
        // 3. 按标签执行
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            for (String tag : request.getTags()) {
                List<TestCase> cases = testCaseRepository.findByTagsContaining(tag);
                for (TestCase testCase : cases) {
                    if (testCase.getStatus() != Status.ACTIVE) {
                        continue;
                    }
                    
                    TestExecution execution = createExecution(testCase, null, triggerUser);
                    executeTestCase(testCase, execution, null);
                    executions.add(execution);
                    
                    if (stopOnFirstFailure && execution.getStatus() == TestExecutionStatus.FAILED) {
                        break;
                    }
                }
            }
        }
        
        return executions;
    }

    /**
     * 分页查询执行记录
     */
    public Page<TestExecution> pageQuery(Long testCaseId, Long testSuiteId, String status, 
                                          String triggerSource, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        TestExecutionStatus statusEnum = null;
        if (StringUtils.hasText(status)) {
            try {
                statusEnum = TestExecutionStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // 忽略无效状态
            }
        }
        
        String keywordParam = StringUtils.hasText(keyword) ? keyword : null;
        String triggerParam = StringUtils.hasText(triggerSource) ? triggerSource.toUpperCase() : null;
        
        return testExecutionRepository.pageQuery(testCaseId, testSuiteId, statusEnum, 
                triggerParam, keywordParam, pageable);
    }

    /**
     * 获取执行详情
     */
    public TestExecution getExecution(Long id) {
        return testExecutionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行记录不存在: " + id));
    }

    /**
     * 获取执行步骤结果
     */
    public List<TestStepResultDTO> getStepResults(Long executionId) {
        List<TestStepResult> stepResults = testStepResultRepository.findByTestExecutionIdOrderByStepOrderAsc(executionId);
        return stepResults.stream().map(this::toStepResultDTO).collect(Collectors.toList());
    }

    /**
     * 获取用例最近执行记录
     */
    public List<TestExecution> getRecentExecutionsByCase(Long testCaseId, int limit) {
        return testExecutionRepository.findByTestCaseIdOrderByCreatedAtDesc(testCaseId)
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 删除执行记录
     */
    @Transactional
    public void deleteExecution(Long id) {
        TestExecution execution = testExecutionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "执行记录不存在: " + id));
        
        // 删除步骤结果
        testStepResultRepository.deleteByTestExecutionId(id);
        
        // 删除执行记录
        testExecutionRepository.delete(execution);
    }

    // ============ 私有方法 ============

    private TestCase loadTestCase(Long caseId, String caseCode) {
        if (caseId != null) {
            return testCaseRepository.findById(caseId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + caseId));
        } else if (StringUtils.hasText(caseCode)) {
            return testCaseRepository.findByCode(caseCode)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + caseCode));
        } else {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "必须指定 caseId 或 caseCode");
        }
    }

    private TestExecution createExecution(TestCase testCase, TestSuite testSuite, String triggerUser) {
        TestExecution execution = TestExecution.builder()
                .id(SnowflakeUtil.nextId())
                .testCaseId(testCase.getId())
                .testCaseCode(testCase.getCode())
                .testSuiteId(testSuite != null ? testSuite.getId() : null)
                .testSuiteCode(testSuite != null ? testSuite.getCode() : null)
                .status(TestExecutionStatus.PENDING)
                .triggerSource("MANUAL")
                .triggerUser(triggerUser)
                .traceId(TraceUtil.generate())
                .build();
        
        return testExecutionRepository.save(execution);
    }

    private void executeTestCase(TestCase testCase, TestExecution execution, String params) {
        long caseStartTime = System.currentTimeMillis();
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus(TestExecutionStatus.RUNNING);
        testExecutionRepository.save(execution);
        
        try {
            // 加载步骤
            List<TestCaseStep> steps = testCaseStepRepository.findByTestCaseIdOrderByStepOrderAsc(testCase.getId());
            if (steps.isEmpty()) {
                execution.setStatus(TestExecutionStatus.FAILED);
                execution.setErrorMessage("用例没有配置步骤");
                finishExecution(execution, caseStartTime, 0, 0, 0);
                return;
            }
            
            execution.setTotalSteps(steps.size());
            
            int passedCount = 0;
            int failedCount = 0;
            int skippedCount = 0;
            
            // 解析自定义参数
            Map<String, Object> customParams = parseParams(params);
            
            for (TestCaseStep step : steps) {
                if (!Boolean.TRUE.equals(step.getEnabled())) {
                    skippedCount++;
                    continue;
                }
                
                // 检查前序失败是否跳过
                if (step.getSkipOnError() != null && step.getSkipOnError() == 1 && failedCount > 0) {
                    skippedCount++;
                    saveStepResult(execution.getId(), step, null, null, TestExecutionStatus.SKIPPED, 
                            null, "前序步骤失败，已跳过");
                    continue;
                }
                
                // 执行步骤
                StepExecuteResult result = executeStep(step, customParams, testCase.getTimeoutMs());
                
                // 保存步骤结果
                saveStepResult(execution.getId(), step, result.getRequestParams(), result.getResponseData(),
                        result.getStatus(), result.getAssertionResults(), result.getErrorMessage());
                
                // 统计
                if (result.getStatus() == TestExecutionStatus.SUCCESS) {
                    passedCount++;
                } else if (result.getStatus() == TestExecutionStatus.FAILED) {
                    failedCount++;
                } else if (result.getStatus() == TestExecutionStatus.SKIPPED) {
                    skippedCount++;
                }
                
                // 检查失败策略
                if (result.getStatus() == TestExecutionStatus.FAILED && "STOP".equals(testCase.getFailureStrategy())) {
                    execution.setErrorMessage("步骤 " + step.getStepCode() + " 执行失败: " + result.getErrorMessage());
                    break;
                }
            }
            
            // 确定最终状态
            if (failedCount > 0) {
                execution.setStatus(TestExecutionStatus.FAILED);
                if (execution.getErrorMessage() == null) {
                    execution.setErrorMessage("共 " + failedCount + " 个步骤失败");
                }
            } else if (passedCount == 0 && skippedCount > 0) {
                execution.setStatus(TestExecutionStatus.SKIPPED);
            } else {
                execution.setStatus(TestExecutionStatus.SUCCESS);
            }
            
            finishExecution(execution, caseStartTime, passedCount, failedCount, skippedCount);
            
        } catch (Exception e) {
            log.error("执行用例 {} 异常", testCase.getCode(), e);
            execution.setStatus(TestExecutionStatus.FAILED);
            execution.setErrorMessage("执行异常: " + e.getMessage());
            finishExecution(execution, caseStartTime, 0, 0, 0);
        }
    }

    private StepExecuteResult executeStep(TestCaseStep step, Map<String, Object> customParams, Integer caseTimeout) {
        long stepStartTime = System.currentTimeMillis();
        StepExecuteResult result = new StepExecuteResult();
        
        try {
            String stepType = step.getStepType().toUpperCase();
            String responseData = null;
            
            switch (stepType) {
                case "API":
                    responseData = invokeApiStep(step, customParams, caseTimeout);
                    break;
                case "SCENARIO":
                    responseData = invokeScenarioStep(step, customParams);
                    break;
                case "MOCK":
                    responseData = invokeMockStep(step, customParams);
                    break;
                default:
                    throw new BusinessException(ErrorCode.INVALID_PARAM, "不支持的步骤类型: " + stepType);
            }
            
            result.setResponseData(responseData);
            
            // 执行断言
            List<AssertionDTO> assertions = assertionEngine.parseAssertions(step.getAssertions());
            List<AssertionResultDTO> assertionResults = assertionEngine.execute(
                    responseData, assertions);
            result.setAssertionResults(assertionResults);
            
            // 判断断言结果
            boolean allPassed = assertionResults.stream()
                    .allMatch(a -> "PASS".equals(a.getStatus()));
            
            if (allPassed) {
                result.setStatus(TestExecutionStatus.SUCCESS);
            } else {
                result.setStatus(TestExecutionStatus.FAILED);
                result.setErrorMessage("断言失败");
            }
            
        } catch (Exception e) {
            log.error("执行步骤 {} 异常", step.getStepCode(), e);
            result.setStatus(TestExecutionStatus.FAILED);
            result.setErrorMessage(e.getMessage());
        }
        
        result.setCostTimeMs(System.currentTimeMillis() - stepStartTime);
        return result;
    }

    private String invokeApiStep(TestCaseStep step, Map<String, Object> customParams, Integer caseTimeout) throws Exception {
        // 获取 API 配置
        var apiConfig = apiConfigService.getByCode(step.getTargetCode());
        if (apiConfig == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API配置不存在: " + step.getTargetCode());
        }

        // 构建请求参数（合并覆盖参数）
        Map<String, Object> requestParams = buildRequestParams(step.getRequestOverride(), customParams);

        // 转换为 InvokeRequestDTO
        InvokeRequestDTO invokeRequest = InvokeRequestDTO.builder()
                .apiCode(step.getTargetCode())
                .params(requestParams)
                .debug(false)
                .build();

        // 真正调用 HTTP 服务
        InvokeResponseDTO response = httpInvokeService.invoke(invokeRequest);

        // 解析响应数据为 JSON 字符串，用于后续断言
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            throw new RuntimeException("API调用失败: " + response.getMessage());
        }

        Object responseData = response.getData();
        if (responseData == null) {
            return "{}";
        }
        return objectMapper.writeValueAsString(responseData);
    }

    private String invokeScenarioStep(TestCaseStep step, Map<String, Object> customParams) throws Exception {
        // 构建场景执行请求
        ScenarioExecuteRequestDTO request = new ScenarioExecuteRequestDTO();
        request.setScenarioCode(step.getTargetCode());
        
        // 转换自定义参数为输入参数
        if (customParams != null && !customParams.isEmpty()) {
            Map<String, Object> params = new HashMap<>();
            params.putAll(customParams);
            request.setParams(params);
        }
        
        // 执行场景
        var result = scenarioExecutionService.execute(request, "TEST_EXECUTOR");
        
        // 从 context 中提取场景输出（最后一个步骤的 output 或整个 context）
        if (result.getContext() != null && result.getContext().containsKey("output")) {
            Object output = result.getContext().get("output");
            return output != null ? output.toString() : "{}";
        }
        return result.getSuccess() ? "{\"success\": true}" : "{\"success\": false, \"error\": \"" + result.getErrorMessage() + "\"}";
    }

    private String invokeMockStep(TestCaseStep step, Map<String, Object> customParams) throws Exception {
        // 根据 targetCode 查找 Mock 配置
        var mockConfigOpt = mockConfigService.findByCode(step.getTargetCode());
        if (mockConfigOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Mock配置不存在: " + step.getTargetCode());
        }
        MockConfig mockConfig = mockConfigOpt.get();

        // 解析覆盖参数作为请求体
        String requestBody = null;
        if (customParams != null && !customParams.isEmpty()) {
            requestBody = objectMapper.writeValueAsString(customParams);
        } else if (StringUtils.hasText(step.getRequestOverride())) {
            requestBody = step.getRequestOverride();
        }

        // 构建 Mock 上下文（从请求体中提取 path/query/header 供匹配规则使用）
        MockTemplateEngine.MockContext context = MockTemplateEngine.MockContext.builder()
                .body(requestBody)
                .build();

        // 执行 Mock：匹配规则 → 渲染模板 → 返回响应
        return mockConfigService.executeMock(mockConfig, context);
    }

    private Map<String, Object> buildRequestParams(String requestOverride, Map<String, Object> customParams) {
        Map<String, Object> params = new HashMap<>();
        
        // 解析覆盖参数
        if (StringUtils.hasText(requestOverride)) {
            try {
                JsonNode overrideNode = objectMapper.readTree(requestOverride);
                overrideNode.fields().forEachRemaining(entry -> {
                    params.put(entry.getKey(), entry.getValue());
                });
            } catch (Exception e) {
                log.warn("解析 requestOverride 失败: {}", requestOverride);
            }
        }
        
        // 合并自定义参数
        if (customParams != null) {
            params.putAll(customParams);
        }
        
        return params;
    }

    private Map<String, Object> parseParams(String params) {
        if (!StringUtils.hasText(params)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(params, Map.class);
        } catch (Exception e) {
            log.warn("解析自定义参数失败: {}", params);
            return new HashMap<>();
        }
    }

    private void saveStepResult(Long executionId, TestCaseStep step, String requestParams, String responseData,
                                 TestExecutionStatus status, List<AssertionResultDTO> assertionResults, String errorMessage) {
        try {
            String assertionJson = assertionResults != null ? 
                    objectMapper.writeValueAsString(assertionResults) : null;
            
            TestStepResult stepResult = TestStepResult.builder()
                    .id(SnowflakeUtil.nextId())
                    .testExecutionId(executionId)
                    .stepOrder(step.getStepOrder())
                    .stepCode(step.getStepCode())
                    .stepType(step.getStepType())
                    .targetCode(step.getTargetCode())
                    .status(status)
                    .requestParams(truncateText(requestParams))
                    .responseData(truncateText(responseData))
                    .assertionResults(assertionJson)
                    .errorMessage(errorMessage)
                    .build();
            
            testStepResultRepository.save(stepResult);
        } catch (Exception e) {
            log.error("保存步骤结果失败", e);
        }
    }

    private void finishExecution(TestExecution execution, long startTime, int passed, int failed, int skipped) {
        execution.setEndTime(LocalDateTime.now());
        execution.setCostTimeMs(System.currentTimeMillis() - startTime);
        execution.setPassedSteps(passed);
        execution.setFailedSteps(failed);
        execution.setSkippedSteps(skipped);
        testExecutionRepository.save(execution);
    }

    private String truncateText(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            return text.substring(0, MAX_TEXT_LENGTH) + "... [truncated]";
        }
        return text;
    }

    private TestStepResultDTO toStepResultDTO(TestStepResult stepResult) {
        TestStepResultDTO dto = new TestStepResultDTO();
        dto.setStepOrder(stepResult.getStepOrder());
        dto.setStepCode(stepResult.getStepCode());
        dto.setStepType(stepResult.getStepType());
        dto.setTargetCode(stepResult.getTargetCode());
        dto.setStatus(stepResult.getStatus() != null ? stepResult.getStatus().name() : null);
        dto.setCostTimeMs(stepResult.getCostTimeMs());
        dto.setRequestParams(stepResult.getRequestParams());
        dto.setResponseData(stepResult.getResponseData());
        dto.setErrorMessage(stepResult.getErrorMessage());
        
        // 解析断言结果
        if (StringUtils.hasText(stepResult.getAssertionResults())) {
            try {
                List<AssertionResultDTO> assertions = objectMapper.readValue(
                        stepResult.getAssertionResults(), 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, AssertionResultDTO.class));
                dto.setAssertionResults(assertions);
            } catch (Exception e) {
                log.warn("解析断言结果失败", e);
            }
        }
        
        return dto;
    }

    /**
     * 步骤执行结果内部类
     */
    private static class StepExecuteResult {
        private TestExecutionStatus status;
        private String requestParams;
        private String responseData;
        private List<AssertionResultDTO> assertionResults;
        private String errorMessage;
        private long costTimeMs;

        public TestExecutionStatus getStatus() { return status; }
        public void setStatus(TestExecutionStatus status) { this.status = status; }
        public String getRequestParams() { return requestParams; }
        public void setRequestParams(String requestParams) { this.requestParams = requestParams; }
        public String getResponseData() { return responseData; }
        public void setResponseData(String responseData) { this.responseData = responseData; }
        public List<AssertionResultDTO> getAssertionResults() { return assertionResults; }
        public void setAssertionResults(List<AssertionResultDTO> assertionResults) { this.assertionResults = assertionResults; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public long getCostTimeMs() { return costTimeMs; }
        public void setCostTimeMs(long costTimeMs) { this.costTimeMs = costTimeMs; }
    }
}
