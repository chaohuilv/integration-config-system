package com.integration.config.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 测试套件详情 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TestSuiteDetailDTO extends TestSuiteListDTO {
    private List<Long> caseIds;  // 关联的用例ID列表
}