package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 接口调用响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeResponseDTO {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 响应状态码
     */
    private Integer statusCode;

    /**
     * 响应数据
     */
    private Object data;

    /**
     * 错误信息
     */
    private String message;

    /**
     * 调用耗时（毫秒）
     */
    private Long costTime;

    /**
     * 调用时间
     */
    private LocalDateTime invokeTime;

    /**
     * 追踪ID
     */
    private String traceId;

    /**
     * 是否来自缓存
     */
    private Boolean fromCache;
}
