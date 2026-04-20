package com.integration.config.util;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 统一消息获取工具。
 * <p>
 * 用法示例：
 * <pre>
 * // 直接用 key（英文消息，用于后端日志/debug）
 * Messages.get("AUTH_FAILED");
 *
 * // 带参数（消息中有 {0} 占位符）
 * Messages.get("ALREADY_EXISTS", "user-api:v1");
 *
 * // 获取中文消息（前端展示用）
 * Messages.getZh("AUTH_FAILED");
 * </pre>
 *
 * 注意：生产环境推荐前端根据 code 做 i18n，此工具仅作后端日志/调试用途。
 */
@Component
public class Messages {

    private static MessageSource messageSource;

    public Messages(MessageSource messageSource) {
        Messages.messageSource = messageSource;
    }

    /**
     * 获取英文消息（默认）
     */
    public static String get(String code, Object... args) {
        return resolve(code, Locale.ENGLISH, args);
    }

    /**
     * 获取中文消息
     */
    public static String getZh(String code, Object... args) {
        return resolve(code, Locale.CHINESE, args);
    }

    /**
     * 根据请求 Accept-Language 自动选择语言
     */
    public static String getAuto(String code, Object... args) {
        return resolve(code, LocaleContextHolder.getLocale(), args);
    }

    private static String resolve(String code, Locale locale, Object... args) {
        try {
            String msg = messageSource.getMessage(code, args, locale);
            return msg != null ? msg : code;
        } catch (Exception e) {
            return code;
        }
    }
}
