package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 场景步骤 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioStepDTO {

    private Long id;
    private Long scenarioId;
    private String stepCode;
    private String stepName;
    private Integer stepOrder;
    private String apiCode;
    private String inputMapping;
    private String outputMapping;
    private String conditionExpr;
    private Integer skipOnError;
    private Integer retryCount;
    private Boolean enableCache;
    private Integer cacheSeconds;
    private String cacheKeys;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 接口名称（仅展示用）
     */
    private String apiName;
}
