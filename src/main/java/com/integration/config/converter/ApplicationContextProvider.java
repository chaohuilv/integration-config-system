package com.integration.config.converter;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * 应用上下文持有者
 *
 * <p>供非 Spring 管理的组件（如 JPA {@link jakarta.persistence.AttributeConverter}）
 * 在需要时获取 Spring Bean。
 *
 * <p>由 Spring 自动填充 applicationContext。
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    public static ApplicationContext getContext() {
        return context;
    }
}