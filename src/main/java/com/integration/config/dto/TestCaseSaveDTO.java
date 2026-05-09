package com.integration.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建/更新测试用例请求 DTO
 */
@Data
public class TestCaseSaveDTO {
    private Long id;

    @NotBlank(message = "用例编码不能为空")
    private String code;

    @NotBlank(message = "用例名称不能为空")
    private String name;

    private String description;
    private String groupName;
    private String status;
    private Integer priority;
    private String tags;
    private Integer timeoutMs;
    private String failureStrategy;
    private Boolean notifyOnFailure;
}