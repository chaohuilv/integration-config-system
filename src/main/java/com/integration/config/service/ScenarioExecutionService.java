package com.integration.config.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.integration.config.dto.*;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.entity.config.Scenario;
import com.integration.config.entity.config.ScenarioStep;
import com.integration.config.entity.log.ScenarioExecution;
import com.integration.config.entity.log.ScenarioStepLog;
import com.integration.config.entity.token.ScenarioCache;
import com.integration.config.enums.ErrorCode;
import com.integration.config.exception.BusinessException;
import com.integration.config.repository.config.ScenarioRepository;
import com.integration.config.repository.config.ScenarioStepRepository;
import com.integration.config.repository.log.ScenarioExecutionRepository;
import com.integration.config.repository.log.ScenarioStepLogRepository;
import com.integration.config.util.JsonPathUtil;
import com.integration.config.util.JsonUtil;
import com.integration.config.util.TraceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 场景执行引擎
 * 核心服务：负责执行场景编排
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScenarioExecutionService {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioStepRepository scenarioStepRepository;
    private final ScenarioExecutionRepository scenarioExecutionRepository;
    private final ScenarioStepLogRepository scenarioStepLogRepository;
    private final HttpInvokeService httpInvokeService;
    private final ApiConfigService apiConfigService;
    private final ScenarioCacheService scenarioCacheService;
    private final TokenCacheManager tokenCacheManager;

    /**
     * 执行场景
     */
    public ScenarioExecuteResultDTO execute(ScenarioExecuteRequestDTO request, String triggerUser) {
        String traceId = TraceUtil.generate();
        long startTime = System.currentTimeMillis();

        // 1. 加载场景配置
        Scenario scenario;
        if (request.getScenarioCode() != null) {
            scenario = scenarioRepository.findByCode(request.getScenarioCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "场景不存在: " + request.getScenarioCode()));
        } else if (request.getScenarioId() != null) {
            scenario = scenarioRepository.findById(request.getScenarioId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "场景不存在"));
        } else {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "必须指定 scenarioCode 或 scenarioId");
        }

        // 检查场景状态
        if (!"ACTIVE".equals(scenario.getStatus().name())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "场景未启用");
        }

        // 2. 加载步骤列表
        List<ScenarioStep> steps = scenarioStepRepository.findByScenarioIdOrderByStepOrder(scenario.getId());
        if (steps.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "场景没有配置步骤");
        }

        // 3. 创建执行记录
        ScenarioExecution execution = ScenarioExecution.builder()
                .scenarioId(scenario.getId())
                .scenarioCode(scenario.getCode())
                .scenarioName(scenario.getName())
                .status("RUNNING")
                .startTime(LocalDateTime.now())
                .triggerSource(request.getTriggerSource() != null ? request.getTriggerSource() : "MANUAL")
                .triggerUser(triggerUser)
                .traceId(traceId)
                .build();
        execution = scenarioExecutionRepository.save(execution);

        // 4. 初始化执行上下文
        ExecutionContext context = new ExecutionContext();
        context.setInput(request.getParams() != null ? request.getParams() : new HashMap<>());

        // 5. 执行步骤
        List<ScenarioExecuteResultDTO.StepResultDTO> stepResults = new ArrayList<>();
        boolean hasFailure = false;
        String failureStrategy = scenario.getFailureStrategy() != null ? scenario.getFailureStrategy() : "STOP";

        try {
            for (ScenarioStep step : steps) {
                ScenarioExecuteResultDTO.StepResultDTO stepResult = executeStep(step, context, execution.getId(), traceId, scenario.getCode(), scenario.getId());
                stepResults.add(stepResult);

                if ("FAILED".equals(stepResult.getStatus())) {
                    hasFailure = true;
                    if ("STOP".equals(failureStrategy)) {
                        log.warn("[{}] 步骤 {} 失败，终止执行（失败策略: STOP）", traceId, step.getStepCode());
                        break;
                    } else {
                        log.warn("[{}] 步骤 {} 失败，继续执行（失败策略: CONTINUE）", traceId, step.getStepCode());
                    }
                }
            }

            // 6. 更新执行记录
            execution.setEndTime(LocalDateTime.now());
            execution.setCostTimeMs(System.currentTimeMillis() - startTime);
            execution.setContext(JsonUtil.toJson(context.toMap()));

            if (hasFailure) {
                // 检查是否有成功的步骤
                boolean hasSuccess = stepResults.stream().anyMatch(r -> "SUCCESS".equals(r.getStatus()));
                execution.setStatus(hasSuccess ? "PARTIAL" : "FAILED");
            } else {
                execution.setStatus("SUCCESS");
            }

            scenarioExecutionRepository.save(execution);

        } catch (Exception e) {
            log.error("[{}] 场景执行异常: {}", traceId, e.getMessage(), e);
            execution.setStatus("FAILED");
            execution.setEndTime(LocalDateTime.now());
            execution.setCostTimeMs(System.currentTimeMillis() - startTime);
            execution.setErrorMessage(e.getMessage());
            execution.setContext(JsonUtil.toJson(context.toMap()));
            scenarioExecutionRepository.save(execution);

            return ScenarioExecuteResultDTO.builder()
                    .success(false)
                    .executionId(execution.getId())
                    .scenarioCode(scenario.getCode())
                    .scenarioName(scenario.getName())
                    .status("FAILED")
                    .startTime(execution.getStartTime())
                    .endTime(execution.getEndTime())
                    .costTimeMs(execution.getCostTimeMs())
                    .errorMessage(e.getMessage())
                    .steps(stepResults)
                    .build();
        }

        TraceUtil.clear();

        return ScenarioExecuteResultDTO.builder()
                .success(!hasFailure)
                .executionId(execution.getId())
                .scenarioCode(scenario.getCode())
                .scenarioName(scenario.getName())
                .status(execution.getStatus())
                .startTime(execution.getStartTime())
                .endTime(execution.getEndTime())
                .costTimeMs(execution.getCostTimeMs())
                .context(context.toMap())
                .steps(stepResults)
                .build();
    }

    /**
     * 执行单个步骤
     */
    private ScenarioExecuteResultDTO.StepResultDTO executeStep(
            ScenarioStep step,
            ExecutionContext context,
            Long executionId,
            String traceId,
            String scenarioCode,
            Long scenarioId) {

        long startTime = System.currentTimeMillis();

        // 1. 检查前序步骤是否失败（skipOnError 标记）
        if (step.getSkipOnError() != null && step.getSkipOnError() == 1) {
            Boolean prevFailed = (Boolean) context.getMetadata().get("_hasFailure");
            if (prevFailed != null && prevFailed) {
                log.info("[{}] 前序步骤失败，跳过步骤 {}", traceId, step.getStepCode());
                return ScenarioExecuteResultDTO.StepResultDTO.builder()
                        .stepCode(step.getStepCode())
                        .stepName(step.getStepName())
                        .stepOrder(step.getStepOrder())
                        .status("SKIPPED")
                        .costTimeMs(0L)
                        .build();
            }
        }

        // 2. 构建输入参数
        InvokeRequestDTO invokeRequest = buildInvokeRequest(step, context, scenarioCode);

        // 2.1 缓存读取：若开启缓存且缓存命中，跳过API调用，直接从缓存构建输出
        if (Boolean.TRUE.equals(step.getEnableCache()) && step.getCacheSeconds() != null && step.getCacheSeconds() > 0) {
            Map<String, Object> cachedOutput = readStepCache(scenarioCode, step);
            if (cachedOutput != null && !cachedOutput.isEmpty()) {
                log.info("[{}] 步骤 {} 缓存命中，跳过API调用", traceId, step.getStepCode());
                context.getSteps().put(step.getStepCode(), cachedOutput);

                // 记录步骤日志（缓存命中）
                ScenarioStepLog cacheLog = ScenarioStepLog.builder()
                        .executionId(executionId)
                        .stepId(step.getId())
                        .stepCode(step.getStepCode())
                        .stepName(step.getStepName())
                        .stepOrder(step.getStepOrder())
                        .status("SUCCESS")
                        .startTime(LocalDateTime.now())
                        .endTime(LocalDateTime.now())
                        .costTimeMs(System.currentTimeMillis() - startTime)
                        .requestParams("(cache hit)")
                        .responseData(truncate(JsonUtil.toJson(cachedOutput), 5000))
                        .build();
                scenarioStepLogRepository.save(cacheLog);

                return buildCacheHitResult(step, cachedOutput, startTime);
            }
        }

        // 3. 记录步骤日志（开始）
        ScenarioStepLog stepLog = ScenarioStepLog.builder()
                .executionId(executionId)
                .stepId(step.getId())
                .stepCode(step.getStepCode())
                .stepName(step.getStepName())
                .stepOrder(step.getStepOrder())
                .status("RUNNING")
                .startTime(LocalDateTime.now())
                .requestParams(JsonUtil.toJson(invokeRequest.getParams()))
                .build();

        try {
            // 4. 调用接口
            InvokeResponseDTO response = httpInvokeService.invoke(invokeRequest);

            // 5. 提取输出并存储到上下文
            Map<String, Object> output = extractOutput(step, response);
            context.getSteps().put(step.getStepCode(), output);

            // 5.1 写入场景缓存（只有 enableCache=true 且配置了缓存时长时才写入）
            if (Boolean.TRUE.equals(step.getEnableCache())
                    && step.getCacheSeconds() != null && step.getCacheSeconds() > 0
                    && !output.isEmpty()) {
                cacheStepOutput(scenarioCode, scenarioId, step, output);
            }

            // 6. 更新步骤日志
            boolean success = response.getSuccess();
            stepLog.setStatus(success ? "SUCCESS" : "FAILED");
            stepLog.setEndTime(LocalDateTime.now());
            stepLog.setCostTimeMs(System.currentTimeMillis() - startTime);
            stepLog.setResponseData(truncate(JsonUtil.toJson(response.getData()), 5000));
            if (!success) {
                stepLog.setErrorMessage(response.getMessage());
                // 标记失败
                context.getMetadata().put("_hasFailure", true);
            }
            scenarioStepLogRepository.save(stepLog);

            // 7. 检查条件表达式（基于当前步骤的返回结果判断是否继续后续步骤）
            if (step.getConditionExpr() != null && !step.getConditionExpr().isEmpty()) {
                boolean conditionMet = evaluateCondition(step.getConditionExpr(), context);
                if (!conditionMet) {
                    log.warn("[{}] 步骤 {} 条件表达式不满足: {}，标记为 FAILED", traceId, step.getStepCode(), step.getConditionExpr());
                    // 条件不满足，更新日志状态
                    stepLog.setStatus("FAILED");
                    stepLog.setErrorMessage("条件表达式不满足: " + step.getConditionExpr());
                    scenarioStepLogRepository.save(stepLog);
                    // 标记失败
                    context.getMetadata().put("_hasFailure", true);
                    
                    return ScenarioExecuteResultDTO.StepResultDTO.builder()
                            .stepCode(step.getStepCode())
                            .stepName(step.getStepName())
                            .stepOrder(step.getStepOrder())
                            .status("FAILED")
                            .output(output)
                            .errorMessage("条件表达式不满足: " + step.getConditionExpr())
                            .costTimeMs(stepLog.getCostTimeMs())
                            .build();
                }
            }

            return ScenarioExecuteResultDTO.StepResultDTO.builder()
                    .stepCode(step.getStepCode())
                    .stepName(step.getStepName())
                    .stepOrder(step.getStepOrder())
                    .status(success ? "SUCCESS" : "FAILED")
                    .output(output)
                    .errorMessage(success ? null : response.getMessage())
                    .costTimeMs(stepLog.getCostTimeMs())
                    .build();

        } catch (Exception e) {
            log.error("[{}] 步骤 {} 执行异常: {}", traceId, step.getStepCode(), e.getMessage(), e);

            stepLog.setStatus("FAILED");
            stepLog.setEndTime(LocalDateTime.now());
            stepLog.setCostTimeMs(System.currentTimeMillis() - startTime);
            stepLog.setErrorMessage(e.getMessage());
            scenarioStepLogRepository.save(stepLog);

            // 存储空输出到上下文，标记失败
            context.getSteps().put(step.getStepCode(), new HashMap<>());
            context.getMetadata().put("_hasFailure", true);

            return ScenarioExecuteResultDTO.StepResultDTO.builder()
                    .stepCode(step.getStepCode())
                    .stepName(step.getStepName())
                    .stepOrder(step.getStepOrder())
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .costTimeMs(stepLog.getCostTimeMs())
                    .build();
        }
    }

    /**
     * 构建接口调用请求
     */
    private InvokeRequestDTO buildInvokeRequest(ScenarioStep step, ExecutionContext context, String scenarioCode) {
        InvokeRequestDTO.InvokeRequestDTOBuilder builder = InvokeRequestDTO.builder()
                .apiCode(step.getApiCode())
                .source("SCENARIO:" + step.getScenarioId());

        // 解析输入映射
        if (step.getInputMapping() != null && !step.getInputMapping().isEmpty()) {
            try {
                JSONObject mapping = JSONUtil.parseObj(step.getInputMapping());

                // 处理 params
                if (mapping.containsKey("params")) {
                    Map<String, Object> params = resolveParams(mapping.getJSONObject("params"), context, scenarioCode, step);
                    builder.params(params);
                }

                // 处理 headers
                if (mapping.containsKey("headers")) {
                    Map<String, String> headers = resolveHeaders(mapping.getJSONObject("headers"), context, scenarioCode, step);
                    builder.headers(headers);
                }
            } catch (Exception e) {
                log.warn("解析输入映射失败: {}", e.getMessage());
            }
        }

        return builder.build();
    }

    /**
     * 解析参数映射
     */
    private Map<String, Object> resolveParams(JSONObject paramsMapping, ExecutionContext context, String scenarioCode, ScenarioStep step) {
        Map<String, Object> params = new HashMap<>();

        if (paramsMapping == null) return params;

        for (String key : paramsMapping.keySet()) {
            JSONObject mapping = paramsMapping.getJSONObject(key);
            if (mapping == null) continue;

            String type = mapping.getStr("type");
            Object value = null;

            switch (type) {
                case "static":
                    value = mapping.get("value");
                    break;
                case "input":
                    String inputKey = mapping.getStr("value");
                    value = context.getInput().get(inputKey);
                    break;
                case "step":
                    String stepCode = mapping.getStr("step");
                    String path = mapping.getStr("path");
                    Map<String, Object> stepOutput = context.getSteps().get(stepCode);
                    if (stepOutput != null && path != null) {
                        value = JsonPathUtil.extract(JsonUtil.toJson(stepOutput), path);
                    }
                    break;
                case "token":
                    // 从Token缓存表读取（接口级别的token持久化，自动过期刷新）
                    String tokenApiCode = mapping.getStr("apiCode");
                    if (tokenApiCode == null || tokenApiCode.isEmpty()) {
                        tokenApiCode = step.getApiCode(); // 默认缓存当前接口的token
                    }
                    value = tokenCacheManager.getCachedToken(tokenApiCode);
                    break;
                case "cache":
                    // 从场景缓存读取（跨执行持久化，按step+key）
                    String cacheStepCode = mapping.getStr("step");
                    String cacheOutputKey = mapping.getStr("key");
                    if (cacheStepCode != null && cacheOutputKey != null) {
                        value = scenarioCacheService.get(scenarioCode, cacheStepCode, cacheOutputKey);
                    }
                    break;
            }

            if (value != null) {
                params.put(key, value);
            }
        }

        return params;
    }

    /**
     * 解析请求头映射
     */
    private Map<String, String> resolveHeaders(JSONObject headersMapping, ExecutionContext context, String scenarioCode, ScenarioStep step) {
        Map<String, String> headers = new HashMap<>();

        if (headersMapping == null) return headers;

        for (String key : headersMapping.keySet()) {
            JSONObject mapping = headersMapping.getJSONObject(key);
            if (mapping == null) continue;

            String type = mapping.getStr("type");
            String value = null;

            switch (type) {
                case "static":
                    value = mapping.getStr("value");
                    break;
                case "input":
                    String inputKey = mapping.getStr("value");
                    Object inputVal = context.getInput().get(inputKey);
                    value = inputVal != null ? String.valueOf(inputVal) : null;
                    break;
                case "step":
                    String stepCode = mapping.getStr("step");
                    String path = mapping.getStr("path");
                    Map<String, Object> stepOutput = context.getSteps().get(stepCode);
                    if (stepOutput != null && path != null) {
                        Object extracted = JsonPathUtil.extract(JsonUtil.toJson(stepOutput), path);
                        value = extracted != null ? String.valueOf(extracted) : null;
                    }
                    break;
                case "token":
                    // 从Token缓存表读取（接口级别的token持久化，自动过期刷新）
                    String tokenApiCode2 = mapping.getStr("apiCode");
                    if (tokenApiCode2 == null || tokenApiCode2.isEmpty()) {
                        tokenApiCode2 = step.getApiCode();
                    }
                    Object cachedToken = tokenCacheManager.getCachedToken(tokenApiCode2);
                    value = cachedToken != null ? String.valueOf(cachedToken) : null;
                    break;
                case "cache":
                    // 从场景缓存读取（跨执行持久化）
                    String cacheStepCode = mapping.getStr("step");
                    String cacheOutputKey = mapping.getStr("key");
                    if (cacheStepCode != null && cacheOutputKey != null) {
                        Object cached = scenarioCacheService.get(scenarioCode, cacheStepCode, cacheOutputKey);
                        value = cached != null ? String.valueOf(cached) : null;
                    }
                    break;
            }

            if (value != null) {
                // 添加前缀
                String prefix = mapping.getStr("prefix");
                if (prefix != null) {
                    value = prefix + value;
                }
                headers.put(key, value);
            }
        }

        return headers;
    }

    /**
     * 提取输出
     */
    private Map<String, Object> extractOutput(ScenarioStep step, InvokeResponseDTO response) {
        Map<String, Object> output = new HashMap<>();

        if (step.getOutputMapping() == null || step.getOutputMapping().isEmpty()) {
            return output;
        }

        try {
            JSONObject mapping = JSONUtil.parseObj(step.getOutputMapping());
            JSONObject outputs = mapping.getJSONObject("outputs");

            if (outputs == null) return output;

            String responseJson = JsonUtil.toJson(response.getData());

            for (String key : outputs.keySet()) {
                String path = outputs.getStr(key);
                if (path != null) {
                    Object value = JsonPathUtil.extract(responseJson, path);
                    if (value != null) {
                        output.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("提取输出失败: {}", e.getMessage());
        }

        return output;
    }

    /**
     * 评估条件表达式
     * 支持简单的条件判断：{{step:xxx.yyy}} == 'value'
     */
    private boolean evaluateCondition(String expr, ExecutionContext context) {
        try {
            // 替换变量引用，支持 JsonPath 深度访问
            Pattern pattern = Pattern.compile("\\{\\{(step|input):([^}]+)\\}\\}");
            Matcher matcher = pattern.matcher(expr);

            while (matcher.find()) {
                String type = matcher.group(1);
                String path = matcher.group(2);
                Object value = null;

                if ("step".equals(type)) {
                    // 解析 step:xxx.yyy.zzz 格式
                    String[] parts = path.split("\\.", 2);
                    String stepCode = parts[0];
                    String jsonPath = parts.length > 1 ? parts[1] : null;
                    Map<String, Object> stepOutput = context.getSteps().get(stepCode);
                    if (stepOutput != null && jsonPath != null) {
                        // 使用 JsonPath 从输出中提取值
                        String outputJson = JsonUtil.toJson(stepOutput);
                        value = JsonPathUtil.extract(outputJson, "$." + jsonPath);
                    }
                } else if ("input".equals(type)) {
                    value = context.getInput().get(path);
                }

                String replacement = value != null ? String.valueOf(value) : "null";
                expr = expr.replace("{{" + type + ":" + path + "}}", replacement);
            }

            // 简单条件评估（仅支持 == 和 !=）
            if (expr.contains("==")) {
                String[] parts = expr.split("==", 2);
                String left = parts[0].trim();
                String right = parts[1].trim().replaceAll("['\"]", "");
                return left.equals(right) || left.equals("true") && right.equals("true");
            } else if (expr.contains("!=")) {
                String[] parts = expr.split("!=", 2);
                String left = parts[0].trim();
                String right = parts[1].trim().replaceAll("['\"]", "");
                return !left.equals(right);
            }

            return Boolean.parseBoolean(expr);
        } catch (Exception e) {
            log.warn("条件表达式评估失败: {} - {}", expr, e.getMessage());
            return false;
        }
    }

    /**
     * 从缓存读取步骤输出
     * 逐个 key 通过 scenarioCacheService.get() 读取，确保：
     * 1. 只读取未过期的缓存
     * 2. 如果指定了 cacheKeys 则只读指定字段，否则读全部 outputMapping 的 key
     * 3. 反序列化后的数据结构与 extractOutput() 输出一致
     */
    private Map<String, Object> readStepCache(String scenarioCode, ScenarioStep step) {
        Map<String, Object> output = new HashMap<>();
        try {
            // 确定要读取哪些 key
            List<String> keysToRead = resolveCacheKeys(step);
            if (keysToRead.isEmpty()) {
                log.debug("[cache] 步骤 {} 无缓存字段定义，跳过读取", step.getStepCode());
                return output;
            }

            for (String key : keysToRead) {
                Object value = scenarioCacheService.get(scenarioCode, step.getStepCode(), key);
                if (value != null) {
                    output.put(key, value);
                }
            }

            if (output.isEmpty()) {
                log.debug("[cache] 步骤 {} 缓存全部未命中", step.getStepCode());
            } else {
                log.info("[cache] 步骤 {} 缓存命中 {} 个字段", step.getStepCode(), output.size());
            }
        } catch (Exception e) {
            log.warn("[cache] 读取场景缓存失败: {}", e.getMessage());
        }
        return output;
    }

    /**
     * 解析步骤需要缓存的字段列表
     * 1. 如果指定了 cacheKeys（逗号分隔），按指定字段
     * 2. 否则从 outputMapping 的 outputs 中提取所有 key
     */
    private List<String> resolveCacheKeys(ScenarioStep step) {
        // 优先使用显式指定的 cacheKeys
        if (step.getCacheKeys() != null && !step.getCacheKeys().trim().isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (String k : step.getCacheKeys().split(",")) {
                String trimmed = k.trim();
                if (!trimmed.isEmpty()) {
                    keys.add(trimmed);
                }
            }
            return keys;
        }

        // 未指定 cacheKeys，从 outputMapping 的 outputs 提取所有 key
        if (step.getOutputMapping() != null && !step.getOutputMapping().isEmpty()) {
            try {
                JSONObject mapping = JSONUtil.parseObj(step.getOutputMapping());
                JSONObject outputs = mapping.getJSONObject("outputs");
                if (outputs != null && !outputs.isEmpty()) {
                    return new ArrayList<>(outputs.keySet());
                }
            } catch (Exception e) {
                log.warn("[cache] 解析 outputMapping 失败: {}", e.getMessage());
            }
        }

        return Collections.emptyList();
    }

    /**
     * 构建缓存命中时的步骤结果
     */
    private ScenarioExecuteResultDTO.StepResultDTO buildCacheHitResult(ScenarioStep step, Map<String, Object> output, long startTime) {
        return ScenarioExecuteResultDTO.StepResultDTO.builder()
                .stepCode(step.getStepCode())
                .stepName(step.getStepName())
                .stepOrder(step.getStepOrder())
                .status("SUCCESS")
                .output(output)
                .errorMessage(null)
                .costTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }

    /**
     * 缓存步骤输出到场景缓存表
     */
    private void cacheStepOutput(String scenarioCode, Long scenarioId, ScenarioStep step, Map<String, Object> output) {
        try {
            if (step.getCacheKeys() != null && !step.getCacheKeys().isEmpty()) {
                // 只缓存指定字段
                for (String key : step.getCacheKeys().split(",")) {
                    key = key.trim();
                    if (output.containsKey(key)) {
                        scenarioCacheService.put(scenarioId, scenarioCode, step.getStepCode(), key, output.get(key), step.getCacheSeconds());
                    }
                }
            } else {
                // 缓存全部输出字段
                scenarioCacheService.putAll(scenarioId, scenarioCode, step.getStepCode(), output, step.getCacheSeconds());
            }
        } catch (Exception e) {
            log.warn("[cache] 写入场景缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...(truncated)";
    }

    /**
     * 执行上下文
     */
    private static class ExecutionContext {
        private Map<String, Object> input = new ConcurrentHashMap<>();
        private Map<String, Map<String, Object>> steps = new ConcurrentHashMap<>();
        private Map<String, Object> metadata = new ConcurrentHashMap<>();

        public Map<String, Object> getInput() {
            return input;
        }

        public void setInput(Map<String, Object> input) {
            this.input = input;
        }

        public Map<String, Map<String, Object>> getSteps() {
            return steps;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new HashMap<>();
            result.put("input", input);
            result.put("steps", steps);
            result.put("metadata", metadata);
            return result;
        }
    }
}
