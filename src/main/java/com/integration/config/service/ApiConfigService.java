package com.integration.config.service;

import com.integration.config.dto.*;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.enums.HttpMethod;
import com.integration.config.enums.Status;
import com.integration.config.repository.config.ApiConfigRepository;
import com.integration.config.repository.log.InvokeLogRepository;
import com.integration.config.util.CurlParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 接口配置管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiConfigService {

    private final ApiConfigRepository apiConfigRepository;
    private final InvokeLogRepository invokeLogRepository;

    /**
     * 创建接口配置
     */
    @Transactional
    public ApiConfig create(ApiConfigDTO dto) {
        // 检查编码唯一性
        if (apiConfigRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("接口编码已存在: " + dto.getCode());
        }

        ApiConfig entity = toEntity(dto);
        return apiConfigRepository.save(entity);
    }

    /**
     * 更新接口配置
     */
    @Transactional
    public ApiConfig update(Long id, ApiConfigDTO dto) {
        ApiConfig entity = apiConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("接口配置不存在: " + id));

        // 检查编码唯一性（排除自身）
        if (!entity.getCode().equals(dto.getCode()) 
                && apiConfigRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("接口编码已存在: " + dto.getCode());
        }

        updateEntity(entity, dto);
        return apiConfigRepository.save(entity);
    }

    /**
     * 删除接口配置
     */
    @Transactional
    public void delete(Long id) {
        if (!apiConfigRepository.existsById(id)) {
            throw new IllegalArgumentException("接口配置不存在: " + id);
        }
        apiConfigRepository.deleteById(id);
    }

    /**
     * 根据ID查询
     */
    public ApiConfigDetailDTO getById(Long id) {
        ApiConfig entity = apiConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("接口配置不存在: " + id));
        ApiConfigDetailDTO dto = ApiConfigDetailDTO.fromEntity(entity);
        
        // 补充统计信息
        Long invokeCount = invokeLogRepository.countByApiCode(entity.getCode());
        Long successCount = invokeLogRepository.countSuccessByApiCode(entity.getCode());
        dto.setInvokeCount(invokeCount);
        dto.setSuccessCount(successCount);
        dto.setSuccessRate(invokeCount > 0 ? (double) successCount / invokeCount * 100 : 0.0);
        
        return dto;
    }

    /**
     * 根据编码查询
     */
    public ApiConfig getByCode(String code) {
        return apiConfigRepository.findByCodeAndStatus(code, Status.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("接口配置不存在或已禁用: " + code));
    }

    /**
     * 分页查询
     */
    public PageResult<ApiConfig> pageQuery(String keyword, Status status, Integer page, Integer size) {
        Page<ApiConfig> pageResult = apiConfigRepository.findByKeywordAndStatus(
                keyword, status, PageRequest.of(page - 1, size));
        return PageResult.of(pageResult.getContent(), pageResult.getTotalElements(), page, size);
    }

    /**
     * 获取所有启用的接口
     */
    public List<ApiConfig> getAllActive() {
        return apiConfigRepository.findByStatusOrderByCreatedAtDesc(Status.ACTIVE);
    }

    /**
     * 切换状态
     */
    @Transactional
    public void toggleStatus(Long id) {
        ApiConfig entity = apiConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("接口配置不存在: " + id));
        entity.setStatus(entity.getStatus() == Status.ACTIVE ? Status.INACTIVE : Status.ACTIVE);
        apiConfigRepository.save(entity);
    }

    /**
     * 获取接口列表（简化信息）
     */
    public List<Map<String, Object>> getSimpleList() {
        return apiConfigRepository.findAll().stream()
                .map(config -> Map.<String, Object>of(
                        "id", config.getId(),
                        "code", config.getCode(),
                        "name", config.getName(),
                        "method", config.getMethod(),
                        "status", config.getStatus(),
                        "groupName", config.getGroupName() != null ? config.getGroupName() : ""
                ))
                .collect(Collectors.toList());
    }

    /**
     * 根据 curl 命令一键导入接口配置
     * @param curl curl 命令字符串
     * @return 导入结果，包含解析后的配置信息
     */
    public ApiConfigDTO importFromCurl(String curl) {
        CurlImportDTO parsed = CurlParser.parse(curl);
        if (!parsed.isSuccess()) {
            throw new IllegalArgumentException(parsed.getMessage());
        }

        // 检查编码是否已存在，存在则追加后缀
        String code = parsed.getCode();
        if (apiConfigRepository.existsByCode(code)) {
            code = code + "-" + System.currentTimeMillis() % 1000;
        }

        // 构建 DTO
        ApiConfigDTO dto = ApiConfigDTO.builder()
                .name(parsed.getName())
                .code(code)
                .url(parsed.getUrl())
                .method(parseMethod(parsed.getMethod()))
                .headers(buildHeadersJson(parsed.getHeaders(), parsed.getAuthParamName()))
                .requestParams(parsed.getRequestParams())
                .requestBody(parsed.getBody())
                .authType(parsed.getAuthType())
                .authInfo(parsed.getAuthValue())
                .timeout(30000)
                .retryCount(0)
                .status(Status.ACTIVE)
                .groupName(parsed.getGroupName())
                .enableDynamicToken(false)
                .build();

        // 保存
        ApiConfig entity = toEntity(dto);
        apiConfigRepository.save(entity);

        // 将解析结果回填到 DTO
        dto.setId(entity.getId());
        return dto;
    }

    // ==================== 私有方法 ====================

    private HttpMethod parseMethod(String method) {
        if (method == null) return HttpMethod.GET;
        try {
            return HttpMethod.valueOf(method.toUpperCase());
        } catch (Exception e) {
            return HttpMethod.GET;
        }
    }

    private ApiConfig toEntity(ApiConfigDTO dto) {
        return ApiConfig.builder()
                .name(dto.getName())
                .code(dto.getCode())
                .description(dto.getDescription())
                .method(dto.getMethod())
                .url(dto.getUrl())
                .contentType(dto.getContentType())
                .headers(dto.getHeaders())
                .requestParams(dto.getRequestParams())
                .requestBody(dto.getRequestBody())
                .authType(dto.getAuthType())
                .authInfo(dto.getAuthInfo())
                .timeout(dto.getTimeout())
                .retryCount(dto.getRetryCount())
                .enableCache(dto.getEnableCache())
                .cacheTime(dto.getCacheTime())
                .status(dto.getStatus() != null ? dto.getStatus() : Status.ACTIVE)
                .groupName(dto.getGroupName())
                .enableDynamicToken(dto.getEnableDynamicToken())
                .tokenApiCode(dto.getTokenApiCode())
                .tokenExtractPath(dto.getTokenExtractPath())
                .tokenPosition(dto.getTokenPosition())
                .tokenParamName(dto.getTokenParamName())
                .tokenPrefix(dto.getTokenPrefix())
                .tokenCacheTime(dto.getTokenCacheTime())
                .build();
    }

    private void updateEntity(ApiConfig entity, ApiConfigDTO dto) {
        entity.setName(dto.getName());
        entity.setCode(dto.getCode());
        entity.setDescription(dto.getDescription());
        entity.setMethod(dto.getMethod());
        entity.setUrl(dto.getUrl());
        entity.setContentType(dto.getContentType());
        entity.setHeaders(dto.getHeaders());
        entity.setRequestParams(dto.getRequestParams());
        entity.setRequestBody(dto.getRequestBody());
        entity.setAuthType(dto.getAuthType());
        entity.setAuthInfo(dto.getAuthInfo());
        entity.setTimeout(dto.getTimeout());
        entity.setRetryCount(dto.getRetryCount());
        entity.setEnableCache(dto.getEnableCache());
        entity.setCacheTime(dto.getCacheTime());
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        entity.setGroupName(dto.getGroupName());
        // 动态Token配置
        entity.setEnableDynamicToken(dto.getEnableDynamicToken());
        entity.setTokenApiCode(dto.getTokenApiCode());
        entity.setTokenExtractPath(dto.getTokenExtractPath());
        entity.setTokenPosition(dto.getTokenPosition());
        entity.setTokenParamName(dto.getTokenParamName());
        entity.setTokenPrefix(dto.getTokenPrefix());
        entity.setTokenCacheTime(dto.getTokenCacheTime());
    }

    /**
     * 从 URL 中提取分组名称
     */
    private String extractGroupName(String url) {
        try {
            if (url == null) return "默认分组";
            int idx = url.indexOf("//");
            if (idx < 0) return "默认分组";
            idx = url.indexOf("/", idx + 2);
            if (idx < 0) return "默认分组";
            String path = url.substring(idx + 1);
            String[] parts = path.split("/");
            if (parts.length > 1) {
                String group = parts[0];
                if (group.length() > 1) {
                    group = group.substring(0, 1).toUpperCase() + group.substring(1);
                } else if (group.length() == 1) {
                    group = group.toUpperCase();
                }
                return group;
            }
            return "默认分组";
        } catch (Exception e) {
            return "默认分组";
        }
    }

    /**
     * 构建请求头 JSON 字符串（排除认证头）
     */
    private String buildHeadersJson(java.util.Map<String, String> headers, String authParamName) {
        if (headers == null || headers.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
            if (authParamName != null && entry.getKey().equalsIgnoreCase(authParamName)) {
                continue;
            }
            if (!first) sb.append("\n");
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
            first = false;
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
