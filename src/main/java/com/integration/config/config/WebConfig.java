package com.integration.config.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Web MVC 配置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 注册 Converter：空字符串/null → null，避免 Required parameter 'startTime' not populated 报错
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        // String → LocalDateTime，空值时返回 null 而非抛异常
        registry.addConverter(new Converter<String, LocalDateTime>() {
            private final DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
            };

            @Override
            public LocalDateTime convert(String source) {
                if (source == null || source.trim().isEmpty()) {
                    return null;
                }
                String s = source.trim();
                for (DateTimeFormatter fmt : formatters) {
                    try {
                        return LocalDateTime.parse(s, fmt);
                    } catch (Exception ignored) {}
                }
                // 都解析不了时返回 null，不抛异常
                return null;
            }
        });
    }
}
