package com.integration.config.util;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 工具类
 */
@Slf4j
public class JsonUtil {

    // 私有构造函数阻止外部实例化
    private JsonUtil() {
        throw new AssertionError("JSON工具类禁止实例化");
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 对象转 JSON 字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败", e);
            return JSONUtil.toJsonStr(obj);
        }
    }

    /**
     * JSON 字符串转对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化失败: {}", json, e);
            return null;
        }
    }

    /**
     * 判断是否为有效 JSON
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        return JSONUtil.isTypeJSON(json);
    }

    /**
     * 格式化 JSON
     */
    public static String format(String json) {
        if (!isValidJson(json)) {
            return json;
        }
        return JSONUtil.toJsonPrettyStr(json);
    }
}
