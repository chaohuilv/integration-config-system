package com.integration.config.dto;

import lombok.Data;
import java.util.List;

/**
 * 测试执行结果 DTO（步骤级）
 */
@Data
public class TestStepResultDTO {
    private Integer stepOrder;
    private String stepCode;
    private String stepType;
    private String targetCode;
    private String status;
    private Long costTimeMs;
    private String requestParams;
    private String responseData;
    private List<AssertionResultDTO> assertionResults;
    private String errorMessage;
}