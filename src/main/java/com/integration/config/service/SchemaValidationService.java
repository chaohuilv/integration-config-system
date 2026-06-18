package com.integration.config.service;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON Schema 校验服务
 * 支持 draft-07 标准的 JSON Schema 校验
 * 校验请求参数、请求体、响应结果是否符合预定义的 Schema
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchemaValidationService {

    private final ObjectMapper objectMapper;

    /** 已编译的 Schema 缓存，避免重复解析 */
    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    /** Schema 缓存的 Key 前缀 */
    private static final String SCHEMA_CACHE_PREFIX = "schema:";

    /**
     * 校验请求体是否符合 Schema
     *
     * @param schemaStr JSON Schema 字符串
     * @param body      请求体（JSON 字符串或普通字符串）
     * @param traceId   链路 ID（用于日志）
     * @return 校验结果，无错误返回 null
     */
    public String validateRequestBody(String schemaStr, Object body, String traceId) {
        if (schemaStr == null || schemaStr.isBlank()) {
            return null;
        }

        // 空请求体时，如果 Schema 的 required 字段为空则允许
        if (body == null || body.toString().isBlank()) {
            return null;
        }

        try {
            JsonNode bodyNode;
            if (body instanceof String) {
                bodyNode = objectMapper.readTree(body.toString());
            } else if (body instanceof Map || body instanceof List) {
                bodyNode = objectMapper.valueToTree(body);
            } else {
                bodyNode = objectMapper.valueToTree(body);
            }

            return validate(schemaStr, bodyNode, traceId, "请求体");
        } catch (Exception e) {
            log.warn("[{}] 请求体 JSON 解析失败: {}", traceId, e.getMessage());
            return "请求体格式错误（非合法 JSON）: " + e.getMessage();
        }
    }

    /**
     * 校验请求参数（Query Params）是否符合 Schema
     *
     * @param schemaStr JSON Schema 字符串
     * @param params    请求参数 Map
     * @param traceId   链路 ID
     * @return 校验结果，无错误返回 null
     */
    public String validateRequestParams(String schemaStr, Map<String, Object> params, String traceId) {
        if (schemaStr == null || schemaStr.isBlank()) {
            return null;
        }

        if (params == null || params.isEmpty()) {
            return null;
        }

        try {
            // 将 Map 转为 JsonNode
            JsonNode paramsNode = objectMapper.valueToTree(params);
            return validate(schemaStr, paramsNode, traceId, "请求参数");
        } catch (Exception e) {
            log.warn("[{}] 请求参数转换失败: {}", traceId, e.getMessage());
            return "请求参数格式错误: " + e.getMessage();
        }
    }

    /**
     * 校验响应结果是否符合 Schema
     *
     * @param schemaStr JSON Schema 字符串
     * @param response  响应数据
     * @param traceId   链路 ID
     * @return 校验结果，无错误返回 null
     */
    public String validateResponse(String schemaStr, Object response, String traceId) {
        if (schemaStr == null || schemaStr.isBlank()) {
            return null;
        }

        if (response == null) {
            return null;
        }

        try {
            JsonNode responseNode;
            if (response instanceof String) {
                // 尝试解析为 JSON，如果失败则作为纯文本不校验
                try {
                    responseNode = objectMapper.readTree(response.toString());
                } catch (Exception e) {
                    log.debug("[{}] 响应为非 JSON 文本，跳过 Schema 校验", traceId);
                    return null;
                }
            } else if (response instanceof Map || response instanceof List) {
                responseNode = objectMapper.valueToTree(response);
            } else {
                responseNode = objectMapper.valueToTree(response);
            }

            return validate(schemaStr, responseNode, traceId, "响应结果");
        } catch (Exception e) {
            log.warn("[{}] 响应结果校验异常: {}", traceId, e.getMessage());
            // 校验异常不阻止响应，仅记录警告
            return "响应结果 Schema 校验异常: " + e.getMessage();
        }
    }

    /**
     * 核心校验逻辑
     *
     * @param schemaStr  JSON Schema 字符串
     * @param data       待校验数据节点
     * @param traceId    链路 ID
     * @param fieldLabel 字段标签（用于错误消息）
     * @return 格式化后的错误信息，或 null 表示校验通过
     */
    private String validate(String schemaStr, JsonNode data, String traceId, String fieldLabel) {
        try {
            // 1. 获取或编译 Schema
            JsonSchema schema = getOrCompileSchema(schemaStr);

            // 2. 执行校验
            Set<ValidationMessage> messages = schema.validate(data);

            // 3. 格式化错误消息
            if (messages == null || messages.isEmpty()) {
                return null;
            }

            // 4. 构建人类可读的错误消息
            StringBuilder sb = new StringBuilder();
            sb.append(fieldLabel).append(" Schema 校验失败（共 ").append(messages.size()).append(" 项错误）：\n");
            int idx = 1;
            for (ValidationMessage msg : messages) {
                String path = msg.getPath();
                String message = msg.getMessage();
                sb.append("  ").append(idx++).append(". ");
                if (!path.isEmpty()) {
                    sb.append("字段 [").append(formatPath(path)).append("]：");
                }
                sb.append(formatMessage(message)).append("\n");
            }

            String result = sb.toString();
            log.warn("[{}] {}校验失败: {}", traceId, fieldLabel, result.replace("\n", " | "));
            return result.trim();

        } catch (SchemaException e) {
            log.error("[{}] Schema 格式错误，无法解析: {}", traceId, e.getMessage());
            return "Schema 定义格式错误: " + e.getMessage();
        } catch (Exception e) {
            log.error("[{}] {}校验异常: {}", traceId, fieldLabel, e.getMessage(), e);
            return fieldLabel + " 校验异常: " + e.getMessage();
        }
    }

    /**
     * 获取或编译 JSON Schema（带缓存）
     *
     * @param schemaStr JSON Schema 字符串
     * @return 编译后的 JsonSchema
     */
    private JsonSchema getOrCompileSchema(String schemaStr) {
        String cacheKey = SCHEMA_CACHE_PREFIX + schemaStr.hashCode();
        return schemaCache.computeIfAbsent(cacheKey, k -> {
            try {
                JsonNode schemaNode = objectMapper.readTree(schemaStr);
                JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                        SpecVersion.VersionFlag.V7
                );
                return factory.getSchema(schemaNode);
            } catch (Exception e) {
                throw new SchemaException("JSON Schema 解析失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 格式化 JSON Path（去掉开头的 #/）
     */
    private String formatPath(String path) {
        if (path.startsWith("#/")) {
            return path.substring(2);
        }
        return path;
    }

    /**
     * 简化错误消息，移除冗余前缀
     */
    private String formatMessage(String message) {
        // networknt 的消息格式如: "size must be between 1 and 100"
        // 或 "#/name: is a required property"
        // 提取核心信息
        if (message.contains("is a required property")) {
            return "缺少必填字段";
        }
        if (message.contains("must be")) {
            return message;
        }
        if (message.contains("expected type")) {
            return "类型错误: " + message;
        }
        return message;
    }

    /**
     * 校验 Schema 定义本身是否合法（用于编辑器实时校验）
     *
     * @param schemaStr JSON Schema 字符串
     * @return 错误信息，或 null 表示 Schema 合法
     */
    public String validateSchemaDefinition(String schemaStr) {
        if (schemaStr == null || schemaStr.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(schemaStr);
            // 确保是对象
            if (!node.isObject()) {
                return "Schema 必须是 JSON 对象";
            }
            // 确保包含 $schema 或 type（基本校验）
            if (!node.has("$schema") && !node.has("type")) {
                return "建议包含 \"type\" 或 \"$schema\" 字段以确保有效";
            }
            // 尝试编译
            getOrCompileSchema(schemaStr);
            return null;
        } catch (Exception e) {
            return "Schema 格式错误: " + e.getMessage();
        }
    }

    /**
     * 清除 Schema 缓存（一般不需要手动调用，缓存量很小）
     */
    public void clearCache() {
        schemaCache.clear();
        log.info("Schema 缓存已清空");
    }

    /**
     * Schema 编译异常
     */
    public static class SchemaException extends RuntimeException {
        public SchemaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
