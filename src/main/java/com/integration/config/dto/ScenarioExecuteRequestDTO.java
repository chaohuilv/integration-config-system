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
     * 场景编码（推荐，优先使用编码调用）
     */
    private String scenarioCode;

    /**
     * 场景ID（兼容旧调用方式，不推荐）
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
