package com.integration.config.dto;

import lombok.Data;

/**
 * 单个断言配置 DTO
 */
@Data
public class AssertionDTO {
    /** 断言ID */
    private String id;
    /** JSON Path 表达式，如 $.code */
    private String field;
    /** 运算符 */
    private String operator;
    /** 期望值 */
    private String expected;
    /** 失败消息 */
    private String message;
}