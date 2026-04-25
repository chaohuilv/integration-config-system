package com.integration.config.config;

import com.integration.config.servlet.MockEndpointServlet;
import io.undertow.Undertow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mock 服务独立端口配置
 *
 * <p>通过 application.yml 配置独立端口：
 * <pre>
 * mock:
 *   server:
 *     port: 9999  # Mock 服务端口，默认 9999
 *     enabled: true
 * </pre>
 *
 * <p>实现方式：在主应用 Undertow 容器中添加额外的 Connector
 */
@Slf4j
@Configuration
public class MockServerConfig {

    @Value("${mock.server.port:9999}")
    private int mockServerPort;

    @Value("${mock.server.enabled:true}")
    private boolean mockServerEnabled;

    @Value("${server.port:8080}")
    private int mainServerPort;

    /**
     * 配置 Undertow 多端口监听
     * 主应用端口：正常业务接口（/api/*）
     * Mock 端口：Mock 服务接口（全部由 MockEndpointServlet 处理）
     */
    @Bean
    public UndertowServletWebServerFactory undertowServletWebServerFactory() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();

        if (mockServerEnabled && mockServerPort != mainServerPort) {
            factory.addBuilderCustomizers(builder -> {
                log.info("启动 Mock 服务端口: {}", mockServerPort);
                builder.addHttpListener(mockServerPort, "0.0.0.0");
            });
        }

        return factory;
    }

    /**
     * 注册 Mock 处理 Servlet
     * 监听 /mock/* 路径，处理所有 Mock 请求
     */
    @Bean
    public ServletRegistrationBean<MockEndpointServlet> mockEndpointServlet() {
        MockEndpointServlet servlet = new MockEndpointServlet();
        ServletRegistrationBean<MockEndpointServlet> registration =
                new ServletRegistrationBean<>(servlet, "/mock/*");
        registration.setName("mockEndpointServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
