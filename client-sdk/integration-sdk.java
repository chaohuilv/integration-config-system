package com.integration.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 统一接口调用 SDK
 * 业务系统集成此 SDK，通过接口编码调用已配置的外部接口
 *
 * 使用方式：
 * IntegrationClient client = new IntegrationClient("http://localhost:8080");
 * client.setApiKey("your-api-key"); // 可选，设置调用凭证
 *
 * // 调用接口
 * String result = client.invoke("user-get", Map.of("id", 1));
 *
 * // 调用接口（POST带请求体）
 * String result = client.invoke("user-create", Map.of(), "{\"name\":\"张三\"}");
 */
public class IntegrationClient {

    private final String baseUrl;
    private String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public IntegrationClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 设置 API Key（可选）
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 调用接口（GET）
     */
    public String invoke(String apiCode) {
        return invoke(apiCode, null, null, "GET");
    }

    /**
     * 调用接口（带参数）
     */
    public String invoke(String apiCode, Map<String, Object> params) {
        return invoke(apiCode, params, null, "GET");
    }

    /**
     * 调用接口（POST 带请求体）
     */
    public String invoke(String apiCode, Map<String, Object> params, String body) {
        return invoke(apiCode, params, body, "POST");
    }

    /**
     * 调用接口（指定方法）
     */
    public String invoke(String apiCode, Map<String, Object> params, String body, String method) {
        try {
            // 构建请求
            InvokeRequest request = new InvokeRequest();
            request.setApiCode(apiCode);
            request.setParams(params);
            request.setBody(body);

            String jsonBody = objectMapper.writeValueAsString(request);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/invoke"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30));

            if (apiKey != null) {
                requestBuilder.header("X-Api-Key", apiKey);
            }

            if ("GET".equalsIgnoreCase(method)) {
                requestBuilder.GET();
            } else {
                requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                InvokeResponse resp = objectMapper.readValue(response.body(), InvokeResponse.class);
                if (Boolean.TRUE.equals(resp.getSuccess())) {
                    return objectMapper.writeValueAsString(resp.getData());
                } else {
                    throw new IntegrationException("调用失败: " + resp.getMessage());
                }
            } else {
                throw new IntegrationException("HTTP错误: " + response.statusCode());
            }

        } catch (IntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new IntegrationException("SDK调用异常", e);
        }
    }

    /**
     * 异步调用接口
     */
    public java.util.concurrent.CompletableFuture<String> invokeAsync(String apiCode, Map<String, Object> params, String body) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> invoke(apiCode, params, body));
    }

    /**
     * 批量调用（串行）
     */
    public java.util.List<String> batchInvoke(java.util.List<BatchInvokeItem> items) {
        return items.stream().map(item -> {
            if (item.getBody() != null) {
                return invoke(item.getApiCode(), item.getParams(), item.getBody(), item.getMethod());
            } else {
                return invoke(item.getApiCode(), item.getParams());
            }
        }).toList();
    }

    // ==================== 内部类 ====================

    public static class InvokeRequest {
        private String apiCode;
        private Map<String, Object> params;
        private String body;
        private Boolean skipTemplate;
        private String source;

        public String getApiCode() { return apiCode; }
        public void setApiCode(String apiCode) { this.apiCode = apiCode; }
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public Boolean getSkipTemplate() { return skipTemplate; }
        public void setSkipTemplate(Boolean skipTemplate) { this.skipTemplate = skipTemplate; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public static class InvokeResponse {
        private Boolean success;
        private Integer statusCode;
        private Object data;
        private String message;
        private Long costTime;

        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public Integer getStatusCode() { return statusCode; }
        public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Long getCostTime() { return costTime; }
        public void setCostTime(Long costTime) { this.costTime = costTime; }
    }

    public static class BatchInvokeItem {
        private String apiCode;
        private Map<String, Object> params;
        private String body;
        private String method = "POST";

        public String getApiCode() { return apiCode; }
        public void setApiCode(String apiCode) { this.apiCode = apiCode; }
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
    }

    public static class IntegrationException extends RuntimeException {
        public IntegrationException(String message) { super(message); }
        public IntegrationException(String message, Throwable cause) { super(message, cause); }
    }
}
