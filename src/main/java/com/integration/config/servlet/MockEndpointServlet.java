package com.integration.config.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.integration.config.entity.config.MockConfig;
import com.integration.config.service.MockConfigService;
import com.integration.config.service.MockTemplateEngine;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mock 端点 Servlet
 *
 * <p>处理 Mock 端口收到的所有请求：
 * <ol>
 *   <li>根据请求路径、方法查找匹配的 Mock 配置</li>
 *   <li>执行匹配规则（Header/Query/Body 条件）</li>
 *   <li>渲染响应模板（替换动态变量）</li>
 *   <li>按配置的延迟返回响应</li>
 * </ol>
 */
@Slf4j
public class MockEndpointServlet extends HttpServlet {

    private MockConfigService mockConfigService;
    private ObjectMapper objectMapper;

    @Override
    public void init() throws ServletException {
        super.init();
        WebApplicationContext ctx = WebApplicationContextUtils
                .getWebApplicationContext(getServletContext());
        if (ctx != null) {
            this.mockConfigService = ctx.getBean(MockConfigService.class);
            this.objectMapper = ctx.getBean(ObjectMapper.class);
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // 获取请求信息（剥离 /mock 前缀）
        String method = req.getMethod();
        String requestPath = req.getRequestURI();
        // Servlet 映射在 /mock/*，剥离前缀以便与数据库配置的 path 匹配
        String servletPath = req.getServletPath(); // "/mock"
        if (requestPath.startsWith(servletPath)) {
            requestPath = requestPath.substring(servletPath.length());
        }
        if (!requestPath.startsWith("/")) {
            requestPath = "/" + requestPath;
        }

        // 获取查询参数
        Map<String, String[]> queryParams = req.getParameterMap();

        // 获取请求头
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, req.getHeader(name));
        }

        // 获取请求体
        String body = null;
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)) {
            body = req.getReader().lines().collect(Collectors.joining("\n"));
        }

        log.debug("Mock 请求: {} {} headers={} body={}",
                method, requestPath, headers.keySet(), body != null ? body.length() + " chars" : "null");

        // 查找匹配的 Mock 配置
        Optional<MockConfig> matchedConfig = mockConfigService.findMatch(
                requestPath, method, queryParams, headers, body);

        if (matchedConfig.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.setContentType("application/json;charset=UTF-8");
            PrintWriter writer = resp.getWriter();
            writer.write("{\"code\":404,\"message\":\"No mock configuration matched\"}");
            writer.flush();
            return;
        }

        MockConfig config = matchedConfig.get();

        // 构建模板上下文
        MockTemplateEngine.MockContext context = MockTemplateEngine.MockContext.builder()
                .queryParams(queryParams)
                .headers(headers)
                .body(body)
                .build();

        // 渲染响应体
        String responseBody = mockConfigService.executeMock(config, context);

        // 解析响应头
        Map<String, String> responseHeaders = mockConfigService.parseResponseHeaders(
                config.getResponseHeaders());

        // 设置响应头
        responseHeaders.forEach((key, value) -> {
            // 渲染响应头中的模板变量
            String renderedValue = mockConfigService.executeMock(config, context);
            resp.setHeader(key, renderedValue != null ? renderedValue : value);
        });

        // 设置默认 Content-Type
        if (!responseHeaders.containsKey("Content-Type")) {
            resp.setContentType("application/json;charset=UTF-8");
        }

        // 设置状态码
        resp.setStatus(config.getStatusCode());

        // 延迟返回
        int delayMs = config.getDelayMs() != null ? config.getDelayMs() : 0;
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 写入响应体
        PrintWriter writer = resp.getWriter();
        writer.write(responseBody != null ? responseBody : "");
        writer.flush();

        log.info("Mock 命中: [{}] {} {} → {} (delay={}ms)",
                config.getCode(), method, requestPath, config.getStatusCode(), delayMs);
    }
}
