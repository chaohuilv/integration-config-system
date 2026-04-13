package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 接口调用请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeRequestDTO {

    /**
     * 接口编码（必填）
     */
    private String apiCode;

    /**
     * 动态请求头（会与配置合并，动态值优先）
     */
    private Map<String, String> headers;

    /**
     * 动态请求参数（会与配置合并）
     */
    private Map<String, Object> params;

    /**
     * 请求体（JSON字符串或普通字符串）
     */
    private String body;

    /**
     * 是否跳过配置的请求模板，直接使用传入的body
     */
    private Boolean skipTemplate;

    /**
     * 调用来源标识（用于追踪）
     */
    private String source;

    /**
     * 是否仅调试（不记录日志）
     */
    private Boolean debug;
}
