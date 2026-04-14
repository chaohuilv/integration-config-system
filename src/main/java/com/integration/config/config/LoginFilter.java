package com.integration.config.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 登录拦截器
 * 保护需要登录的资源
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoginFilter implements Filter {

    // 不需要登录的路径前缀
    private static final String[] EXCLUDE_PREFIXES = {
            "/login.html",
            "/api/auth/login",
            "/api/auth/check",
            "/api/version",
            "/h2-console",
            "/css/",
            "/js/"
    };

    // 不需要登录的完整路径
    private static final String[] EXCLUDE_EXACTS = {
            "/",
            "/login.html",
            "/favicon.ico"
    };

    // 静态资源后缀
    private static final String[] STATIC_SUFFIXES = {
            ".css", ".js", ".ico", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".woff", ".woff2", ".ttf", ".eot", ".html"
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String uri = req.getRequestURI();

        // 排除静态资源后缀
        for (String suffix : STATIC_SUFFIXES) {
            if (uri.endsWith(suffix)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 排除精确匹配的路径
        for (String exact : EXCLUDE_EXACTS) {
            if (uri.equals(exact)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 排除前缀匹配的路径
        for (String prefix : EXCLUDE_PREFIXES) {
            if (uri.startsWith(prefix)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 检查登录状态
        HttpSession session = req.getSession(false);
        boolean loggedIn = session != null && session.getAttribute("userId") != null;

        if (!loggedIn) {
            // API请求返回401
            if (uri.startsWith("/api/")) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"code\":401,\"message\":\"未登录或会话已过期\",\"data\":null}");
                return;
            }
            // 页面请求重定向到登录页
            res.sendRedirect("/login.html");
            return;
        }

        chain.doFilter(request, response);
    }
}

