package com.integration.config.aspect;

import com.integration.config.annotation.RequirePermission;
import com.integration.config.enums.ErrorCode;
import com.integration.config.exception.BusinessException;
import com.integration.config.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 权限校验切面
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionAspect {

    private final RoleService roleService;

    @Around("@annotation(com.integration.config.annotation.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取当前请求
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法获取请求上下文");
        }

        HttpServletRequest request = attrs.getRequest();

        // 从 request attribute 获取用户ID（由 LoginFilter 设置）
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
        }

        // 获取注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        String permissionCode = annotation.value();

        // 检查权限
        if (!roleService.hasPermission(userId, permissionCode)) {
            log.warn("[PermissionAspect] 用户 {} 无权限: {}", userId, permissionCode);
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "无操作权限: " + permissionCode);
        }

        log.debug("[PermissionAspect] 用户 {} 权限校验通过: {}", userId, permissionCode);

        return joinPoint.proceed();
    }
}
