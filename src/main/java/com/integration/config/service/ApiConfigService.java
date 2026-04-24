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
     * 创建接口配置（第一版）
     */
    @Transactional
    public ApiConfig create(ApiConfigDTO dto, Long userId, String userName) {
        // 检查编码唯一性
        if (apiConfigRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("接口编码已存在: " + dto.getCode());
        }

        // 自动设置 baseCode（第一个版本的 code 即 baseCode）
        String baseCode = dto.getBaseCode();
        if (baseCode == null || baseCode.isEmpty()) {
            baseCode = dto.getCode();
        }

        // 版本默认 v1，且为第一个版本时默认 latestVersion=true
        String version = dto.getVersion();
        if (version == null || version.isEmpty()) {
            version = "v1";
        }

        ApiConfig entity = toEntity(dto);
        entity.setBaseCode(baseCode);
        entity.setVersion(version);
        entity.setLatestVersion(true);  // 第一版默认是最新
        entity.setDeprecated(false);
        entity.setCreatedById(userId);
        entity.setCreatedByName(userName);
        return apiConfigRepository.save(entity);
    }

    /**
     * 创建新版本（基于现有接口复制配置）
     */
    @Transactional
    public ApiConfig createNewVersion(Long sourceId, CreateVersionDTO dto, Long userId, String userName) {
        ApiConfig source = apiConfigRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("源接口不存在: " + sourceId));

        // 生成新版本号
        String nextVersion = nextVersion(source.getVersion());

        // 提取纯净的 baseCode（去掉 :v1, :v2 等版本后缀）
        String cleanBaseCode = source.getBaseCode();
        if (cleanBaseCode == null || cleanBaseCode.contains(":")) {
            // baseCode 为空或已包含版本号，从 code 提取纯净前缀
            String code = source.getCode();
            if (code != null && code.contains(":")) {
                cleanBaseCode = code.substring(0, code.lastIndexOf(":"));
            } else {
                cleanBaseCode = code;
            }
        }
        String newCode = cleanBaseCode + ":" + nextVersion;

        // 检查新编码是否已存在
        if (apiConfigRepository.existsByCode(newCode)) {
            throw new IllegalArgumentException("该版本已存在: " + newCode);
        }

        // 源版本的 latestVersion 改为 false
        source.setLatestVersion(false);
        apiConfigRepository.save(source);

        // 从源实体完整复制，再覆盖 dto 中的字段
        ApiConfig entity = copyEntity(source);
        // 覆盖业务字段（dto 为 null 的字段保留源接口的值）
        if (dto.getName() != null) {
            entity.setName(dto.getName());
        } else {
            // 自动更新名称中的版本号（如 "用户接口 v2" → "用户接口 v3"）
            String srcName = source.getName();
            if (srcName != null) {
                String oldVersion = source.getVersion();
                if (oldVersion != null && srcName.endsWith(" " + oldVersion)) {
                    // 名称以 " v2" 结尾，替换为新版本号
                    entity.setName(srcName.substring(0, srcName.lastIndexOf(" " + oldVersion)) + " " + nextVersion);
                } else {
                    // 名称不含旧版本号，追加新版本号
                    entity.setName(srcName + " " + nextVersion);
                }
            }
        }
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getUrl() != null) entity.setUrl(dto.getUrl());
        if (dto.getGroupName() != null) entity.setGroupName(dto.getGroupName());
        // 版本字段重新生成
        entity.setId(null);             // 交给 @PrePersist 生成雪花ID
        entity.setCode(newCode);
        entity.setBaseCode(cleanBaseCode); // 确保新版本使用纯净的 baseCode
        entity.setVersion(nextVersion);
        entity.setLatestVersion(true);  // 新版本默认是最新
        entity.setDeprecated(false);
        entity.setCreatedById(userId);
        entity.setCreatedByName(userName);
        entity.setUpdatedById(null);
        entity.setUpdatedByName(null);
        entity.setCreatedAt(null);
        entity.setUpdatedAt(null);
        return apiConfigRepository.save(entity);
    }

    /**
     * 深拷贝一个 ApiConfig 实体（用于创建新版本）
     */
    private ApiConfig copyEntity(ApiConfig src) {
        return ApiConfig.builder()
                .name(src.getName())
                .code(src.getCode())
                .description(src.getDescription())
                .method(src.getMethod())
                .url(src.getUrl())
                .contentType(src.getContentType())
                .headers(src.getHeaders())
                .requestParams(src.getRequestParams())
                .requestBody(src.getRequestBody())
                .authType(src.getAuthType())
                .authInfo(src.getAuthInfo())
                .timeout(src.getTimeout())
                .retryCount(src.getRetryCount())
                .enableCache(src.getEnableCache())
                .cacheTime(src.getCacheTime())
                .status(src.getStatus())
                .groupName(src.getGroupName())
                .enableDynamicToken(src.getEnableDynamicToken())
                .enableRateLimit(src.getEnableRateLimit())
                .rateLimitWindow(src.getRateLimitWindow())
                .rateLimitMax(src.getRateLimitMax())
                .tokenApiCode(src.getTokenApiCode())
                .tokenExtractPath(src.getTokenExtractPath())
                .tokenPosition(src.getTokenPosition())
                .tokenParamName(src.getTokenParamName())
                .tokenPrefix(src.getTokenPrefix())
                .tokenCacheTime(src.getTokenCacheTime())
                .enableTokenCache(src.getEnableTokenCache())
                .tokenCacheSeconds(src.getTokenCacheSeconds())
                .build();
    }

    /**
     * 更新接口配置
     */
    @Transactional
    public ApiConfig update(Long id, ApiConfigDTO dto, Long userId, String userName) {
        ApiConfig entity = apiConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("接口配置不存在: " + id));

        // 检查编码唯一性（排除自身）
        if (!entity.getCode().equals(dto.getCode())
                && apiConfigRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("接口编码已存在: " + dto.getCode());
        }

        // 禁止在编辑页修改版本号
        dto.setVersion(entity.getVersion());
        dto.setBaseCode(entity.getBaseCode());

        updateEntity(entity, dto);
        entity.setUpdatedById(userId);
        entity.setUpdatedByName(userName);
        return apiConfigRepository.save(entity);
    }

    /**
     * 删除接口配置
     */
    @Transactional
    public void delete(Long id) {
        ApiConfig entity = apiConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("接口配置不存在: " + id));

        // 如果删除的是最新版本，需要把上一个版本设为最新
        if (entity.getLatestVersion() != null && entity.getLatestVersion()) {
            String baseCode = entity.getBaseCode();

            // 第一步：按 baseCode 查（含 null 兼容）
            List<ApiConfig> versions = apiConfigRepository.findVersionsByBaseCode(baseCode);

            // 第二步：如果 baseCode 为空但 code 包含冒号，说明是旧数据（baseCode 未拆分）
            // 从 code 截取前缀作为兜底（user-api:v1 -> user-api）
            if ((versions == null || versions.isEmpty()) && baseCode == null && entity.getCode() != null && entity.getCode().contains(":")) {
                String fallbackBase = entity.getCode().substring(0, entity.getCode().lastIndexOf(':'));
                versions = apiConfigRepository.findVersionsByBaseCode(fallbackBase);
            }

            // 找到下一个最新的版本（排除自己）
            if (versions != null && !versions.isEmpty()) {
                for (ApiConfig v : versions) {
                    if (!v.getId().equals(id)) {
                        v.setLatestVersion(true);
                        // 如果该版本被废弃了，删除最新版本时一并恢复
                        if (v.getDeprecated() != null && v.getDeprecated()) {
                            v.setDeprecated(false);
                        }
                        apiConfigRepository.save(v);
                        break;
                    }
                }
            }
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

        // 补充版本信息
        List<ApiConfig> allVersions = apiConfigRepository.findByBaseCodeOrderByVersionDesc(entity.getBaseCode());
        dto.setVersionCount(allVersions.size());
        dto.setOtherVersions(allVersions.stream()
                .filter(v -> !v.getVersion().equals(entity.getVersion()))
                .map(v -> ApiConfigDetailDTO.VersionSummary.builder()
                        .id(v.getId())
                        .code(v.getCode())
                        .version(v.getVersion())
                        .latestVersion(v.getLatestVersion())
                        .deprecated(v.getDeprecated())
                        .status(v.getStatus())
                        .url(v.getUrl())
                        .build())
                .collect(Collectors.toList()));

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
     * 分页查询（支持版本筛选）
     * @param versionFilter "latest"=只看最新，"deprecated"=只看废弃，null/""=全部
     */
    public PageResult<ApiConfig> pageQuery(String keyword, Status status, String versionFilter, Integer page, Integer size) {
        Boolean latestVersion = null;
        Boolean deprecated = null;
        if ("latest".equals(versionFilter)) {
            latestVersion = true;
        } else if ("deprecated".equals(versionFilter)) {
            deprecated = true;
        }
        Page<ApiConfig> pageResult = apiConfigRepository.findByConditions(
                keyword, status, latestVersion, deprecated,
                PageRequest.of(page - 1, size));
        return PageResult.of(pageResult.getContent(), pageResult.getTotalElements(), page, size);
    }

    /**
     * 获取所有启用的接口
     */
    public List<ApiConfig> getAllActive() {
        return apiConfigRepository.findByStatusOrderByCreatedAtDesc(Status.ACTIVE);
    }

    /** 检查编码是否存在 */
    public boolean existsByCode(String code) {
        return apiConfigRepository.existsByCode(code);
    }

    /**
     * 根据ID获取实体（不走 DTO）
     */
    public ApiConfig getByIdForEntity(Long id) {
        return apiConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("接口配置不存在: " + id));
    }

    /**
     * 获取某 baseCode 的所有版本
     */
    public List<ApiConfig> getAllVersions(String baseCode) {
        return apiConfigRepository.findByBaseCodeOrderByVersionDesc(baseCode);
    }

    /**
     * 设置最新推荐版本（自动取消其他版本的 latestVersion）
     */
    @Transactional
    public void setLatestVersion(Long id) {
        ApiConfig entity = apiConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("接口配置不存在: " + id));

        List<ApiConfig> all = apiConfigRepository.findByBaseCodeOrderByVersionDesc(entity.getBaseCode());
        for (ApiConfig v : all) {
            if (v.getId().equals(id)) {
                v.setLatestVersion(true);
            } else {
                v.setLatestVersion(false);
            }
            apiConfigRepository.save(v);
        }
    }

    /**
     * 切换废弃状态
     */
    @Transactional
    public void toggleDeprecated(Long id) {
        ApiConfig entity = apiConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("接口配置不存在: " + id));
        entity.setDeprecated(entity.getDeprecated() == null ? true : !entity.getDeprecated());
        apiConfigRepository.save(entity);
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
     * @param userId 创建人ID
     * @param userName 创建人名称
     * @return 导入结果，包含解析后的配置信息
     */
    public ApiConfigDTO importFromCurl(String curl, Long userId, String userName) {
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
                .groupName(parsed.getGroupName())
                .enableDynamicToken(false)
                .enableTokenCache(null)
                .tokenCacheSeconds(null)
                .build();

        // 保存
        ApiConfig entity = toEntity(dto);
        entity.setCreatedById(userId);
        entity.setCreatedByName(userName);
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
                .enableRateLimit(dto.getEnableRateLimit())
                .rateLimitWindow(dto.getRateLimitWindow())
                .rateLimitMax(dto.getRateLimitMax())
                .tokenApiCode(dto.getTokenApiCode())
                .tokenExtractPath(dto.getTokenExtractPath())
                .tokenPosition(dto.getTokenPosition())
                .tokenParamName(dto.getTokenParamName())
                .tokenPrefix(dto.getTokenPrefix())
                .enableTokenCache(dto.getEnableTokenCache())
                .tokenCacheSeconds(dto.getTokenCacheSeconds())
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
        entity.setEnableTokenCache(dto.getEnableTokenCache());
        entity.setTokenCacheSeconds(dto.getTokenCacheSeconds());

        // 频率限制
        entity.setEnableRateLimit(dto.getEnableRateLimit());
        entity.setRateLimitWindow(dto.getRateLimitWindow());
        entity.setRateLimitMax(dto.getRateLimitMax());
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

    /**
     * 计算下一个版本号
     * v1 -> v2, v2 -> v3, v10 -> v11
     */
    public static String nextVersion(String currentVersion) {
        if (currentVersion == null || currentVersion.isEmpty()) return "v1";
        String v = currentVersion.trim().toLowerCase();
        // 匹配 v{n} 格式
        if (v.matches("^v\\d+$")) {
            int num = Integer.parseInt(v.substring(1));
            return "v" + (num + 1);
        }
        // 匹配 {n} 格式（如 1 -> 2）
        if (v.matches("^\\d+$")) {
            return String.valueOf(Integer.parseInt(v) + 1);
        }
        // 其他格式：追加 _v2
        return currentVersion + "_v2";
    }
}
