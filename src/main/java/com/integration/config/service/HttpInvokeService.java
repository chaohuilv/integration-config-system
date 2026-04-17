package com.integration.config.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.integration.config.config.IntegrationConfig;
import com.integration.config.dto.EnvironmentDTO;
import com.integration.config.dto.InvokeRequestDTO;
import com.integration.config.dto.InvokeResponseDTO;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.entity.log.InvokeLog;
import com.integration.config.enums.ContentType;
import com.integration.config.repository.log.InvokeLogRepository;
import com.integration.config.util.JsonPathUtil;
import com.integration.config.util.JsonUtil;
import com.integration.config.util.TraceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 接口调用服务
 * 核心服务：负责执行配置的接口调用
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HttpInvokeService {

    private final RestTemplate restTemplate;
    private final ApiConfigService apiConfigService;
    private final InvokeLogRepository invokeLogRepository;
    private final IntegrationConfig integrationConfig;
    private final TokenCacheManager tokenCacheManager;
    private final EnvironmentService environmentService;
    private final RedisCacheService redisCacheService;

    /**
     * 调用接口（通过编码）
     */
    public InvokeResponseDTO invoke(InvokeRequestDTO request) {
        String traceId = TraceUtil.generate();
        long startTime = System.currentTimeMillis();

        InvokeResponseDTO response;
        String requestUrl = null;  // 声明在外层，try 和 catch 都能访问
        InvokeLog logEntry = InvokeLog.builder()
                .apiCode(request.getApiCode())
                .clientIp(getClientIp())
                .traceId(traceId)
                .requestParams(JsonUtil.toJson(request.getParams()))
                .requestHeaders(JsonUtil.toJson(request.getHeaders()))
                .requestBody(request.getBody())
                .invokeTime(LocalDateTime.now())
                .build();

        try {
            // 1. 获取接口配置
            ApiConfig config = apiConfigService.getByCode(request.getApiCode());

            // 2. 检查缓存
            if (Boolean.TRUE.equals(config.getEnableCache())) {
                String cacheKey = buildCacheKey(request);
                Object cachedData = redisCacheService.get(cacheKey);
                if (cachedData != null) {
                    log.info("[{}] Redis 缓存命中: {}", traceId, cacheKey);
                    return InvokeResponseDTO.builder()
                            .success(true)
                            .statusCode(200)
                            .data(cachedData)
                            .message("来自缓存")
                            .costTime(System.currentTimeMillis() - startTime)
                            .invokeTime(LocalDateTime.now())
                            .traceId(traceId)
                            .fromCache(true)
                            .build();
                }
            }

            // 3. 处理动态Token
            String dynamicToken = null;
            if (Boolean.TRUE.equals(config.getEnableDynamicToken()) && config.getTokenApiCode() != null) {
                dynamicToken = obtainDynamicToken(config, traceId);
                if (dynamicToken == null) {
                    throw new RuntimeException("动态Token获取失败");
                }
            }

            // 4. 应用环境配置替换
            if (integrationConfig.isEnvironmentEnabled()) {
                config = applyEnvironmentUrl(config);
            }

            // 5. 预先构建完整请求URL（声明在外层，catch 块也能访问）
            requestUrl = buildUrl(config, request, dynamicToken, traceId);
            logEntry.setRequestUrl(requestUrl);

            // 6. 执行调用
            response = doInvoke(config, request, traceId, dynamicToken);

            // 7. 记录日志
            logEntry.setSuccess(response.getSuccess());
            logEntry.setResponseStatus(response.getStatusCode());
            logEntry.setRequestUrl(response.getRequestUrl()); // doInvoke 返回的 URL 覆盖（更准确）
            // HTML 响应直接存原始字符串，非 JSON 对象也直接存
            String respData = response.getData() != null ? response.getData().toString() : null;
            logEntry.setResponseData(truncate(respData, 10000));
            logEntry.setErrorMessage(response.getMessage());
            logEntry.setCostTime(response.getCostTime());

            // 6. 写入缓存
            if (Boolean.TRUE.equals(config.getEnableCache()) && response.getSuccess()) {
                String cacheKey = buildCacheKey(request);
                int ttl = config.getCacheTime() != null ? config.getCacheTime() : 300;
                redisCacheService.put(cacheKey, response.getData(), ttl);
            }

        } catch (Exception e) {
            log.error("[{}] 接口调用异常: {}", traceId, e.getMessage(), e);
            response = InvokeResponseDTO.builder()
                    .success(false)
                    .statusCode(500)
                    .message("调用失败: " + e.getMessage())
                    .costTime(System.currentTimeMillis() - startTime)
                    .invokeTime(LocalDateTime.now())
                    .traceId(traceId)
                    .build();

            logEntry.setSuccess(false);
            logEntry.setErrorMessage(e.getMessage());
            logEntry.setCostTime(response.getCostTime());
            logEntry.setRequestUrl(requestUrl); // URL 在 doInvoke 前已构建
        }

        // 7. 保存日志（非调试模式）
        if (!Boolean.TRUE.equals(request.getDebug())) {
            invokeLogRepository.save(logEntry);
        }

        TraceUtil.clear();
        return response;
    }

    /**
     * 获取动态Token
     */
    private String obtainDynamicToken(ApiConfig config, String traceId) {
        String apiCode = config.getCode();
        String tokenApiCode = config.getTokenApiCode();

        // 1. 先尝试从缓存获取
        String cachedToken = tokenCacheManager.getCachedToken(apiCode);
        if (cachedToken != null) {
            log.info("[{}] 使用缓存的动态Token", traceId);
            return cachedToken;
        }

        // 2. 调用Token接口获取新Token
        log.info("[{}] 调用Token接口获取动态Token: {}", traceId, tokenApiCode);

        // 构建Token日志记录
        InvokeLog tokenLog = InvokeLog.builder()
                .apiCode(tokenApiCode)
                .clientIp(getClientIp())
                .traceId(traceId)
                .requestParams(null)
                .requestHeaders(null)
                .requestBody(null)
                .invokeTime(LocalDateTime.now())
                .build();

        try {
            ApiConfig tokenConfig = apiConfigService.getByCode(tokenApiCode);

            // 应用环境配置替换
            if (integrationConfig.isEnvironmentEnabled()) {
                tokenConfig = applyEnvironmentUrl(tokenConfig);
            }

            // 预先构建Token请求URL用于日志
            String tokenRequestUrl = buildUrl(tokenConfig, InvokeRequestDTO.builder().apiCode(tokenApiCode).build(), null, traceId);
            tokenLog.setRequestUrl(tokenRequestUrl);

            // 构建Token请求（使用Token接口配置的参数）
            InvokeRequestDTO tokenRequest = InvokeRequestDTO.builder()
                    .apiCode(tokenApiCode)
                    .debug(false)
                    .build();

            // 直接调用Token接口
            InvokeResponseDTO tokenResponse = doInvoke(tokenConfig, tokenRequest, traceId, null);

            // 记录Token接口日志
            tokenLog.setSuccess(tokenResponse.getSuccess());
            tokenLog.setResponseStatus(tokenResponse.getStatusCode());
            tokenLog.setRequestUrl(tokenResponse.getRequestUrl());
            String tokenRespData = tokenResponse.getData() != null ? tokenResponse.getData().toString() : null;
            tokenLog.setResponseData(truncate(tokenRespData, 5000));
            tokenLog.setErrorMessage(tokenResponse.getMessage());
            tokenLog.setCostTime(tokenResponse.getCostTime());
            invokeLogRepository.save(tokenLog);

            if (!tokenResponse.getSuccess()) {
                log.error("[{}] Token接口调用失败: {}", traceId, tokenResponse.getMessage());
                return null;
            }

            // 3. 从响应中提取Token
            String responseJson = JsonUtil.toJson(tokenResponse.getData());
            String token = JsonPathUtil.extract(responseJson, config.getTokenExtractPath());

            if (token == null || token.isEmpty()) {
                log.error("[{}] 无法从响应中提取Token, path: {}", traceId, config.getTokenExtractPath());
                return null;
            }

            // 4. 缓存Token（使用Token接口配置的cacheTime，业务接口的tokenCacheTime作为兜底）
            int cacheTime = tokenConfig.getCacheTime() != null ? tokenConfig.getCacheTime() :
                            (config.getTokenCacheTime() != null ? config.getTokenCacheTime() : 0);
            tokenCacheManager.cacheToken(apiCode, token, cacheTime, tokenApiCode);

            log.info("[{}] 动态Token获取成功, 缓存时间: {}s", traceId, cacheTime);
            return token;

        } catch (Exception e) {
            log.error("[{}] 获取动态Token异常: {}", traceId, e.getMessage(), e);
            // 记录异常日志
            tokenLog.setSuccess(false);
            tokenLog.setResponseStatus(500);
            tokenLog.setErrorMessage(e.getMessage());
            tokenLog.setCostTime(0L);
            invokeLogRepository.save(tokenLog);
            return null;
        }
    }

    /**
     * 执行实际 HTTP 调用
     */
    private InvokeResponseDTO doInvoke(ApiConfig config, InvokeRequestDTO request, String traceId, String dynamicToken) {
        long startTime = System.currentTimeMillis();

        // 0. 构建 URL（用于日志记录，提前构建确保异常分支也能拿到）
        String url = buildUrl(config, request, dynamicToken, traceId);

        try {
            // 2. 构建请求头
            HttpHeaders headers = buildHeaders(config, request, dynamicToken, traceId);

            // 3. 构建请求体
            Object body = buildBody(config, request, dynamicToken, traceId);

            if (integrationConfig.isLogRequest()) {
                log.info("[{}] 请求: {} {}", traceId, config.getMethod(), url);
                log.info("[{}] 请求头: {}", traceId, headers);
                if (body != null) {
                    log.info("[{}] 请求体: {}", traceId, JsonUtil.format(JsonUtil.toJson(body)));
                }
            }

            // 4. 执行调用
            HttpEntity<?> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> responseEntity;

            switch (config.getMethod()) {
                case GET:
                    responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                    break;
                case POST:
                    responseEntity = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                    break;
                case PUT:
                    responseEntity = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
                    break;
                case DELETE:
                    responseEntity = restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
                    break;
                case PATCH:
                    responseEntity = restTemplate.exchange(url, HttpMethod.PATCH, entity, String.class);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的请求方法: " + config.getMethod());
            }

            long costTime = System.currentTimeMillis() - startTime;

            if (integrationConfig.isLogResponse()) {
                log.info("[{}] 响应: {} ({}ms)", traceId, responseEntity.getStatusCode(), costTime);
            }

            // 5. 解析响应
            Object responseData = parseResponse(responseEntity.getBody(), config);

            return InvokeResponseDTO.builder()
                    .success(true)
                    .statusCode(responseEntity.getStatusCode().value())
                    .data(responseData)
                    .message("调用成功")
                    .costTime(costTime)
                    .invokeTime(LocalDateTime.now())
                    .traceId(traceId)
                    .fromCache(false)
                    .requestUrl(url)
                    .build();

        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[{}] 调用异常: {}", traceId, e.getMessage());
            // 吞掉异常，始终返回带 URL 的响应对象
            return InvokeResponseDTO.builder()
                    .success(false)
                    .statusCode(500)
                    .message("调用失败: " + e.getMessage())
                    .costTime(costTime)
                    .invokeTime(LocalDateTime.now())
                    .traceId(traceId)
                    .fromCache(false)
                    .requestUrl(url)
                    .build();
        }
    }

    /**
     * 构建请求头
     */
    private HttpHeaders buildHeaders(ApiConfig config, InvokeRequestDTO request, String dynamicToken, String traceId) {
        HttpHeaders headers = new HttpHeaders();

        // Content-Type
        if (config.getContentType() != null) {
            headers.setContentType(MediaType.parseMediaType(config.getContentType().getValue()));
        } else {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        // 配置的请求头
        if (config.getHeaders() != null && JsonUtil.isValidJson(config.getHeaders())) {
            JSONObject jsonHeaders = JSONUtil.parseObj(config.getHeaders());
            jsonHeaders.forEach((key, value) -> headers.set(key, String.valueOf(value)));
        }

        // 动态请求头（覆盖配置）
        if (request.getHeaders() != null) {
            request.getHeaders().forEach(headers::set);
        }

        // 认证信息
        if (config.getAuthType() != null && !"none".equals(config.getAuthType())) {
            applyAuth(headers, config);
        }

        // 动态Token注入到Header
        if (dynamicToken != null && "header".equals(config.getTokenPosition())) {
            String tokenValue = (config.getTokenPrefix() != null ? config.getTokenPrefix() : "") + dynamicToken;
            String headerName = config.getTokenParamName() != null ? config.getTokenParamName() : "Authorization";
            headers.set(headerName, tokenValue);
            log.info("[{}] 动态Token已注入到Header: {}", traceId, headerName);
        }

        return headers;
    }

    /**
     * 应用认证
     * authInfo 存储的是纯 token 值（不带 "Bearer "/"Basic " 前缀）
     * 如果带前缀，则先去掉再重新拼接（保证格式统一）
     */
    private void applyAuth(HttpHeaders headers, ApiConfig config) {
        String authType = config.getAuthType() != null ? config.getAuthType().toLowerCase().trim() : "";
        String authInfo = config.getAuthInfo();

        if ("bearer".equals(authType)) {
            // 去掉已有的 "bearer " 前缀，只保留 token
            if (authInfo != null && authInfo.toLowerCase().startsWith("bearer ")) {
                authInfo = authInfo.substring(7).trim();
            }
            if (authInfo != null && !authInfo.isEmpty()) {
                headers.set("Authorization", "Bearer " + authInfo);
            }
        } else if ("basic".equals(authType)) {
            // Basic Auth 同样处理
            if (authInfo != null && authInfo.toLowerCase().startsWith("basic ")) {
                authInfo = authInfo.substring(6).trim();
            }
            if (authInfo != null && !authInfo.isEmpty()) {
                headers.set("Authorization", "Basic " + authInfo);
            }
        } else if ("api_key".equals(authType)) {
            if (authInfo != null && authInfo.contains(":")) {
                String[] parts = authInfo.split(":", 2);
                headers.set(parts[0].trim(), parts[1].trim());
            } else if (authInfo != null && !authInfo.isEmpty()) {
                headers.set("X-API-Key", authInfo);
            }
        }
    }

    /**
     * 构建请求体
     */
    private Object buildBody(ApiConfig config, InvokeRequestDTO request, String dynamicToken, String traceId) {
        // 如果直接传了 body 且要求跳过模板
        if (Boolean.TRUE.equals(request.getSkipTemplate()) && request.getBody() != null) {
            return request.getBody();
        }

        // 如果没有配置模板
        if (config.getRequestBody() == null || config.getRequestBody().isEmpty()) {
            String bodyStr = request.getBody();

            // 动态Token注入到Body
            if (dynamicToken != null && "body".equals(config.getTokenPosition()) && bodyStr != null) {
                bodyStr = injectTokenToBody(bodyStr, dynamicToken, config);
            }

            // 如果有动态参数但没有显式 body，则将 params 作为 body
            if (bodyStr == null && request.getParams() != null && !request.getParams().isEmpty()) {
                // 转为 JSON 字符串作为 body
                bodyStr = JsonUtil.toJson(request.getParams());
                if (integrationConfig.isLogRequest()) {
                    log.info("[{}] 使用 params 作为请求体: {}", traceId, bodyStr);
                }
            }

            return bodyStr;
        }

        // 有模板：模板 + 参数替换
        String bodyTemplate = config.getRequestBody();
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            for (Map.Entry<String, Object> entry : request.getParams().entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                if (bodyTemplate.contains(placeholder)) {
                    bodyTemplate = bodyTemplate.replace(placeholder, String.valueOf(entry.getValue()));
                }
            }
        }
        // 替换请求体中的参数
        if (request.getBody() != null && bodyTemplate.contains("{{body}}")) {
            bodyTemplate = bodyTemplate.replace("{{body}}", request.getBody());
        }
        // 动态Token注入到Body
        if (dynamicToken != null && "body".equals(config.getTokenPosition())) {
            bodyTemplate = injectTokenToBody(bodyTemplate, dynamicToken, config);
        }
        // 解析为对象或保持字符串
        if (JsonUtil.isValidJson(bodyTemplate)) {
            return JSONUtil.parse(bodyTemplate);
        }
        return bodyTemplate;
    }

    /**
     * 将Token注入到请求体
     */
    private String injectTokenToBody(String body, String token, ApiConfig config) {
        String paramName = config.getTokenParamName() != null ? config.getTokenParamName() : "access_token";
        String tokenValue = (config.getTokenPrefix() != null ? config.getTokenPrefix() : "") + token;

        try {
            if (JsonUtil.isValidJson(body)) {
                JSONObject json = JSONUtil.parseObj(body);
                json.set(paramName, tokenValue);
                return json.toString();
            }
        } catch (Exception e) {
            log.warn("注入Token到Body失败: {}", e.getMessage());
        }
        return body;
    }

    /**
     * 构建 URL（处理参数）
     */
    private String buildUrl(ApiConfig config, InvokeRequestDTO request, String dynamicToken, String traceId) {
        String url = config.getUrl();

        // 合并参数
        Map<String, Object> mergedParams = new HashMap<>();

        // 配置的静态参数
        if (config.getRequestParams() != null && JsonUtil.isValidJson(config.getRequestParams())) {
            JSONObject jsonParams = JSONUtil.parseObj(config.getRequestParams());
            jsonParams.forEach((key, value) -> mergedParams.put(key, value));
        }

        // 动态参数
        if (request.getParams() != null) {
            mergedParams.putAll(request.getParams());
        }

        // URL 参数替换 {{paramName}}
        for (Map.Entry<String, Object> entry : mergedParams.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            if (url.contains(placeholder)) {
                url = url.replace(placeholder, String.valueOf(entry.getValue()));
                mergedParams.remove(entry.getKey()); // 已替换的从参数中移除
            }
        }

        // 动态Token注入到URL
        if (dynamicToken != null && "url".equals(config.getTokenPosition())) {
            String paramName = config.getTokenParamName() != null ? config.getTokenParamName() : "access_token";
            mergedParams.put(paramName, (config.getTokenPrefix() != null ? config.getTokenPrefix() : "") + dynamicToken);
            log.info("[{}] 动态Token已注入到URL参数: {}", traceId, paramName);
        }

        // GET 请求将剩余参数加到 URL
        // 非 GET 请求：如果有剩余参数（如 Token），也附加到 URL
        if ((config.getMethod() == com.integration.config.enums.HttpMethod.GET || !mergedParams.isEmpty())
                && !mergedParams.isEmpty()) {
            StringBuilder paramStr = new StringBuilder();
            mergedParams.forEach((key, value) -> {
                if (paramStr.length() > 0) paramStr.append("&");
                try {
                    paramStr.append(key).append("=").append(java.net.URLEncoder.encode(String.valueOf(value), "UTF-8"));
                } catch (Exception e) {
                    paramStr.append(key).append("=").append(value);
                }
            });
            url += (url.contains("?") ? "&" : "?") + paramStr;
        }

        return url;
    }

    /**
     * 解析响应
     */
    private Object parseResponse(String body, ApiConfig config) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        // HTML 页面（认证失败、404等），直接返回原始字符串
        if (isHtmlResponse(body)) {
            return body;
        }
        if (JsonUtil.isValidJson(body)) {
            return JSONUtil.parse(body);
        }
        return body;
    }

    /**
     * 判断是否为 HTML 响应
     */
    private boolean isHtmlResponse(String body) {
        String trimmed = body.trim();
        return trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html")
                || trimmed.startsWith("<HTML") || trimmed.startsWith("<!doctype")
                || (trimmed.contains("<") && trimmed.contains(">") && trimmed.length() < 500
                    && (trimmed.contains("<head") || trimmed.contains("<title")
                        || trimmed.contains("<body") || trimmed.contains("<div")));
    }

    /**
     * 构建缓存 Key
     */
    private String buildCacheKey(InvokeRequestDTO request) {
        return request.getApiCode() + ":" + JsonUtil.toJson(request.getParams());
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...(truncated)";
    }

    /**
     * 应用环境配置替换 URL 域名
     * 根据接口的 groupName 查找该分组下已启用的环境配置，替换 URL 中的域名部分
     */
    private ApiConfig applyEnvironmentUrl(ApiConfig config) {
        String groupName = config.getGroupName();
        if (groupName == null || groupName.isEmpty()) {
            log.info("接口 {} 无分组，不应用环境配置", config.getCode());
            return config;
        }

        log.info("查找环境配置: groupName={}, 查找系统中启用的环境...", groupName);
        var envOpt = environmentService.getActiveEnvironment(groupName);
        if (envOpt.isEmpty()) {
            log.warn("分组 [{}] 无已启用的环境配置，使用原始URL: {}", groupName, config.getUrl());
            return config;
        }

        EnvironmentDTO env = envOpt.get();

        // 检查该环境是否启用了域名替换
        if (Boolean.FALSE.equals(env.getUrlReplace())) {
            log.info("分组 [{}] 的环境 [{}] 已禁用域名替换，使用原始URL", groupName, env.getEnvName());
            return config;
        }

        String originalUrl = config.getUrl();
        String baseUrl = env.getBaseUrl();

        // 替换域名：提取 originalUrl 的 path 部分，与 baseUrl 拼接
        String newUrl = replaceUrlDomain(originalUrl, baseUrl);
        log.info("环境替换: 分组={}, 环境={}, {} -> {}", groupName, env.getEnvName(), originalUrl, newUrl);

        // 返回新的 ApiConfig（克隆，避免修改原对象）
        return ApiConfig.builder()
                .id(config.getId())
                .name(config.getName())
                .code(config.getCode())
                .description(config.getDescription())
                .method(config.getMethod())
                .url(newUrl)
                .contentType(config.getContentType())
                .headers(config.getHeaders())
                .requestParams(config.getRequestParams())
                .requestBody(config.getRequestBody())
                .authType(config.getAuthType())
                .authInfo(config.getAuthInfo())
                .timeout(config.getTimeout())
                .retryCount(config.getRetryCount())
                .enableCache(config.getEnableCache())
                .cacheTime(config.getCacheTime())
                .status(config.getStatus())
                .groupName(config.getGroupName())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .createdById(config.getCreatedById())
                .createdByName(config.getCreatedByName())
                .updatedById(config.getUpdatedById())
                .updatedByName(config.getUpdatedByName())
                .enableDynamicToken(config.getEnableDynamicToken())
                .tokenApiCode(config.getTokenApiCode())
                .tokenExtractPath(config.getTokenExtractPath())
                .tokenPosition(config.getTokenPosition())
                .tokenParamName(config.getTokenParamName())
                .tokenPrefix(config.getTokenPrefix())
                .tokenCacheTime(config.getTokenCacheTime())
                .build();
    }

    /**
     * 替换 URL 的域名部分
     * 保留原始 URL 的 path、query、fragment，只替换 scheme://host:port 部分
     */
    private String replaceUrlDomain(String originalUrl, String newBaseUrl) {
        if (originalUrl == null || newBaseUrl == null) {
            return originalUrl;
        }

        // 确保baseUrl不带末尾斜杠
        String base = newBaseUrl.endsWith("/") ? newBaseUrl.substring(0, newBaseUrl.length() - 1) : newBaseUrl;

        try {
            java.net.URL baseParsed = new java.net.URL(base);

            String path;
            String query;
            String fragment;

            // 判断 originalUrl 是绝对 URL 还是相对路径
            if (originalUrl.matches("^https?://.*")) {
                // 绝对URL：提取 path/query/fragment
                java.net.URL original = new java.net.URL(originalUrl);
                path = original.getPath();
                query = original.getQuery();
                fragment = original.getRef();
            } else {
                // 相对路径：以 / 开头（如 /api/oauth/token?key=val）
                int queryIdx = originalUrl.indexOf('?');
                int fragIdx = originalUrl.indexOf('#');
                int splitIdx = -1;
                if (queryIdx != -1) splitIdx = queryIdx;
                if (fragIdx != -1 && (splitIdx == -1 || fragIdx < splitIdx)) splitIdx = fragIdx;

                if (splitIdx != -1) {
                    path = originalUrl.substring(0, splitIdx);
                    String rest = originalUrl.substring(splitIdx); // 包含 ? 或 #
                    if (rest.startsWith("?")) {
                        query = rest.substring(1);
                        fragment = null;
                        int fragInQuery = query.indexOf('#');
                        if (fragInQuery != -1) {
                            fragment = query.substring(fragInQuery + 1);
                            query = query.substring(0, fragInQuery);
                        }
                    } else {
                        query = null;
                        fragment = rest.substring(1);
                    }
                } else {
                    path = originalUrl;
                    query = null;
                    fragment = null;
                }
            }

            // 拼接：baseUrl + path + query + fragment
            StringBuilder sb = new StringBuilder();
            sb.append(baseParsed.getProtocol()).append("://")
              .append(baseParsed.getHost());
            if (baseParsed.getPort() != -1) {
                sb.append(":").append(baseParsed.getPort());
            }
            // baseUrl 自身的 path 部分（如 https://example.com/api）
            if (baseParsed.getPath() != null && !baseParsed.getPath().equals("/")) {
                sb.append(baseParsed.getPath());
            }
            if (path != null && !path.isEmpty()) {
                // 去重斜杠
                if (!path.startsWith("/")) {
                    sb.append("/");
                }
                sb.append(path);
            }
            if (query != null && !query.isEmpty()) {
                sb.append("?").append(query);
            }
            if (fragment != null && !fragment.isEmpty()) {
                sb.append("#").append(fragment);
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("URL 域名替换失败，尝试简单拼接: {}", e.getMessage());
            // 降级：baseUrl + originalUrl（保证至少返回绝对URL）
            if (originalUrl.startsWith("/")) {
                return base + originalUrl;
            }
            return base + "/" + originalUrl;
        }
    }

    /**
     * 获取真实客户端IP（支持代理/负载均衡）
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return getLocalIp();
            }
            HttpServletRequest request = attrs.getRequest();

            // 1. 优先从 Nginx 反向代理 Header 获取
            String ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            // 2. 代理链（多级代理时取第一个）
            ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // 多个IP时取第一个（格式: client, proxy1, proxy2）
                int idx = ip.indexOf(',');
                if (idx > 0) {
                    return ip.substring(0, idx).trim();
                }
                return ip.trim();
            }

            // 3. 阿里云 SLB
            ip = request.getHeader("Ali-Cdn-Real-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            // 4. 腾讯云 CLB
            ip = request.getHeader("X-Custom-Real-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            // 5. 直接连接（REMOTE_ADDR）
            ip = request.getRemoteAddr();
            if (ip != null && !ip.isEmpty()) {
                // 本地回环地址统一为 localhost
                if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1")) {
                    return "localhost";
                }
                return ip;
            }

            return getLocalIp();
        } catch (Exception e) {
            log.warn("获取客户端IP异常: {}", e.getMessage());
            return getLocalIp();
        }
    }

    /**
     * 获取本机IP
     */
    private String getLocalIp() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
