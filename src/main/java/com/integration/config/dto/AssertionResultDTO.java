package com.integration.config.dto;

import lombok.Data;

/**
 * 断言执行结果 DTO
 */
@Data
public class AssertionResultDTO {
    private String id;
    private String field;
    private String operator;
    private String expected;
    private String actual;
    private String status;   // PASS / FAIL
    private String message;
}