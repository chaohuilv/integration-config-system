package com.integration.config.dto;

import com.integration.config.entity.config.ApiConfig;
import com.integration.config.enums.ContentType;
import com.integration.config.enums.HttpMethod;
import com.integration.config.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 接口配置详情 DTO（响应）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiConfigDetailDTO {

    private Long id;
    private String name;
    private String code;
    private String description;
    private HttpMethod method;
    private String url;
    private ContentType contentType;
    private String headers;
    private String requestParams;
    private String requestBody;
    private String authType;
    private String authInfo;
    private Integer timeout;
    private Integer retryCount;
    private Boolean enableCache;
    private Integer cacheTime;
    private Status status;
    private String groupName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;

    // ========== 动态Token配置 ==========
    private Boolean enableDynamicToken;
    private String tokenApiCode;
    private String tokenExtractPath;
    private String tokenPosition;
    private String tokenParamName;
    private String tokenPrefix;
    private Integer tokenCacheTime;

    /**
     * 调用统计
     */
    private Long invokeCount;
    private Long successCount;
    private Double successRate;

    /**
     * 从实体转换
     */
    public static ApiConfigDetailDTO fromEntity(ApiConfig entity) {
        return ApiConfigDetailDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .code(entity.getCode())
                .description(entity.getDescription())
                .method(entity.getMethod())
                .url(entity.getUrl())
                .contentType(entity.getContentType())
                .headers(entity.getHeaders())
                .requestParams(entity.getRequestParams())
                .requestBody(entity.getRequestBody())
                .authType(entity.getAuthType())
                .authInfo(entity.getAuthInfo())
                .timeout(entity.getTimeout())
                .retryCount(entity.getRetryCount())
                .enableCache(entity.getEnableCache())
                .cacheTime(entity.getCacheTime())
                .status(entity.getStatus())
                .groupName(entity.getGroupName())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .enableDynamicToken(entity.getEnableDynamicToken())
                .tokenApiCode(entity.getTokenApiCode())
                .tokenExtractPath(entity.getTokenExtractPath())
                .tokenPosition(entity.getTokenPosition())
                .tokenParamName(entity.getTokenParamName())
                .tokenPrefix(entity.getTokenPrefix())
                .tokenCacheTime(entity.getTokenCacheTime())
                .build();
    }
}
