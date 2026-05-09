package com.integration.config.dto;

import lombok.Data;

/**
 * 测试套件列表 DTO
 */
@Data
public class TestSuiteListDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String groupName;
    private String status;
    private String executionMode;
    private Integer concurrency;
    private Boolean stopOnFirstFailure;
    private Integer timeoutMs;
    private Boolean notifyOnComplete;
    private Long caseCount;       // 用例数量
    private String createdAt;
    private String createdByName;
}