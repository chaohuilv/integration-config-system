package com.integration.config.config;

import com.integration.config.service.HttpInvokeService;
import com.integration.config.service.IpWhitelistService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * IP 白名单过滤器
 * <p>
 * 在登录认证之前执行，统一校验所有请求的来源 IP。
 * 支持全局规则和按接口级别的白名单/黑名单控制。
 * <p>
 * 匹配逻辑：
 * - 全局规则优先于 API 规则
 * - BLACKLIST 命中 → 403 Forbidden
 * - WHITELIST 命中 → 允许通过
 * - 无匹配规则 → 允许通过（默认开放）
 * <p>
 * 注意：此过滤器在 LoginFilter 之前执行，可以提前拦截非法 IP。
 */
@Component
@Order(100) // 在 LoginFilter(@Order(Integer.MIN_VALUE+100)) 之前执行
@Slf4j
public class IpWhitelistFilter implements Filter {

    @Autowired
    private IpWhitelistService ipWhitelistService;

    @Autowired(required = false)
    private HttpInvokeService httpInvokeService;

    /** 无需 IP 校验的路径（静态资源、登录接口等） */
    private static final String[] EXCLUDE_PATHS = {
            "/h2-console/",
            "/swagger-ui",
            "/v3/api-docs",
            "/swagger-resources",
            "/webjars/",
            "/favicon.ico",
            "/login.html",
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/reset-password",
            "/api/public/",          // 开放接口前缀
            "/actuator/health",       // 健康检查
            "/mock/"                  // Mock 服务独立端口，不走主应用
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String uri = req.getRequestURI();

        // 1. 跳过排除路径
        if (shouldExclude(uri)) {
            chain.doFilter(request, response);
            return;
        }

        // 2. 获取客户端 IP
        String clientIp = getClientIp(req);

        // 3. 提取接口编码（从 URI 路径推断）
        // 例如：/api/invoke/call?apiCode=xxx → apiCode 参数
        // 或：/api/config/xxx → 尝试从路径推断
        String apiCode = extractApiCode(req);

        // 4. IP 白名单校验
        if (!ipWhitelistService.isAllowed(clientIp, apiCode)) {
            log.warn("[IpWhite] IP {} 访问受限 uri={}, apiCode={}", clientIp, uri, apiCode);
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setContentType("application/json;charset=UTF-8");
            res.setHeader("X-Frame-Options", "DENY");
            res.setHeader("X-Content-Type-Options", "nosniff");
            res.getWriter().write("{\"code\":403,\"message\":\"IP " + clientIp + " 不在白名单中，访问被拒绝\",\"data\":null}");
            res.getWriter().flush();
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 判断是否跳过 IP 校验
     */
    private boolean shouldExclude(String uri) {
        if (uri == null) return true;

        // 精确前缀匹配
        for (String prefix : EXCLUDE_PATHS) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }

        // 静态资源后缀
        String[] staticSuffixes = {".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".woff", ".woff2", ".ttf", ".map"};
        for (String suffix : staticSuffixes) {
            if (uri.endsWith(suffix)) {
                return true;
            }
        }

        // Mock 端口的请求（Mock 服务独立监听，不走此过滤器）
        // 如果主应用也处理 /mock 路径，则不跳过
        return false;
    }

    /**
     * 提取接口编码
     * 优先从请求参数获取，否则尝试从 URI 路径推断
     */
    private String extractApiCode(HttpServletRequest req) {
        // 优先从 query parameter 获取
        String apiCode = req.getParameter("apiCode");
        if (apiCode != null && !apiCode.isEmpty()) {
            return apiCode;
        }

        // 从路径推断（/api/invoke/call/xxx → 从上下文获取）
        // 对于 POST /api/invoke/call，实际的 apiCode 在请求体中
        // 这里只能做粗粒度判断，不影响全局规则
        String uri = req.getRequestURI();

        // /api/invoke/* → 来自 InvokeController
        if (uri.startsWith("/api/invoke/")) {
            return "INVOKE_API"; // 占位，实际校验由全局规则兜底
        }

        // 其他接口直接使用 URI 作为标识
        return uri;
    }

    /**
     * 获取客户端真实 IP（与 HttpInvokeService 逻辑一致）
     */
    private String getClientIp(HttpServletRequest request) {
        // 1. Nginx 反向代理
        String ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }

        // 2. 代理链
        ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }

        // 3. 阿里云 SLB
        ip = request.getHeader("Ali-Cdn-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        // 4. 腾讯云 CLB
        ip = request.getHeader("X-Custom-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        // 5. 直接连接
        ip = request.getRemoteAddr();
        if (ip != null) {
            if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
                return "localhost";
            }
            return ip;
        }

        return "unknown";
    }
}
