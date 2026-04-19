package com.integration.config.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.integration.config.enums.AppConstants;
import com.integration.config.annotation.AuditLog;
import com.integration.config.entity.log.AuditSysLog;
import com.integration.config.repository.log.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * 审计日志 AOP 切面
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        LocalDateTime startDateTime = LocalDateTime.now();

        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        // 获取用户信息（从 Request Attribute，由 LoginFilter 设置）
        Long userId = request != null ? (Long) request.getAttribute("userId") : null;
        String userCode = request != null ? (String) request.getAttribute("userCode") : null;
        String userName = request != null ? (String) request.getAttribute("username") : null;

        // 创建审计日志对象
        AuditSysLog.AuditSysLogBuilder auditLogBuilder = AuditSysLog.builder()
                .operateType(auditLog.operateType())
                .module(auditLog.module())
                .userId(userId)
                .userCode(userCode)
                .userName(userName)
                .operateTime(startDateTime);

        // 提前获取方法签名和参数（多处使用）
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = discoverer.getParameterNames(signature.getMethod());
        Object[] args = joinPoint.getArgs();

        // 设置请求信息
        if (request != null) {
            auditLogBuilder
                    .clientIp(getClientIp(request))
                    .userAgent(request.getHeader("User-Agent"))
                    .requestMethod(request.getMethod())
                    .requestUri(request.getRequestURI());

            // 记录请求参数
            try {
                Map<String, Object> requestParamsMap = new HashMap<>();
                boolean isGetLike = "GET".equalsIgnoreCase(request.getMethod());

                if (isGetLike) {
                    // GET 请求：路径参数 + Query 参数
                    // 1. 路径参数（@PathVariable）：从方法参数中提取
                    Map<String, Object> pathParams = extractPathParams(joinPoint, signature, paramNames, args);
                    if (!pathParams.isEmpty()) {
                        requestParamsMap.put("path", pathParams);
                    }

                    // 2. Query 参数（@RequestParam）
                    if (!request.getParameterMap().isEmpty()) {
                        Map<String, Object> queryParams = new HashMap<>();
                        request.getParameterMap().forEach((k, v) -> queryParams.put(k, String.join(",", v)));
                        requestParamsMap.put("query", queryParams);
                    }
                } else {
                    // 非 GET 请求：query string + body JSON
                    // 1. Query String
                    if (!request.getParameterMap().isEmpty()) {
                        Map<String, Object> queryParams = new HashMap<>();
                        request.getParameterMap().forEach((k, v) -> queryParams.put(k, String.join(",", v)));
                        requestParamsMap.put("query", queryParams);
                    }

                    // 2. Body JSON（recordParams=true 时记录）
                    if (auditLog.recordParams()) {
                        String body = getCachedBody(request);
                        if (body != null && !body.isBlank()) {
                            requestParamsMap.put("body", body);
                        }
                    }
                }

                if (!requestParamsMap.isEmpty()) {
                    auditLogBuilder.requestParams(objectMapper.writeValueAsString(requestParamsMap));
                }
            } catch (Exception e) {
                log.warn("Failed to record request params", e);
            }
        }

        // 解析 SpEL 表达式
        StandardEvaluationContext context = new StandardEvaluationContext();

        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        // 设置描述：始终用 SpEL 表达式
        if (!auditLog.description().isEmpty()) {
            try {
                String desc = parser.parseExpression(auditLog.description()).getValue(context, String.class);
                auditLogBuilder.description(desc);
            } catch (Exception e) {
                auditLogBuilder.description(auditLog.description());
            }
        }

        // 设置目标对象信息
        auditLogBuilder.targetType(auditLog.targetType());

        if (!auditLog.targetId().isEmpty()) {
            try {
                String targetId = parser.parseExpression(auditLog.targetId()).getValue(context, String.class);
                auditLogBuilder.targetId(targetId);
            } catch (Exception e) {
                log.warn("Failed to parse targetId expression: {}", auditLog.targetId());
            }
        }

        if (!auditLog.targetName().isEmpty()) {
            try {
                String targetName = parser.parseExpression(auditLog.targetName()).getValue(context, String.class);
                auditLogBuilder.targetName(targetName);
            } catch (Exception e) {
                log.warn("Failed to parse targetName expression: {}", auditLog.targetName());
            }
        }

        // 执行目标方法
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            auditLogBuilder.result("SUCCESS");

            // 记录返回值
            if (auditLog.recordResult() && result != null) {
                try {
                    auditLogBuilder.newData(objectMapper.writeValueAsString(result));
                } catch (Exception e) {
                    log.warn("Failed to record result", e);
                }
            }

        } catch (Throwable t) {
            error = t;
            auditLogBuilder.result("FAIL");
            auditLogBuilder.errorMsg(t.getMessage());
        }

        // 计算耗时
        long costTime = ChronoUnit.MILLIS.between(startDateTime, LocalDateTime.now());
        auditLogBuilder.costTime(costTime);

        // 保存审计日志
        try {
            AuditSysLog logEntity = auditLogBuilder.build();
            auditLogRepository.save(logEntity);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }

        // 如果有异常，继续抛出
        if (error != null) {
            throw error;
        }

        return result;
    }

    /**
     * 从请求中读取 body（兼容 CachedBodyHttpServletRequest）
     */
    private String getCachedBody(HttpServletRequest request) {
        try {
            // 先尝试 CachedBodyHttpServletRequest 的 getInputStream()（已缓存）
            if (request.getContentLength() > 0) {
                ByteArrayInputStream bais = new ByteArrayInputStream(
                        request.getInputStream().readAllBytes());
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(bais, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[1024];
                    int len;
                    while ((len = reader.read(buf)) != -1) {
                        sb.append(buf, 0, len);
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            log.debug("getCachedBody failed, fallback to parameterMap", e);
        }
        return null;
    }

    /**
     * 提取方法参数中标注了 @PathVariable 的参数
     */
    private Map<String, Object> extractPathParams(ProceedingJoinPoint joinPoint, MethodSignature signature,
                                                   String[] paramNames, Object[] args) {
        Map<String, Object> pathParams = new HashMap<>();
        java.lang.reflect.Parameter[] parameters = signature.getMethod().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            PathVariable pv = parameters[i].getAnnotation(PathVariable.class);
            if (pv != null && args[i] != null) {
                String name = pv.value().isEmpty() ? paramNames[i] : pv.value();
                pathParams.put(name, args[i]);
            }
        }
        return pathParams;
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Forwarded-For");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Ali-Cdn-Real-Ip");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("CDN-Src-Ip");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理 X-Forwarded-For 多个 IP 的情况
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
