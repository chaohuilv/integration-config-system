package com.integration.config.dto;

import lombok.Data;

/**
 * 执行单个测试用例请求 DTO
 */
@Data
public class TestRunRequestDTO {
    /** 用例ID（与 code 二选一） */
    private Long caseId;

    /** 用例编码（与 id 二选一） */
    private String caseCode;

    /** 自定义执行参数（JSON），可在步骤的 requestOverride 中引用 */
    private String params;
}