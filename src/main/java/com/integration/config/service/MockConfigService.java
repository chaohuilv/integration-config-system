package com.integration.config.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.integration.config.dto.MockMatchRuleDTO;
import com.integration.config.entity.config.MockConfig;
import com.integration.config.exception.BusinessException;
import com.integration.config.enums.ErrorCode;
import com.integration.config.repository.config.MockConfigRepository;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockConfigService {

    private final MockConfigRepository mockConfigRepository;
    private final ObjectMapper objectMapper;
    private final MockTemplateEngine templateEngine;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // ==================== CRUD 操作 ====================

    @Transactional
    public MockConfig create(MockConfig config) {
        // 编码唯一性校验
        if (mockConfigRepository.existsByCode(config.getCode())) {
            throw new BusinessException(ErrorCode.ALREADY_EXISTS, "Mock 编码已存在: " + config.getCode());
        }
        return mockConfigRepository.save(config);
    }

    @Transactional
    public MockConfig update(Long id, MockConfig updates) {
        MockConfig existing = mockConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Mock 配置不存在: " + id));

        // 编码唯一性校验（排除自身）
        if (!existing.getCode().equals(updates.getCode()) &&
            mockConfigRepository.existsByCodeAndIdNot(updates.getCode(), id)) {
            throw new BusinessException(ErrorCode.ALREADY_EXISTS, "Mock 编码已存在: " + updates.getCode());
        }

        // 更新字段
        existing.setCode(updates.getCode());
        existing.setName(updates.getName());
        existing.setPath(updates.getPath());
        existing.setMethod(updates.getMethod());
        existing.setStatusCode(updates.getStatusCode());
        existing.setResponseBody(updates.getResponseBody());
        existing.setResponseHeaders(updates.getResponseHeaders());
        existing.setDelayMs(updates.getDelayMs());
        existing.setMatchRules(updates.getMatchRules());
        existing.setEnabled(updates.getEnabled());
        existing.setGroupName(updates.getGroupName());
        existing.setPriority(updates.getPriority());
        existing.setDescription(updates.getDescription());
        existing.setUpdatedBy(updates.getUpdatedBy());

        return mockConfigRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        if (!mockConfigRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Mock 配置不存在: " + id);
        }
        mockConfigRepository.deleteById(id);
    }

    public Optional<MockConfig> findById(Long id) {
        return mockConfigRepository.findById(id);
    }

    public Optional<MockConfig> findByCode(String code) {
        return mockConfigRepository.findByCode(code);
    }

    public Page<MockConfig> pageQuery(String groupName, Boolean enabled, String keyword, Pageable pageable) {
        return mockConfigRepository.pageQuery(groupName, enabled, keyword, pageable);
    }

    public List<String> getAllGroupNames() {
        return mockConfigRepository.findAllGroupNames();
    }

    // ==================== Mock 匹配与执行 ====================

    /**
     * 查找匹配的 Mock 配置
     *
     * @param requestPath   请求路径
     * @param method        HTTP 方法
     * @param queryParams   查询参数
     * @param headers       请求头
     * @param body          请求体
     * @return 匹配的 Mock 配置，无匹配返回 Optional.empty()
     */
    public Optional<MockConfig> findMatch(String requestPath, String method,
                                          Map<String, String[]> queryParams,
                                          Map<String, String> headers,
                                          String body) {
        // 获取所有启用的配置（已按优先级排序）
        List<MockConfig> allEnabled = mockConfigRepository.findAllEnabledOrderByPriority();

        for (MockConfig config : allEnabled) {
            if (matches(config, requestPath, method, queryParams, headers, body)) {
                return Optional.of(config);
            }
        }

        return Optional.empty();
    }

    /**
     * 判断请求是否匹配 Mock 配置
     */
    private boolean matches(MockConfig config, String requestPath, String method,
                           Map<String, String[]> queryParams,
                           Map<String, String> headers,
                           String body) {
        // 1. 方法必须匹配
        if (!config.getMethod().equalsIgnoreCase(method)) {
            log.info("Mock 匹配失败 [code={}]: 方法不匹配 config={}, request={}",
                    config.getCode(), config.getMethod(), method);
            return false;
        }

        // 2. 路径匹配（支持 Ant 风格）
        if (!pathMatcher.match(config.getPath(), requestPath)) {
            log.info("Mock 匹配失败 [code={}]: 路径不匹配 config={}, request={}",
                    config.getCode(), config.getPath(), requestPath);
            return false;
        }

        // 3. 如果有自定义匹配规则，执行规则匹配
        if (StringUtils.hasText(config.getMatchRules())) {
            try {
                List<MockMatchRuleDTO> rules = objectMapper.readValue(
                        config.getMatchRules(),
                        new TypeReference<List<MockMatchRuleDTO>>() {}
                );

                for (MockMatchRuleDTO rule : rules) {
                    if (!matchRule(rule, requestPath, queryParams, headers, body)) {
                        log.info("Mock 匹配失败 [code={}]: 规则不匹配 type={}, field={}, operator={}, value={}, negate={}",
                                config.getCode(), rule.getType(), rule.getField(),
                                rule.getOperator(), rule.getValue(), rule.getNegate());
                        return false;
                    }
                }
            } catch (Exception e) {
                log.warn("解析 Mock 匹配规则失败 [id={}, code={}]: {}",
                        config.getId(), config.getCode(), e.getMessage());
                // 规则解析失败，视为不匹配
                return false;
            }
        }

        return true;
    }

    /**
     * 执行单条匹配规则
     */
    private boolean matchRule(MockMatchRuleDTO rule, String requestPath,
                              Map<String, String[]> queryParams,
                              Map<String, String> headers,
                              String body) {
        String actualValue = extractActualValue(rule, requestPath, queryParams, headers, body);
        if (actualValue == null && !"EXISTS".equals(rule.getOperator())) {
            return Boolean.TRUE.equals(rule.getNegate()); // 不存在时，取反规则为 true
        }

        boolean matched;
        switch (rule.getOperator()) {
            case "EQUALS":
                matched = actualValue != null && actualValue.equals(rule.getValue());
                break;
            case "CONTAINS":
                matched = actualValue != null && actualValue.contains(rule.getValue());
                break;
            case "REGEX":
                matched = actualValue != null && Pattern.matches(rule.getValue(), actualValue);
                break;
            case "EXISTS":
                matched = actualValue != null;
                break;
            case "JSON_PATH":
                matched = matchJsonPath(body, rule.getField(), rule.getValue());
                break;
            default:
                matched = false;
        }

        return Boolean.TRUE.equals(rule.getNegate()) ? !matched : matched;
    }

    /**
     * 提取实际值
     */
    private String extractActualValue(MockMatchRuleDTO rule, String requestPath,
                                       Map<String, String[]> queryParams,
                                       Map<String, String> headers,
                                       String body) {
        if (rule.getType() == null) return null;

        switch (rule.getType().toUpperCase()) {
            case "PATH":
                // 可以用 $request.path.xxx 获取路径变量
                Map<String, String> pathVars = extractPathVariables(requestPath);
                return pathVars.get(rule.getField());
            case "HEADER":
                return headers != null ? headers.get(rule.getField()) : null;
            case "QUERY":
                String[] values = queryParams != null ? queryParams.get(rule.getField()) : null;
                return values != null && values.length > 0 ? values[0] : null;
            case "BODY":
                return extractFromBody(body, rule.getField());
            default:
                return null;
        }
    }

    /**
     * 从请求体提取值（JSON Path）
     */
    private String extractFromBody(String body, String jsonPath) {
        if (!StringUtils.hasText(body) || !StringUtils.hasText(jsonPath)) {
            return null;
        }
        try {
            Object value = JsonPath.read(body, jsonPath.startsWith("$") ? jsonPath : "$." + jsonPath);
            return value != null ? value.toString() : null;
        } catch (PathNotFoundException e) {
            return null;
        } catch (Exception e) {
            log.debug("JSON Path 提取失败 [path={}]: {}", jsonPath, e.getMessage());
            return null;
        }
    }

    /**
     * JSON Path 匹配
     */
    private boolean matchJsonPath(String body, String jsonPath, String expected) {
        String actual = extractFromBody(body, jsonPath);
        return expected.equals(actual);
    }

    /**
     * 提取路径变量
     */
    private Map<String, String> extractPathVariables(String requestPath) {
        // 简单实现：返回空 Map，完整实现需要知道模板路径
        return Collections.emptyMap();
    }

    /**
     * 执行 Mock 响应
     *
     * @param config   Mock 配置
     * @param context  模板上下文
     * @return 渲染后的响应体
     */
    public String executeMock(MockConfig config, MockTemplateEngine.MockContext context) {
        // 更新命中统计
        config.setHitCount(config.getHitCount() + 1);
        config.setLastHitTime(LocalDateTime.now());
        mockConfigRepository.save(config);

        // 渲染响应模板
        return templateEngine.render(config.getResponseBody(), context);
    }

    /**
     * 解析响应头配置
     */
    public Map<String, String> parseResponseHeaders(String responseHeadersJson) {
        if (!StringUtils.hasText(responseHeadersJson)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(responseHeadersJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("解析响应头配置失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ==================== 统计与操作 ====================

    @Transactional
    public void toggleEnabled(Long id) {
        MockConfig config = mockConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Mock 配置不存在: " + id));
        config.setEnabled(!config.getEnabled());
        mockConfigRepository.save(config);
    }

    @Transactional
    public void resetHitCount(Long id) {
        MockConfig config = mockConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Mock 配置不存在: " + id));
        config.setHitCount(0);
        config.setLastHitTime(null);
        mockConfigRepository.save(config);
    }

    public long countEnabled() {
        return mockConfigRepository.countEnabled();
    }
}
