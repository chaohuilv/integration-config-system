package com.integration.sdk;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * 集成配置系统 SDK 客户端
 *
 * <p>使用示例：
 * <pre>{@code
 * IntegrationClient client = new IntegrationClient("http://localhost:8080");
 *
 * // GET 请求
 * String result = client.invoke("user-list");
 *
 * // GET 请求（带 URL 参数）
 * String result = client.invoke("user-get", Map.of("id", 1001));
 *
 * // POST 请求（带参数和请求体）
 * String result = client.invoke("user-create",
 *     Map.of("deptId", 10),
 *     "{\"name\":\"张三\",\"age\":25}"
 * );
 *
 * // 带自定义请求头
 * Map<String, String> headers = new HashMap<>();
 * headers.put("X-Request-Id", "12345");
 * String result = client.invoke("user-create",
 *     Map.of(), "{\"name\":\"张三\"}", headers
 * );
 * }</pre>
 */
public class IntegrationClient {

    private final String baseUrl;
    private final java.util.Map<String, String> defaultHeaders;

    /**
     * 构造客户端
     *
     * @param baseUrl 后端服务地址，如：http://localhost:8080
     */
    public IntegrationClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.defaultHeaders = new java.util.HashMap<>();
    }

    /**
     * 添加默认请求头，所有请求都会带上
     */
    public void addDefaultHeader(String key, String value) {
        this.defaultHeaders.put(key, value);
    }

    /**
     * GET 请求，无参数
     *
     * @param apiCode 接口编码
     * @return JSON 响应字符串
     */
    public String invoke(String apiCode) {
        return invoke(apiCode, null, null, null);
    }

    /**
     * GET 请求，带 URL 参数
     *
     * @param apiCode 接口编码
     * @param params  URL 查询参数
     * @return JSON 响应字符串
     */
    public String invoke(String apiCode, java.util.Map<String, Object> params) {
        return invoke(apiCode, params, null, null);
    }

    /**
     * POST/PUT 请求，带 URL 参数和请求体
     *
     * @param apiCode 接口编码
     * @param params  URL 查询参数（可选）
     * @param body    JSON 请求体字符串（可选）
     * @return JSON 响应字符串
     */
    public String invoke(String apiCode,
                        java.util.Map<String, Object> params,
                        String body) {
        return invoke(apiCode, params, body, null);
    }

    /**
     * 完整调用，支持自定义请求头（覆盖配置的同名请求头）
     *
     * @param apiCode 接口编码
     * @param params  URL 查询参数（可选，传 null）
     * @param body    JSON 请求体字符串（可选，传 null 表示无请求体）
     * @param headers 自定义请求头（可选，传 null）
     * @return JSON 响应字符串
     * @throws IntegrationException 调用失败时抛出
     */
    public String invoke(String apiCode,
                         java.util.Map<String, Object> params,
                         String body,
                         java.util.Map<String, String> headers) {

        String url = baseUrl + "/api/invoke/" + apiCode;

        try {
            java.net.HttpURLConnection conn = createConnection(url);

            // 合并请求头
            java.util.Map<String, String> mergedHeaders = new HashMap<>(defaultHeaders);
            mergedHeaders.put("Content-Type", "application/json; charset=UTF-8");
            if (headers != null) {
                mergedHeaders.putAll(headers);
            }
            mergedHeaders.forEach(conn::setRequestProperty);

            // 发送请求体
            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

            // 读取响应
            int status = conn.getResponseCode();
            String response;
            try (java.io.InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                response = is != null
                        ? new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        : "";
            }

            if (status >= 200 && status < 300) {
                return response;
            } else {
                throw new IntegrationException("HTTP " + status + ": " + response);
            }

        } catch (IntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new IntegrationException("SDK 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步调用
     *
     * @param apiCode 接口编码
     * @param params  URL 参数
     * @param body    请求体
     * @param headers 请求头
     * @return CompletableFuture
     */
    public java.util.concurrent.CompletableFuture<String> invokeAsync(
            String apiCode,
            java.util.Map<String, Object> params,
            String body,
            java.util.Map<String, String> headers) {
        return java.util.concurrent.CompletableFuture.supplyAsync(
                () -> invoke(apiCode, params, body, headers)
        );
    }

    /**
     * 批量调用（顺序执行）
     *
     * @param items 批量调用项，每项格式：new Object[]{apiCode, params, body, headers}
     * @return 调用结果列表
     */
    public java.util.List<String> batchInvoke(java.util.List<Object[]> items) {
        java.util.List<String> results = new java.util.ArrayList<>();
        for (Object[] item : items) {
            String apiCode = (String) item[0];
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> params = item.length > 1 ? (java.util.Map<String, Object>) item[1] : null;
            String body = item.length > 2 ? (String) item[2] : null;
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> headers = item.length > 3 ? (java.util.Map<String, String>) item[3] : null;
            results.add(invoke(apiCode, params, body, headers));
        }
        return results;
    }

    private java.net.HttpURLConnection createConnection(String urlStr) throws Exception {
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }
}
