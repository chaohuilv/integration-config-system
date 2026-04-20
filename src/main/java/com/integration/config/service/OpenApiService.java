package com.integration.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.integration.config.dto.ApiConfigDTO;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.enums.ContentType;
import com.integration.config.enums.HttpMethod;
import com.integration.config.enums.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenAPI / Swagger 导入导出服务
 *
 * 支持：
 *   导入 - OpenAPI 3.0 JSON / Swagger 2.0 JSON
 *   导出 - OpenAPI 3.0 JSON
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenApiService {

    private final ObjectMapper objectMapper;
    private final ApiConfigService apiConfigService;

    // ==================== 导入 ====================

    /**
     * 解析 OpenAPI/Swagger JSON 字符串，返回预览列表（不入库）
     */
    public List<ApiConfigDTO> parseOpenApi(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        // 判断版本
        String version = "";
        if (root.has("openapi")) {
            version = root.get("openapi").asText();
        } else if (root.has("swagger")) {
            version = root.get("swagger").asText();
        }

        if (version.startsWith("3.")) {
            return parseOpenApi3(root);
        } else if (version.startsWith("2.")) {
            return parseSwagger2(root);
        } else {
            throw new IllegalArgumentException("不支持的格式，请提供 OpenAPI 3.0 或 Swagger 2.0 JSON");
        }
    }

    /**
     * 解析 OpenAPI 3.0
     */
    private List<ApiConfigDTO> parseOpenApi3(JsonNode root) {
        List<ApiConfigDTO> result = new ArrayList<>();

        // 提取 servers[0].url 作为 baseUrl
        String baseUrl = "";
        JsonNode servers = root.path("servers");
        if (servers.isArray() && servers.size() > 0) {
            baseUrl = servers.get(0).path("url").asText("");
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        JsonNode paths = root.path("paths");
        if (paths.isMissingNode()) return result;

        final String finalBaseUrl = baseUrl;
        paths.fields().forEachRemaining(pathEntry -> {
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();

            for (String method : Arrays.asList("get", "post", "put", "delete", "patch", "head", "options")) {
                JsonNode op = pathItem.path(method);
                if (op.isMissingNode()) continue;

                try {
                    ApiConfigDTO dto = buildDtoFromOp3(op, method.toUpperCase(), finalBaseUrl + path, path);
                    result.add(dto);
                } catch (Exception e) {
                    log.warn("解析路径 {} {} 失败: {}", method, path, e.getMessage());
                }
            }
        });

        return result;
    }

    private ApiConfigDTO buildDtoFromOp3(JsonNode op, String method, String fullUrl, String path) {
        String summary = op.path("summary").asText("");
        String description = op.path("description").asText("");
        String operationId = op.path("operationId").asText("");

        // 名称：summary > operationId > path 末段
        String name = !summary.isEmpty() ? summary
                : !operationId.isEmpty() ? operationId
                : extractLastSegment(path);

        // 编码：operationId > path 转换
        String code = !operationId.isEmpty() ? sanitizeCode(operationId) : pathToCode(path, method);

        // 分组：tags[0]
        String groupName = "";
        JsonNode tags = op.path("tags");
        if (tags.isArray() && tags.size() > 0) {
            groupName = tags.get(0).asText("");
        }

        // 请求参数（query params）
        String requestParams = extractQueryParams3(op);

        // 请求体
        String requestBody = null;
        ContentType contentType = ContentType.JSON;
        JsonNode reqBody = op.path("requestBody");
        if (!reqBody.isMissingNode()) {
            JsonNode content = reqBody.path("content");
            if (content.has("application/json")) {
                contentType = ContentType.JSON;
                requestBody = extractSchemaExample(content.path("application/json").path("schema"));
            } else if (content.has("application/x-www-form-urlencoded")) {
                contentType = ContentType.FORM;
                requestBody = extractSchemaExample(content.path("application/x-www-form-urlencoded").path("schema"));
            } else if (content.has("multipart/form-data")) {
                contentType = ContentType.MULTIPART;
            }
        }

        // 认证（从 security 字段推断）
        String authType = "NONE";
        JsonNode security = op.path("security");
        if (!security.isMissingNode() && security.isArray() && security.size() > 0) {
            authType = "BEARER"; // 默认推断为 Bearer
        }

        return ApiConfigDTO.builder()
                .name(name)
                .code(code)
                .description(!description.isEmpty() ? description : summary)
                .method(parseMethod(method))
                .url(fullUrl)
                .contentType(contentType)
                .requestParams(requestParams)
                .requestBody(requestBody)
                .authType(authType)
                .timeout(30000)
                .retryCount(0)
                .status(Status.ACTIVE)
                .groupName(groupName)
                .enableDynamicToken(false)
                .build();
    }

    /**
     * 解析 Swagger 2.0
     */
    private List<ApiConfigDTO> parseSwagger2(JsonNode root) {
        List<ApiConfigDTO> result = new ArrayList<>();

        // 提取 basePath
        String scheme = "https";
        JsonNode schemes = root.path("schemes");
        if (schemes.isArray() && schemes.size() > 0) {
            scheme = schemes.get(0).asText("https");
        }
        String host = root.path("host").asText("");
        String basePath = root.path("basePath").asText("/");
        if (!basePath.startsWith("/")) basePath = "/" + basePath;
        if (basePath.endsWith("/")) basePath = basePath.substring(0, basePath.length() - 1);
        String baseUrl = host.isEmpty() ? "" : scheme + "://" + host + basePath;

        JsonNode paths = root.path("paths");
        if (paths.isMissingNode()) return result;

        final String finalBaseUrl = baseUrl;
        paths.fields().forEachRemaining(pathEntry -> {
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();

            for (String method : Arrays.asList("get", "post", "put", "delete", "patch")) {
                JsonNode op = pathItem.path(method);
                if (op.isMissingNode()) continue;

                try {
                    ApiConfigDTO dto = buildDtoFromOp2(op, method.toUpperCase(), finalBaseUrl + path, path);
                    result.add(dto);
                } catch (Exception e) {
                    log.warn("解析路径 {} {} 失败: {}", method, path, e.getMessage());
                }
            }
        });

        return result;
    }

    private ApiConfigDTO buildDtoFromOp2(JsonNode op, String method, String fullUrl, String path) {
        String summary = op.path("summary").asText("");
        String description = op.path("description").asText("");
        String operationId = op.path("operationId").asText("");

        String name = !summary.isEmpty() ? summary
                : !operationId.isEmpty() ? operationId
                : extractLastSegment(path);
        String code = !operationId.isEmpty() ? sanitizeCode(operationId) : pathToCode(path, method);

        String groupName = "";
        JsonNode tags = op.path("tags");
        if (tags.isArray() && tags.size() > 0) {
            groupName = tags.get(0).asText("");
        }

        // 参数（query + body）
        String requestParams = null;
        String requestBody = null;
        ContentType contentType = ContentType.JSON;

        JsonNode params = op.path("parameters");
        if (params.isArray()) {
            Map<String, Object> queryMap = new LinkedHashMap<>();
            for (JsonNode p : params) {
                String in = p.path("in").asText("");
                String pName = p.path("name").asText("");
                if ("query".equals(in)) {
                    queryMap.put(pName, getDefaultValue(p));
                } else if ("body".equals(in)) {
                    JsonNode schema = p.path("schema");
                    requestBody = extractSchemaExample(schema);
                }
            }
            if (!queryMap.isEmpty()) {
                try {
                    requestParams = objectMapper.writeValueAsString(queryMap);
                } catch (Exception ignored) {}
            }
        }

        // consumes
        JsonNode consumes = op.path("consumes");
        if (consumes.isArray() && consumes.size() > 0) {
            String ct = consumes.get(0).asText("");
            if (ct.contains("form")) contentType = ContentType.FORM;
            else if (ct.contains("xml")) contentType = ContentType.XML;
        }

        return ApiConfigDTO.builder()
                .name(name)
                .code(code)
                .description(!description.isEmpty() ? description : summary)
                .method(parseMethod(method))
                .url(fullUrl)
                .contentType(contentType)
                .requestParams(requestParams)
                .requestBody(requestBody)
                .authType("NONE")
                .timeout(30000)
                .retryCount(0)
                .status(Status.ACTIVE)
                .groupName(groupName)
                .enableDynamicToken(false)
                .build();
    }

    // ==================== 导出 ====================

    /**
     * 将 ApiConfig 列表导出为 OpenAPI 3.0 JSON 字符串
     */
    public String exportToOpenApi3(List<ApiConfig> apis, String title, String version, String serverUrl) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("openapi", "3.0.3");

        // info
        ObjectNode info = root.putObject("info");
        info.put("title", title != null ? title : "API 配置导出");
        info.put("version", version != null ? version : "1.0.0");
        info.put("description", "由 integration-config-system 导出");

        // servers
        ArrayNode serversArr = root.putArray("servers");
        ObjectNode server = serversArr.addObject();
        server.put("url", serverUrl != null ? serverUrl : "https://api.example.com");
        server.put("description", "默认服务器");

        // paths
        ObjectNode pathsNode = root.putObject("paths");

        // 按 URL path 分组
        Map<String, List<ApiConfig>> byPath = new LinkedHashMap<>();
        for (ApiConfig api : apis) {
            String urlPath = extractPath(api.getUrl());
            byPath.computeIfAbsent(urlPath, k -> new ArrayList<>()).add(api);
        }

        // tags（按 groupName 收集）
        Set<String> tagSet = new LinkedHashSet<>();
        apis.forEach(a -> { if (a.getGroupName() != null && !a.getGroupName().isEmpty()) tagSet.add(a.getGroupName()); });
        ArrayNode tagsArr = root.putArray("tags");
        tagSet.forEach(t -> {
            ObjectNode tagNode = tagsArr.addObject();
            tagNode.put("name", t);
        });

        byPath.forEach((urlPath, pathApis) -> {
            ObjectNode pathItem = pathsNode.putObject(urlPath);
            pathApis.forEach(api -> {
                String m = api.getMethod().name().toLowerCase();
                ObjectNode op = pathItem.putObject(m);

                // operationId
                op.put("operationId", api.getCode());
                op.put("summary", api.getName());
                if (api.getDescription() != null && !api.getDescription().isEmpty()) {
                    op.put("description", api.getDescription());
                }

                // tags
                if (api.getGroupName() != null && !api.getGroupName().isEmpty()) {
                    op.putArray("tags").add(api.getGroupName());
                }

                // deprecated
                if (Boolean.TRUE.equals(api.getDeprecated())) {
                    op.put("deprecated", true);
                }

                // parameters（query params）
                if (api.getRequestParams() != null && !api.getRequestParams().isEmpty()) {
                    ArrayNode paramsArr = op.putArray("parameters");
                    try {
                        JsonNode paramsJson = objectMapper.readTree(api.getRequestParams());
                        paramsJson.fields().forEachRemaining(e -> {
                            ObjectNode param = paramsArr.addObject();
                            param.put("name", e.getKey());
                            param.put("in", "query");
                            param.put("required", false);
                            ObjectNode schema = param.putObject("schema");
                            schema.put("type", inferType(e.getValue()));
                            if (!e.getValue().isNull()) {
                                schema.set("example", e.getValue());
                            }
                        });
                    } catch (Exception ignored) {}
                }

                // requestBody
                if (api.getRequestBody() != null && !api.getRequestBody().isEmpty()
                        && api.getMethod() != HttpMethod.GET) {
                    ObjectNode reqBody = op.putObject("requestBody");
                    reqBody.put("required", true);
                    ObjectNode content = reqBody.putObject("content");
                    String mediaType = api.getContentType() != null
                            ? api.getContentType().getValue()
                            : "application/json";
                    ObjectNode mediaObj = content.putObject(mediaType);
                    ObjectNode schema = mediaObj.putObject("schema");
                    schema.put("type", "object");
                    try {
                        JsonNode bodyJson = objectMapper.readTree(api.getRequestBody());
                        if (bodyJson.isObject()) {
                            ObjectNode props = schema.putObject("properties");
                            bodyJson.fields().forEachRemaining(e -> {
                                ObjectNode prop = props.putObject(e.getKey());
                                prop.put("type", inferType(e.getValue()));
                                if (!e.getValue().isNull()) {
                                    prop.set("example", e.getValue());
                                }
                            });
                        }
                    } catch (Exception ignored) {
                        schema.put("example", api.getRequestBody());
                    }
                }

                // responses（固定 200）
                ObjectNode responses = op.putObject("responses");
                ObjectNode resp200 = responses.putObject("200");
                resp200.put("description", "成功");
                ObjectNode respContent = resp200.putObject("content");
                ObjectNode respJson = respContent.putObject("application/json");
                ObjectNode respSchema = respJson.putObject("schema");
                respSchema.put("type", "object");

                // security（如果有认证）
                if (api.getAuthType() != null && !"NONE".equals(api.getAuthType())) {
                    ArrayNode security = op.putArray("security");
                    ObjectNode secItem = security.addObject();
                    secItem.putArray("bearerAuth");
                }

                // x-extensions（版本信息）
                if (api.getVersion() != null) {
                    op.put("x-version", api.getVersion());
                    op.put("x-base-code", api.getBaseCode() != null ? api.getBaseCode() : api.getCode());
                    op.put("x-latest-version", Boolean.TRUE.equals(api.getLatestVersion()));
                }
            });
        });

        // components.securitySchemes
        ObjectNode components = root.putObject("components");
        ObjectNode secSchemes = components.putObject("securitySchemes");
        ObjectNode bearerScheme = secSchemes.putObject("bearerAuth");
        bearerScheme.put("type", "http");
        bearerScheme.put("scheme", "bearer");
        bearerScheme.put("bearerFormat", "JWT");

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    // ==================== 工具方法 ====================

    private String extractQueryParams3(JsonNode op) {
        JsonNode params = op.path("parameters");
        if (!params.isArray()) return null;
        Map<String, Object> queryMap = new LinkedHashMap<>();
        for (JsonNode p : params) {
            if ("query".equals(p.path("in").asText(""))) {
                queryMap.put(p.path("name").asText(""), getDefaultValue(p));
            }
        }
        if (queryMap.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(queryMap);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractSchemaExample(JsonNode schema) {
        if (schema == null || schema.isMissingNode()) return null;
        // 如果有 example 字段直接用
        if (schema.has("example")) {
            try {
                return objectMapper.writeValueAsString(schema.get("example"));
            } catch (Exception ignored) {}
        }
        // 根据 properties 构建示例
        JsonNode props = schema.path("properties");
        if (props.isObject()) {
            Map<String, Object> example = new LinkedHashMap<>();
            props.fields().forEachRemaining(e -> {
                example.put(e.getKey(), getDefaultValueFromSchema(e.getValue()));
            });
            try {
                return objectMapper.writeValueAsString(example);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Object getDefaultValue(JsonNode param) {
        if (param.has("example")) return param.get("example").asText();
        if (param.has("default")) return param.get("default").asText();
        JsonNode schema = param.path("schema");
        if (!schema.isMissingNode()) return getDefaultValueFromSchema(schema);
        String type = param.path("type").asText("string");
        return getDefaultForType(type);
    }

    private Object getDefaultValueFromSchema(JsonNode schema) {
        if (schema.has("example")) return schema.get("example").asText();
        if (schema.has("default")) return schema.get("default").asText();
        String type = schema.path("type").asText("string");
        return getDefaultForType(type);
    }

    private Object getDefaultForType(String type) {
        switch (type) {
            case "integer": case "number": return 0;
            case "boolean": return false;
            case "array": return Collections.emptyList();
            case "object": return Collections.emptyMap();
            default: return "";
        }
    }

    private String inferType(JsonNode value) {
        if (value == null || value.isNull()) return "string";
        if (value.isInt() || value.isLong()) return "integer";
        if (value.isDouble() || value.isFloat()) return "number";
        if (value.isBoolean()) return "boolean";
        if (value.isArray()) return "array";
        if (value.isObject()) return "object";
        return "string";
    }

    private String extractPath(String url) {
        if (url == null) return "/";
        try {
            int idx = url.indexOf("://");
            if (idx < 0) return url.startsWith("/") ? url : "/" + url;
            int pathStart = url.indexOf("/", idx + 3);
            if (pathStart < 0) return "/";
            String path = url.substring(pathStart).split("\\?")[0];
            return path.isEmpty() ? "/" : path;
        } catch (Exception e) {
            return "/";
        }
    }

    private String extractLastSegment(String path) {
        if (path == null || path.isEmpty()) return "API";
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = parts[i];
            if (!p.isEmpty() && !p.startsWith("{")) {
                return p.substring(0, 1).toUpperCase() + p.substring(1);
            }
        }
        return "API";
    }

    private String pathToCode(String path, String method) {
        if (path == null) return method.toLowerCase() + "-api";
        String code = path.replaceAll("\\{[^}]+\\}", "")
                .replaceAll("[^a-zA-Z0-9/]", "-")
                .replaceAll("/+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase();
        return method.toLowerCase() + "-" + (code.isEmpty() ? "api" : code);
    }

    private String sanitizeCode(String operationId) {
        return operationId.replaceAll("[^a-zA-Z0-9_-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase();
    }

    private HttpMethod parseMethod(String method) {
        try {
            return HttpMethod.valueOf(method.toUpperCase());
        } catch (Exception e) {
            return HttpMethod.GET;
        }
    }
}
