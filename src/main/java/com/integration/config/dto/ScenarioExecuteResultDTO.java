package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 场景执行结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioExecuteResultDTO {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 执行记录ID
     */
    private Long executionId;

    /**
     * 场景编码
     */
    private String scenarioCode;

    /**
     * 场景名称
     */
    private String scenarioName;

    /**
     * 执行状态：RUNNING / SUCCESS / FAILED / PARTIAL
     */
    private String status;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 耗时（毫秒）
     */
    private Long costTimeMs;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 执行上下文（包含所有步骤输出）
     */
    private Map<String, Object> context;

    /**
     * 步骤执行结果列表
     */
    private java.util.List<StepResultDTO> steps;

    /**
     * 步骤执行结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepResultDTO {
        private String stepCode;
        private String stepName;
        private Integer stepOrder;
        private String status;
        private Object output;
        private String errorMessage;
        private Long costTimeMs;
    }
}
