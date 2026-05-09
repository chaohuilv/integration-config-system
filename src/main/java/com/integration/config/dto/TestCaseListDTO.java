package com.integration.config.dto;

import lombok.Data;

import java.util.List;

/**
 * 测试用例 DTO（列表展示）
 */
@Data
public class TestCaseListDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String groupName;
    private String status;
    private Integer priority;
    private String tags;
    private Integer timeoutMs;
    private String failureStrategy;
    private Boolean notifyOnFailure;
    private Long stepCount;       // 步骤数量
    private String createdAt;
    private String createdByName;
}