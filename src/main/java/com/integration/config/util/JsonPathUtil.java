package com.integration.config.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * JSONPath工具类
 * 简单实现，支持 $.data.token 格式的路径提取
 */
@Slf4j
public class JsonPathUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从JSON字符串中提取指定路径的值
     * 支持格式：
     * - $.data.accessToken
     * - $.token
     * - data.accessToken (可省略 $. 前缀)
     * - $[0].name (数组索引)
     *
     * @param json JSON字符串
     * @param path JSONPath路径
     * @return 提取的值，如果路径不存在返回null
     */
    public static String extract(String json, String path) {
        if (json == null || json.isEmpty() || path == null || path.isEmpty()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            return extractFromNode(root, path);
        } catch (Exception e) {
            log.error("JSON解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从JsonNode中提取指定路径的值
     */
    private static String extractFromNode(JsonNode node, String path) {
        // 处理路径格式
        String normalizedPath = path;
        if (path.startsWith("$.")) {
            normalizedPath = path.substring(2);
        } else if (path.startsWith("$")) {
            normalizedPath = path.substring(1);
        }

        // 分割路径
        String[] parts = normalizedPath.split("\\.");
        JsonNode current = node;

        for (String part : parts) {
            if (part.isEmpty()) continue;

            // 处理数组索引 [0]
            if (part.startsWith("[") && part.endsWith("]")) {
                try {
                    int index = Integer.parseInt(part.substring(1, part.length() - 1));
                    if (current.isArray() && index < current.size()) {
                        current = current.get(index);
                    } else {
                        return null;
                    }
                } catch (NumberFormatException e) {
                    log.warn("无效的数组索引: {}", part);
                    return null;
                }
            } else {
                // 处理对象属性，支持混合格式如 items[0].name
                if (part.contains("[")) {
                    String fieldName = part.substring(0, part.indexOf("["));
                    String indexStr = part.substring(part.indexOf("[") + 1, part.indexOf("]"));
                    try {
                        int index = Integer.parseInt(indexStr);
                        if (current.has(fieldName)) {
                            JsonNode arrayNode = current.get(fieldName);
                            if (arrayNode.isArray() && index < arrayNode.size()) {
                                current = arrayNode.get(index);
                            } else {
                                return null;
                            }
                        } else {
                            return null;
                        }
                    } catch (NumberFormatException e) {
                        log.warn("无效的数组索引: {}", indexStr);
                        return null;
                    }
                } else {
                    if (current.has(part)) {
                        current = current.get(part);
                    } else {
                        log.debug("路径不存在: {}", part);
                        return null;
                    }
                }
            }
        }

        // 返回值的文本形式
        if (current.isValueNode()) {
            return current.asText();
        } else if (current.isObject() || current.isArray()) {
            return current.toString();
        }
        return null;
    }

    /**
     * 从JSON字符串中提取指定路径的值，带默认值
     */
    public static String extract(String json, String path, String defaultValue) {
        String result = extract(json, path);
        return result != null ? result : defaultValue;
    }
}
