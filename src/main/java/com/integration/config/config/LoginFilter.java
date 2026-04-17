package com.integration.config.config;

import com.integration.config.service.TokenService;
import com.integration.config.service.TokenService.TokenInfo;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 登录拦截器 - Bearer Token 认证模式
 * 从 Authorization: Bearer <token> 提取 Token 并验证
 */
@Component
@Order(Integer.MIN_VALUE + 100)
@Slf4j
public class LoginFilter implements Filter {

    private final TokenService tokenService;

    public LoginFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    // 不需要登录的路径前缀
    private static final String[] EXCLUDE_PREFIXES = {
            "/login.html",
            "/api/auth/login",
            // "/api/auth/check",  // 移除：check 需要验证 Token
            "/api/health/",
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

        // 从 Authorization: Bearer <token> 提取 Token
        String token = extractBearerToken(req);

        // 验证 Token
        TokenInfo tokenInfo = tokenService.validateToken(token);

        if (tokenInfo == null) {
            log.warn("[LoginFilter] Access denied - invalid token. URI: {}", uri);
            // API请求返回401
            if (uri.startsWith("/api/")) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"code\":401,\"message\":\"未登录或Token已过期\",\"data\":null}");
                return;
            }
            // 页面请求重定向到登录页
            res.sendRedirect("/login.html");
            return;
        }

        // Token 有效，续期
        tokenService.refreshToken(token);

        // 将用户信息存入 Request Attribute，供下游 Controller 使用
        req.setAttribute("userId", tokenInfo.getUserId());
        req.setAttribute("userCode", tokenInfo.getUserCode());
        req.setAttribute("username", tokenInfo.getUsername());
        req.setAttribute("displayName", tokenInfo.getDisplayName());

        log.debug("[LoginFilter] Access granted. URI: {}, user: {}", uri, tokenInfo.getUserCode());
        chain.doFilter(request, response);
    }

    /**
     * 从 Authorization header 提取 Bearer token
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        // 兼容：也支持从 query parameter 获取 token
        String queryToken = request.getParameter("access_token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken.trim();
        }
        return null;
    }
}
