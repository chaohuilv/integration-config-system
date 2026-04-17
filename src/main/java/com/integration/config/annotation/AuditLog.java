package com.integration.config.annotation;

import java.lang.annotation.*;

/**
 * 审计日志注解
 * 标记需要记录审计日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 操作类型
     * CREATE/UPDATE/DELETE/LOGIN/LOGOUT/QUERY/IMPORT/EXPORT/ENABLE/DISABLE/OTHER
     */
    String operateType();

    /**
     * 操作模块
     */
    String module() default "";

    /**
     * 操作描述（支持 SpEL 表达式，如：#userCode + '登录系统'）
     */
    String description() default "";

    /**
     * 目标对象类型
     */
    String targetType() default "";

    /**
     * 目标对象ID表达式（如：#id 或 #dto.code）
     */
    String targetId() default "";

    /**
     * 目标对象名称表达式
     */
    String targetName() default "";

    /**
     * 是否记录请求参数
     */
    boolean recordParams() default false;

    /**
     * 是否记录返回值
     */
    boolean recordResult() default true;
}
