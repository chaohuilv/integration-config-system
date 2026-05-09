package com.integration.config.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 测试用例详情 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TestCaseDetailDTO extends TestCaseListDTO {
    private List<TestCaseStepDTO> steps;
}