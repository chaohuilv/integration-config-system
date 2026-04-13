package com.integration.config.util;

import com.integration.config.dto.CurlImportDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class CurlParser {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "['\"](https?://[^'\"]*?)['\"]|https?://\\S+");
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "-H\\s*['\"]([^'\"]*?)['\"]|--header\\s*=\\s*['\"]([^'\"]*?)['\"]");
    private static final Pattern DATA_PATTERN = Pattern.compile(
            "-d\\s*['\"]([^'\"]*?)['\"]|--data(?:[-_]raw)?\\s*=\\s*['\"]([^'\"]*?)['\"]");
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "-X\\s+(\\w+)|--request\\s*=\\s*(\\w+)");

    public static CurlImportDTO parse(String curl) {
        if (curl == null || curl.trim().isEmpty()) {
            return CurlImportDTO.error("curl command cannot be empty");
        }
        try {
            String cmd = preprocess(curl);
            String url = extractUrl(cmd);
            if (url == null || url.isEmpty()) {
                return CurlImportDTO.error("URL not found");
            }
            String method = extractMethod(cmd);
            if (method == null) method = "GET";
            Map<String, String> headers = extractHeaders(cmd);
            String body = extractBody(cmd);
            cleanHeaders(headers);
            AuthInfo auth = detectAuth(headers);

            // 从 URL 中分离出 query params，url 本身去掉 query string
            String cleanUrl = url;
            String requestParamsJson = null;
            if (url.contains("?")) {
                int qi = url.indexOf("?");
                cleanUrl = url.substring(0, qi);
                String queryString = url.substring(qi + 1);
                requestParamsJson = parseQueryStringToJson(queryString);
            }

            String code = generateCode(cleanUrl);
            String name = generateName(cleanUrl);
            String headersJson = buildHeadersJson(headers, auth.paramName);
            String groupName = extractGroupName(cleanUrl);
            return CurlImportDTO.builder()
                    .success(true)
                    .name(name)
                    .code(code)
                    .url(cleanUrl)
                    .method(method)
                    .headers(headers)
                    .body(body)
                    .requestParams(requestParamsJson)
                    .authType(auth.type)
                    .authParamName(auth.paramName)
                    .authValue(auth.value)
                    .groupName(groupName)
                    .build();
        } catch (Exception e) {
            log.error("curl parse failed: {}", e.getMessage(), e);
            return CurlImportDTO.error("parse failed: " + e.getMessage());
        }
    }

    private static String preprocess(String curl) {
        String cmd = curl.trim();
        cmd = cmd.replaceFirst("(?i)^\\s*curl\\s+", "");
        cmd = cmd.replaceAll("(?<!\\\\)\\\\\\s*", " ");
        cmd = cmd.replaceAll("\\s+", " ").trim();
        return cmd;
    }

    private static String extractUrl(String cmd) {
        Matcher m = Pattern.compile("['\"](https?://[^'\"]*?)['\"]").matcher(cmd);
        while (m.find()) {
            String url = m.group(1);
            if (url != null && !url.isEmpty() && isValidUrl(url)) {
                return url;
            }
        }
        Matcher um = Pattern.compile("(https?://\\S+)").matcher(cmd);
        while (um.find()) {
            String url = um.group(1);
            url = url.replaceAll("['\">]+$", "");
            if (isValidUrl(url)) {
                return url;
            }
        }
        return null;
    }

    private static boolean isValidUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static String extractMethod(String cmd) {
        Matcher m = METHOD_PATTERN.matcher(cmd);
        while (m.find()) {
            String method = m.group(1) != null ? m.group(1) : m.group(2);
            if (method != null) return method.toUpperCase();
        }
        if (Pattern.compile("(?i)-d\\s| --data| --data-raw").matcher(cmd).find()) {
            return "POST";
        }
        return null;
    }

    private static Map<String, String> extractHeaders(String cmd) {
        Map<String, String> headers = new HashMap<>();
        Matcher m = HEADER_PATTERN.matcher(cmd);
        while (m.find()) {
            String header = m.group(1) != null ? m.group(1) : m.group(2);
            if (header != null && !header.trim().isEmpty()) {
                header = header.trim();
                int colonIdx = header.indexOf(':');
                if (colonIdx > 0) {
                    String key = header.substring(0, colonIdx).trim();
                    String value = trimQuotes(header.substring(colonIdx + 1).trim());
                    headers.put(key, value);
                } else {
                    headers.put(header, "");
                }
            }
        }
        return headers;
    }

    private static String extractBody(String cmd) {
        Matcher m = DATA_PATTERN.matcher(cmd);
        while (m.find()) {
            String body = m.group(1) != null ? m.group(1) : m.group(2);
            if (body != null && !body.trim().isEmpty()) {
                return trimQuotes(body.trim());
            }
        }
        return null;
    }

    private static void cleanHeaders(Map<String, String> headers) {
        String[] systemKeys = {"Host", "Content-Length", "Connection", "Keep-Alive",
                "Accept-Encoding", "Origin"};
        for (String key : systemKeys) {
            headers.remove(key);
        }
        headers.keySet().removeIf(k -> k.startsWith("Sec-Fetch"));
    }

    private static AuthInfo detectAuth(Map<String, String> headers) {
        String auth = headers.get("Authorization");
        if (auth != null && !auth.isEmpty()) {
            if (auth.toLowerCase().startsWith("bearer ")) {
                return new AuthInfo("BEARER", "Authorization", auth);
            } else if (auth.toLowerCase().startsWith("basic ")) {
                return new AuthInfo("BASIC", "Authorization", auth);
            } else {
                return new AuthInfo("API_KEY", "Authorization", auth);
            }
        }
        String[] apiKeyHeaders = {"X-API-Key", "Api-Key", "apikey", "X-Api-Key"};
        for (String key : apiKeyHeaders) {
            String v = headers.get(key);
            if (v != null && !v.isEmpty()) {
                return new AuthInfo("API_KEY", key, v);
            }
        }
        return new AuthInfo(null, null, null);
    }

    private static String generateCode(String url) {
        try {
            String path = url;
            int qi = path.indexOf('?');
            if (qi >= 0) path = path.substring(0, qi);
            int di = path.indexOf("://");
            if (di >= 0) {
                int pi = path.indexOf("/", di + 3);
                path = pi >= 0 ? path.substring(pi + 1) : "";
            }
            path = path.replaceAll("\\.[a-zA-Z]+$", "");
            path = path.replaceAll("\\{[^}]+\\}", "-");
            path = path.replaceAll("/+", "-");
            path = path.replaceAll("[^a-zA-Z0-9/-]", "-");
            path = path.replaceAll("-+", "-");
            path = path.replaceAll("^-|-$", "");
            path = path.toLowerCase();
            if (path.isEmpty()) {
                return "api-" + UUID.randomUUID().toString().substring(0, 8);
            }
            return path;
        } catch (Exception e) {
            return "api-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private static String generateName(String url) {
        try {
            String path = url;
            int di = path.indexOf("://");
            if (di >= 0) {
                int pi = path.indexOf("/", di + 3);
                path = pi >= 0 ? path.substring(pi + 1) : "";
            }
            String[] parts = path.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                String part = parts[i];
                if (part.isEmpty() || part.matches("\\d+") || part.matches("-+")) continue;
                part = part.replaceAll("[^a-zA-Z0-9]", " ").trim();
                if (!part.isEmpty()) {
                    return part.substring(0, 1).toUpperCase() + part.substring(1);
                }
            }
            int di2 = url.indexOf("://") + 3;
            int ei = url.indexOf("/", di2);
            String host = ei > di2 ? url.substring(di2, ei) : url.substring(di2);
            return host.substring(0, 1).toUpperCase() + host.substring(1);
        } catch (Exception e) {
            return "API";
        }
    }

    private static String extractGroupName(String url) {
        try {
            String path = url;
            int di = path.indexOf("://");
            if (di >= 0) {
                int pi = path.indexOf("/", di + 3);
                path = pi >= 0 ? path.substring(pi + 1) : "";
            }
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
            return "Default";
        } catch (Exception e) {
            return "Default";
        }
    }

    private static String buildHeadersJson(Map<String, String> headers, String authParamName) {
        if (headers == null || headers.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (authParamName != null && e.getKey().equalsIgnoreCase(authParamName)) continue;
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            if (!first) sb.append("\n");
            sb.append(e.getKey()).append(": ").append(e.getValue());
            first = false;
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String trimQuotes(String s) {
        if (s == null || s.length() < 2) return s;
        boolean isQuoted = (s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'"));
        if (isQuoted) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * 将 query string 解析为 JSON 字符串
     * 例如：pageIndex=1&pageSize=20  ->  {"pageIndex":"1","pageSize":"20"}
     */
    private static String parseQueryStringToJson(String queryString) {
        if (queryString == null || queryString.isEmpty()) return null;
        try {
            String[] pairs = queryString.split("&");
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (String pair : pairs) {
                int eq = pair.indexOf("=");
                if (eq <= 0) continue;
                String key = pair.substring(0, eq);
                String value = eq < pair.length() ? pair.substring(eq + 1) : "";
                // URL decode
                value = java.net.URLDecoder.decode(value, "UTF-8");
                if (!first) json.append(",");
                json.append("\"").append(escapeJson(key)).append("\":\"").append(escapeJson(value)).append("\"");
                first = false;
            }
            json.append("}");
            return json.toString().equals("{}") ? null : json.toString();
        } catch (Exception e) {
            log.warn("query string 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static class AuthInfo {
        String type;
        String paramName;
        String value;
        AuthInfo(String type, String paramName, String value) {
            this.type = type;
            this.paramName = paramName;
            this.value = value;
        }
    }
}
