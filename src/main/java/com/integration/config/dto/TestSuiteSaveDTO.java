package com.integration.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建/更新测试套件请求 DTO
 */
@Data
public class TestSuiteSaveDTO {
    private Long id;

    @NotBlank(message = "套件编码不能为空")
    private String code;

    @NotBlank(message = "套件名称不能为空")
    private String name;

    private String description;
    private String groupName;
    private String status;
    private String executionMode;
    private Integer concurrency;
    private Boolean stopOnFirstFailure;
    private Integer timeoutMs;
    private Boolean notifyOnComplete;
}