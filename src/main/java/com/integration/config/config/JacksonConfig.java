package com.integration.config.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;

/**
 * Jackson 全局序列化配置
 * - Long/Long 类型的 ID 序列化为 String，避免 JavaScript 精度丢失
 * - Long.MAX_VALUE = 9223372036854775807，超出 JS 安全整数 9007199254740991
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false)
                .modules(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        // Long/Integer/BigInteger/Long 统一序列化为 String，避免 JS 精度丢失
        objectMapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addSerializer(Long.class, ToStringSerializer.instance)
                .addSerializer(long.class, ToStringSerializer.instance)
                .addSerializer(BigDecimal.class, new com.fasterxml.jackson.databind.ser.std.ToStringSerializer())
        );

        // 空字符串 "" → 空集合 []（List / Set 等 Collection 类型）
        objectMapper.coercionConfigFor(LogicalType.Collection)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsEmpty);

        return objectMapper;
    }
}
