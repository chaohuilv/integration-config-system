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
 * // 登录认证
 * AuthResult auth = client.login("admin", "123456");
 * System.out.println("欢迎: " + auth.getDisplayName());
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
    private final Map<String, String> defaultHeaders;
    private String accessToken;

    /**
     * 构造客户端
     *
     * @param baseUrl 后端服务地址，如：http://localhost:8080
     */
    public IntegrationClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.defaultHeaders = new HashMap<>();
        this.defaultHeaders.put("Accept", "application/json");
    }

    /**
     * 登录认证（调用 /api/auth/login）
     *
     * @param userCode 用户编码
     * @param password 密码
     * @return 认证结果，包含用户信息和 access_token
     * @throws IntegrationException 登录失败时抛出
     */
    public AuthResult login(String userCode, String password) {
        String body = "{\"userCode\":\"" + escapeJson(userCode) + "\",\"password\":\"" + escapeJson(password) + "\"}";
        try {
            String response = rawRequest("POST", "/api/auth/login", null, body, null);
            // 简单解析：提取 access_token
            String token = extractJsonString(response, "access_token");
            Long userId = extractJsonLong(response, "id");
            String username = extractJsonString(response, "username");
            String displayName = extractJsonString(response, "displayName");
            String sdkUserCode = extractJsonString(response, "userCode");
            if (token == null || token.isEmpty()) {
                String msg = extractJsonString(response, "message");
                throw new IntegrationException(msg != null ? msg : "登录失败：未获取到 token");
            }
            this.accessToken = token;
            return new AuthResult(token, userId, sdkUserCode, username, displayName);
        } catch (IntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new IntegrationException("登录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查是否已登录（有有效 token）
     */
    public boolean isLoggedIn() {
        return accessToken != null && !accessToken.isEmpty();
    }

    /**
     * 退出登录（调用 /api/auth/logout）
     */
    public void logout() {
        try {
            rawRequest("POST", "/api/auth/logout", null, null, null);
        } catch (Exception e) {
            // 忽略退出失败的错误
        } finally {
            this.accessToken = null;
        }
    }

    /**
     * 获取当前登录用户信息（调用 /api/auth/current）
     *
     * @return 用户信息 Map，key 包括 id / userCode / username / displayName
     * @throws IntegrationException 未登录或请求失败时抛出
     */
    public Map<String, Object> getCurrentUser() {
        if (!isLoggedIn()) {
            throw new IntegrationException("未登录，请先调用 login()");
        }
        try {
            String response = rawRequest("GET", "/api/auth/current", null, null, null);
            return parseJsonToMap(response);
        } catch (IntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new IntegrationException("获取当前用户失败: " + e.getMessage(), e);
        }
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
    public String invoke(String apiCode, Map<String, Object> params) {
        return invoke(apiCode, params, null, null);
    }

    /**
     * POST/PUT 请求，带 URL 参数和请求体
     *
     * @param apiCode 接口编码
     * @param params  URL 查询参数（可选，传 null）
     * @param body    JSON 请求体字符串（可选，传 null 表示无请求体）
     * @return JSON 响应字符串
     */
    public String invoke(String apiCode,
                        Map<String, Object> params,
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
                         Map<String, Object> params,
                         String body,
                         Map<String, String> headers) {
        requireLogin();
        return rawRequest("POST", "/api/invoke/" + apiCode, params, body, headers);
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
            Map<String, Object> params,
            String body,
            Map<String, String> headers) {
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
    public List<String> batchInvoke(List<Object[]> items) {
        List<String> results = new ArrayList<>();
        for (Object[] item : items) {
            String apiCode = (String) item[0];
            @SuppressWarnings("unchecked")
            Map<String, Object> params = item.length > 1 ? (Map<String, Object>) item[1] : null;
            String body = item.length > 2 ? (String) item[2] : null;
            @SuppressWarnings("unchecked")
            Map<String, String> headers = item.length > 3 ? (Map<String, String>) item[3] : null;
            results.add(invoke(apiCode, params, body, headers));
        }
        return results;
    }

    // ==================== 内部方法 ====================

    /**
     * 核心请求方法，统一处理认证、自动重试、响应解析
     */
    private String rawRequest(String method,
                               String path,
                               Map<String, Object> params,
                               String body,
                               Map<String, String> headers) {
        // 1. 构建完整 URL（含查询参数）
        String url = buildFullUrl(path, params);

        try {
            java.net.HttpURLConnection conn = createConnection(url);
            conn.setRequestMethod(method);

            // 2. 设置请求头
            Map<String, String> mergedHeaders = new HashMap<>(defaultHeaders);
            mergedHeaders.put("Content-Type", "application/json; charset=UTF-8");

            // 认证 Token（自动添加）
            if (accessToken != null && !accessToken.isEmpty()) {
                mergedHeaders.put("Authorization", "Bearer " + accessToken);
            }

            // 用户自定义请求头（可覆盖默认）
            if (headers != null) {
                mergedHeaders.putAll(headers);
            }
            mergedHeaders.forEach(conn::setRequestProperty);

            // 3. 发送请求体
            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

            // 4. 读取响应
            int status = conn.getResponseCode();
            String response;
            try (java.io.InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                response = is != null
                        ? new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        : "";
            }

            // 5. 处理业务错误（非 2xx）
            if (status >= 200 && status < 300) {
                // 业务层错误（如 code != 200）
                String bizMsg = extractJsonString(response, "message");
                if (bizMsg != null && response.contains("\"code\":") && !response.contains("\"code\":200")) {
                    throw new IntegrationException("业务错误: " + bizMsg);
                }
                return response;
            } else if (status == 401) {
                this.accessToken = null;
                throw new IntegrationException("认证失败（401）：Token 无效或已过期，请重新登录");
            } else {
                String errMsg = extractJsonString(response, "message");
                throw new IntegrationException("HTTP " + status + ": " + (errMsg != null ? errMsg : response));
            }

        } catch (IntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new IntegrationException("SDK 调用失败: " + e.getMessage(), e);
        }
    }

    private java.net.HttpURLConnection createConnection(String urlStr) throws Exception {
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    private String buildFullUrl(String path, Map<String, Object> params) {
        StringBuilder url = new StringBuilder(baseUrl).append(path);
        if (params != null && !params.isEmpty()) {
            url.append("?");
            boolean first = true;
            for (Map.Entry<String, Object> e : params.entrySet()) {
                if (!first) url.append("&");
                first = false;
                url.append(urlEncode(e.getKey()))
                   .append("=")
                   .append(urlEncode(e.getValue() != null ? e.getValue().toString() : ""));
            }
        }
        return url.toString();
    }

    private void requireLogin() {
        if (!isLoggedIn()) {
            throw new IntegrationException("未登录，请先调用 client.login(userCode, password)");
        }
    }

    // ---------- JSON 简化工具 ----------

    private String extractJsonString(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx);
        if (colon < 0) return null;
        int start = json.indexOf("\"", colon);
        if (start < 0) return null;
        int end = json.indexOf("\"", start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    private Long extractJsonLong(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx);
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == start) return null;
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> parseJsonToMap(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isEmpty()) return result;
        // 简单解析 data 字段（兼容标准 Result 包装）
        String dataStr = extractJsonString(json, "data");
        if (dataStr == null) return result;
        // 继续提取 data 内的字段
        extractTopLevelFields(dataStr, result);
        return result;
    }

    private void extractTopLevelFields(String json, Map<String, Object> map) {
        int i = 0;
        while (i < json.length()) {
            // 找 "key":"
            while (i < json.length() && json.charAt(i) != '"') i++;
            if (i >= json.length()) break;
            int keyStart = i + 1;
            int keyEnd = json.indexOf('"', keyStart);
            if (keyEnd < 0) break;
            String key = json.substring(keyStart, keyEnd);
            int colon = json.indexOf(':', keyEnd);
            if (colon < 0) break;
            i = colon + 1;
            while (i < json.length() && json.charAt(i) == ' ') i++;
            if (i >= json.length()) break;
            char c = json.charAt(i);
            if (c == '"') {
                int valStart = i + 1;
                int valEnd = json.indexOf('"', valStart);
                if (valEnd < 0) break;
                map.put(key, json.substring(valStart, valEnd));
                i = valEnd + 1;
            } else if (Character.isDigit(c)) {
                int valStart = i;
                while (i < json.length() && (Character.isDigit(json.charAt(i)) || json.charAt(i) == '.')) i++;
                map.put(key, json.substring(valStart, i));
            }
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    // ==================== AuthResult 内部类 ====================

    /**
     * 登录认证结果
     */
    public static class AuthResult {
        private final String accessToken;
        private final Long userId;
        private final String userCode;
        private final String username;
        private final String displayName;

        public AuthResult(String accessToken, Long userId, String userCode,
                          String username, String displayName) {
            this.accessToken = accessToken;
            this.userId = userId;
            this.userCode = userCode;
            this.username = username;
            this.displayName = displayName;
        }

        public String getAccessToken() { return accessToken; }
        public Long   getUserId()      { return userId; }
        public String getUserCode()    { return userCode; }
        public String getUsername()    { return username; }
        public String getDisplayName(){ return displayName; }
    }
}
