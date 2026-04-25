package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Mock 匹配规则配置
 *
 * <p>用于 JSON 序列化/反序列化匹配规则
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockMatchRuleDTO {

    /** 匹配类型：PATH/METHOD/HEADER/QUERY/BODY */
    private String type;

    /** 字段名（Header/Query/Body 时使用） */
    private String field;

    /** 匹配操作：EQUALS/CONTAINS/REGEX/EXISTS/JSON_PATH */
    private String operator;

    /** 匹配值 */
    private String value;

    /** 是否取反 */
    private Boolean negate;

    /**
     * 批量创建路径匹配规则
     */
    public static MockMatchRuleDTO pathMatch(String pathPattern) {
        return MockMatchRuleDTO.builder()
                .type("PATH")
                .operator("REGEX")
                .value(pathPattern)
                .build();
    }

    /**
     * 批量创建方法匹配规则
     */
    public static MockMatchRuleDTO methodMatch(String method) {
        return MockMatchRuleDTO.builder()
                .type("METHOD")
                .operator("EQUALS")
                .value(method.toUpperCase())
                .build();
    }

    /**
     * 批量创建请求头匹配规则
     */
    public static MockMatchRuleDTO headerMatch(String headerName, String expectedValue) {
        return MockMatchRuleDTO.builder()
                .type("HEADER")
                .field(headerName)
                .operator("EQUALS")
                .value(expectedValue)
                .build();
    }

    /**
     * 批量创建查询参数匹配规则
     */
    public static MockMatchRuleDTO queryMatch(String paramName, String expectedValue) {
        return MockMatchRuleDTO.builder()
                .type("QUERY")
                .field(paramName)
                .operator("EQUALS")
                .value(expectedValue)
                .build();
    }

    /**
     * 批量创建请求体 JSON Path 匹配规则
     */
    public static MockMatchRuleDTO bodyJsonPathMatch(String jsonPath, String expectedValue) {
        return MockMatchRuleDTO.builder()
                .type("BODY")
                .field(jsonPath)
                .operator("JSON_PATH")
                .value(expectedValue)
                .build();
    }
}
