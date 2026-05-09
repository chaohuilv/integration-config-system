package com.integration.config.dto;

import lombok.Data;

/**
 * 测试用例步骤 DTO（用于传输）
 */
@Data
public class TestCaseStepDTO {
    private Long id;
    private Long testCaseId;
    private Integer stepOrder;
    private String stepCode;
    private String stepName;
    private String stepType;      // API / SCENARIO / MOCK
    private String targetCode;
    private Long targetId;
    private String requestOverride;
    private String assertions;    // JSON 数组
    private Integer skipOnError;
    private Integer timeoutMs;
    private Boolean enabled;
}