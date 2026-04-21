package com.integration.config.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.exception.BusinessException;
import com.integration.config.repository.config.ApiConfigRepository;
import com.integration.config.enums.ErrorCode;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * API 文档服务
 * 根据 ApiConfig 自动生成接口文档模型
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiDocService {

    private final ApiConfigRepository apiConfigRepository;
    private final ObjectMapper objectMapper;

    /**
     * 获取所有启用的接口文档列表（分组）
     */
    @Transactional(readOnly = true)
    public List<ApiDocGroup> getAllGroupedDocs() {
        List<ApiConfig> apis = apiConfigRepository.findByStatusOrderByGroupNameAscCreatedAtDesc(
                com.integration.config.enums.Status.ACTIVE);

        // 按分组聚合
        Map<String, List<ApiDocItem>> groupMap = new LinkedHashMap<>();
        for (ApiConfig api : apis) {
            String group = api.getGroupName() != null ? api.getGroupName() : "未分组";
            groupMap.computeIfAbsent(group, k -> new ArrayList<>()).add(toDocItem(api));
        }

        return groupMap.entrySet().stream()
                .map(e -> new ApiDocGroup(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 获取单个接口的完整文档
     */
    @Transactional(readOnly = true)
    public ApiDocDetail getDocDetail(Long id) {
        ApiConfig api = apiConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "接口不存在"));
        return toDocDetail(api);
    }

    /**
     * 生成接口的在线调试地址
     */
    public String buildDebugUrl(ApiConfig api, String baseUrl) {
        // 从系统环境配置中获取 baseUrl，简化版直接返回调试页面路径
        return "/pages/api_debug.html?apiCode=" + api.getCode();
    }

    // ==================== 转换方法 ====================

    private ApiDocItem toDocItem(ApiConfig api) {
        return ApiDocItem.builder()
                .id(api.getId())
                .code(api.getCode())
                .name(api.getName())
                .method(api.getMethod() != null ? api.getMethod().name() : null)
                .url(api.getUrl())
                .groupName(api.getGroupName())
                .version(api.getVersion())
                .latestVersion(Boolean.TRUE.equals(api.getLatestVersion()))
                .deprecated(Boolean.TRUE.equals(api.getDeprecated()))
                .description(api.getDescription())
                .build();
    }

    private ApiDocDetail toDocDetail(ApiConfig api) {
        // 解析 requestParams JSON
        List<ApiParam> queryParams = parseParams(api.getRequestParams(), "query");
        List<ApiParam> bodyParams = parseParams(api.getRequestBody(), "body");

        // 解析请求头
        List<ApiParam> headerParams = parseHeaders(api.getHeaders());

        return ApiDocDetail.builder()
                .id(api.getId())
                .code(api.getCode())
                .name(api.getName())
                .description(api.getDescription())
                .method(api.getMethod() != null ? api.getMethod().name() : null)
                .url(api.getUrl())
                .groupName(api.getGroupName())
                .version(api.getVersion())
                .latestVersion(Boolean.TRUE.equals(api.getLatestVersion()))
                .deprecated(Boolean.TRUE.equals(api.getDeprecated()))
                .contentType(api.getContentType() != null ? api.getContentType().name() : null)
                .authType(api.getAuthType())
                .timeout(api.getTimeout())
                .retryCount(api.getRetryCount())
                .queryParams(queryParams)
                .bodyParams(bodyParams)
                .headers(headerParams)
                .build();
    }

    /**
     * 解析 JSON 参数定义为 ApiParam 列表
     */
    @SuppressWarnings("unchecked")
    private List<ApiParam> parseParams(String json, String in) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            Map<String, Object> params = objectMapper.readValue(json, Map.class);
            List<ApiParam> result = new ArrayList<>();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                result.add(ApiParam.builder()
                        .name(entry.getKey())
                        .in(in)
                        .required(true)
                        .type("string")
                        .example(String.valueOf(entry.getValue()))
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.warn("解析参数定义失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析请求头字符串（每行 "Key: Value"）
     */
    private List<ApiParam> parseHeaders(String headers) {
        if (headers == null || headers.isBlank()) return Collections.emptyList();
        List<ApiParam> result = new ArrayList<>();
        for (String line : headers.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int idx = line.indexOf(':');
            if (idx > 0) {
                result.add(ApiParam.builder()
                        .name(line.substring(0, idx).trim())
                        .in("header")
                        .required(true)
                        .type("string")
                        .example(line.substring(idx + 1).trim())
                        .build());
            }
        }
        return result;
    }

    // ==================== 内部模型类 ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiDocGroup {
        private String groupName;
        private List<ApiDocItem> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiDocItem {
        private Long id;
        private String code;
        private String name;
        private String method;     // GET/POST/PUT/DELETE
        private String url;
        private String groupName;
        private String version;
        private boolean latestVersion;
        private boolean deprecated;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiDocDetail {
        private Long id;
        private String code;
        private String name;
        private String description;
        private String method;
        private String url;
        private String groupName;
        private String version;
        private boolean latestVersion;
        private boolean deprecated;
        private String contentType;
        private String authType;
        private Integer timeout;
        private Integer retryCount;
        private List<ApiParam> queryParams;
        private List<ApiParam> bodyParams;
        private List<ApiParam> headers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiParam {
        private String name;
        private String in;         // query / body / header / path
        private boolean required;
        private String type;       // string / number / boolean / object
        private String description;
        private String example;
    }
}
