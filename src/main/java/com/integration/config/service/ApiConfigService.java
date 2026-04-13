package com.integration.config.service;

import com.integration.config.dto.*;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.enums.Status;
import com.integration.config.repository.config.ApiConfigRepository;
import com.integration.config.repository.log.InvokeLogRepository;
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

    // ==================== 私有方法 ====================

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
}
