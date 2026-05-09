package com.integration.config.converter;

import cn.hutool.json.JSONUtil;
import com.integration.config.dto.AssertionDTO;
import com.integration.config.dto.AssertionResultDTO;
import com.integration.config.enums.ErrorCode;
import com.integration.config.exception.BusinessException;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 测试断言引擎
 * 支持 JSON Path 表达式 + 多种断言运算符
 */
@Component
@Slf4j
public class TestAssertionEngine {

    private static final Pattern JSON_PATH_PATTERN = Pattern.compile("\\$\\.\\S+");

    /**
     * 执行断言
     * @param jsonResponse 原始 JSON 响应字符串
     * @param assertions 断言配置列表
     * @return 断言结果列表
     */
    public List<AssertionResultDTO> execute(String jsonResponse, List<AssertionDTO> assertions) {
        List<AssertionResultDTO> results = new ArrayList<>();
        if (assertions == null || assertions.isEmpty()) {
            return results;
        }

        for (AssertionDTO assertion : assertions) {
            AssertionResultDTO result = executeOne(jsonResponse, assertion);
            results.add(result);
        }
        return results;
    }

    /**
     * 执行单个断言
     */
    public AssertionResultDTO executeOne(String jsonResponse, AssertionDTO assertion) {
        AssertionResultDTO result = new AssertionResultDTO();
        result.setId(assertion.getId());
        result.setField(assertion.getField());
        result.setOperator(assertion.getOperator());
        result.setExpected(assertion.getExpected());

        try {
            String actual = extractValue(jsonResponse, assertion.getField());
            result.setActual(actual);

            boolean passed = evaluate(actual, assertion.getOperator(), assertion.getExpected());
            result.setStatus(passed ? "PASS" : "FAIL");

            if (!passed) {
                String msg = assertion.getMessage();
                if (!StringUtils.hasText(msg)) {
                    msg = String.format("字段 [%s] 断言失败: 期望 %s [%s]，实际 [%s]",
                            assertion.getField(), assertion.getOperator(), assertion.getExpected(), actual);
                }
                result.setMessage(msg);
            } else {
                result.setMessage("断言通过");
            }
        } catch (Exception e) {
            result.setStatus("FAIL");
            result.setMessage("断言执行异常: " + e.getMessage());
            log.warn("[TestAssertion] 断言执行异常 field={}, error={}", assertion.getField(), e.getMessage());
        }
        return result;
    }

    /**
     * 从 JSON 字符串中提取指定字段值
     * 支持 JSON Path 表达式（如 $.code、$.data.items[0].name）
     */
    public String extractValue(String jsonResponse, String jsonPath) {
        if (jsonPath == null || jsonPath.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "JSON Path 不能为空");
        }

        // 基础校验：必须以 $. 开头
        if (!jsonPath.startsWith("$.")) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "无效的 JSON Path，应以 $. 开头: " + jsonPath);
        }

        try {
            // 处理数组索引访问，如 $.data.items[0]
            // Jayway JsonPath 支持直接表达式，但需要确保输入是有效 JSON
            Object value = JsonPath.read(jsonResponse, jsonPath);
            return valueToString(value);
        } catch (Exception e) {
            // 兼容：字段名不带 $.(如直接写 code)
            try {
                Object value = JsonPath.read(jsonResponse, "$.data." + jsonPath);
                return valueToString(value);
            } catch (Exception ex) {
                throw new RuntimeException("无法提取字段 [" + jsonPath + "]: " + ex.getMessage());
            }
        }
    }

    /**
     * 根据运算符计算断言结果
     */
    public boolean evaluate(String actual, String operator, String expected) {
        if (actual == null) actual = "";
        if (expected == null) expected = "";

        switch (operator.toUpperCase()) {
            case "EQUALS":
                return actual.equals(expected);
            case "NOT_EQUALS":
                return !actual.equals(expected);
            case "CONTAINS":
                return actual.contains(expected);
            case "NOT_CONTAINS":
                return !actual.contains(expected);
            case "GREATER_THAN":
                try {
                    return Double.parseDouble(actual) > Double.parseDouble(expected);
                } catch (NumberFormatException e) {
                    return false;
                }
            case "LESS_THAN":
                try {
                    return Double.parseDouble(actual) < Double.parseDouble(expected);
                } catch (NumberFormatException e) {
                    return false;
                }
            case "STARTS_WITH":
                return actual.startsWith(expected);
            case "ENDS_WITH":
                return actual.endsWith(expected);
            case "REGEX":
                return Pattern.matches(expected, actual);
            case "IN":
                String[] parts = expected.split(",");
                for (String p : parts) {
                    if (p.trim().equals(actual)) return true;
                }
                return false;
            case "NOT_IN":
                String[] parts2 = expected.split(",");
                for (String p : parts2) {
                    if (p.trim().equals(actual)) return true;
                }
                return false;
            default:
                throw new BusinessException(ErrorCode.INVALID_PARAM, "不支持的断言运算符: " + operator);
        }
    }

    private String valueToString(Object value) {
        if (value == null) return "";
        if (value instanceof String) return (String) value;
        return JSONUtil.toJsonStr(value);
    }

    /**
     * 解析 JSON 字符串为断言 DTO 列表
     */
    public List<AssertionDTO> parseAssertions(String assertionsJson) {
        if (!StringUtils.hasText(assertionsJson)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(assertionsJson, AssertionDTO.class);
        } catch (Exception e) {
            log.warn("解析断言配置失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}