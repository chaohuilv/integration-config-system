package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 场景执行请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioExecuteRequestDTO {

    /**
     * 场景编码（可选，如果传了 scenarioId 则不需要）
     */
    private String scenarioCode;

    /**
     * 场景ID（可选，如果传了 scenarioCode 则不需要）
     */
    private Long scenarioId;

    /**
     * 输入参数
     */
    private Map<String, Object> params;

    /**
     * 是否异步执行
     */
    private Boolean async;

    /**
     * 触发来源：MANUAL / SCHEDULE / API
     */
    private String triggerSource;
}
