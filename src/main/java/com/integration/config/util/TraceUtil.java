package com.integration.config.util;

import java.util.UUID;

/**
 * 追踪 ID 生成工具
 */
public class TraceUtil {

    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

    /**
     * 生成新的追踪ID
     */
    public static String generate() {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        TRACE_ID_HOLDER.set(traceId);
        return traceId;
    }

    /**
     * 获取当前追踪ID
     */
    public static String get() {
        String traceId = TRACE_ID_HOLDER.get();
        return traceId != null ? traceId : generate();
    }

    /**
     * 清除追踪ID
     */
    public static void clear() {
        TRACE_ID_HOLDER.remove();
    }
}
