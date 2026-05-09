package com.integration.config.dto;

import lombok.Data;
import java.util.List;

/**
 * 测试执行结果 DTO（用例级）
 */
@Data
public class TestExecutionResultDTO {
    private Long executionId;
    private Long testCaseId;
    private String testCaseCode;
    private String status;
    private Long costTimeMs;
    private Integer totalSteps;
    private Integer passedSteps;
    private Integer failedSteps;
    private Integer skippedSteps;
    private String errorMessage;
    private List<TestStepResultDTO> stepResults;
}