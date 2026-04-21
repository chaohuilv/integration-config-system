package com.integration.config.enums;

import lombok.Getter;

/**
 * 统一异常码定义（枚举形式）
 *
 * 码段规划：
 *   200     成功
 *   400     参数/校验类
 *   401     未认证（登录/token缺失）
 *   403     无权限
 *   410     认证失败
 *   420     权限不足
 *   430     资源不存在/冲突
 *   500     服务端异常
 *
 * 每个枚举项提供三个属性：
 *   code          HTTP/业务码（int，向后兼容 ErrorCodes 常量）
 *   messageKey    i18n key（前端按此查 Messages.get()）
 *   defaultMsg    默认英文消息（日志/fallback）
 */
@Getter
public enum ErrorCode {

    // ========== 成功 (200) ==========
    SUCCESS(200, "OPERATION_SUCCESS", "Operation successful"),

    // ========== 参数/校验失败 (400) ==========
    /** 通用参数错误 */
    INVALID_PARAM(400, "INVALID_PARAM", "Invalid parameter"),
    /** 参数校验失败（如 @Valid 注解触发） */
    VALIDATION_FAILED(400, "VALIDATION_FAILED", "Validation failed"),
    /** 参数绑定失败 */
    BIND_FAILED(400, "BIND_FAILED", "Binding failed"),

    // ========== 未认证 (401) ==========
    /** 未登录 / Token 缺失 */
    UNAUTHORIZED(401, "UNAUTHORIZED", "Unauthorized"),

    // ========== 无权限 (403) ==========
    /** 无权限访问 */
    FORBIDDEN(403, "FORBIDDEN", "Access denied"),

    // ========== 认证失败 (410) ==========
    /** 登录失败 */
    AUTH_FAILED(410, "AUTH_FAILED", "Authentication failed"),
    /** Token 缺失 */
    TOKEN_MISSING(411, "TOKEN_MISSING", "Token is missing"),
    /** Token 无效/过期 */
    TOKEN_INVALID(412, "TOKEN_INVALID", "Token is invalid or expired"),
    /** 账号已禁用 */
    ACCOUNT_DISABLED(413, "ACCOUNT_DISABLED", "Account is disabled"),

    // ========== 权限不足 (420) ==========
    /** 接口访问被禁止 */
    API_FORBIDDEN(420, "API_FORBIDDEN", "API access forbidden"),
    /** 缺少操作权限 */
    PERMISSION_DENIED(421, "PERMISSION_DENIED", "Permission denied"),
    /** 系统角色禁止操作 */
    SYSTEM_ROLE_PROTECTED(422, "SYSTEM_ROLE_PROTECTED", "System role is protected"),

    // ========== 资源相关 (430) ==========
    /** 资源不存在 */
    NOT_FOUND(430, "NOT_FOUND", "Resource not found"),
    /** 资源已存在（重复） */
    ALREADY_EXISTS(431, "ALREADY_EXISTS", "Resource already exists"),
    /** 版本冲突 */
    VERSION_CONFLICT(432, "VERSION_CONFLICT", "Version conflict"),
    /** 前置条件不满足 */
    PRECONDITION_FAILED(433, "PRECONDITION_FAILED", "Precondition failed"),

    // ========== 频率限制 (429) ==========
    /** 请求频率超限 */
    TOO_MANY_REQUESTS(429, "TOO_MANY_REQUESTS", "Too many requests"),

    // ========== 服务端异常 (500) ==========
    /** 系统内部错误 */
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "Internal server error"),
    /** 服务不可用 */
    SERVICE_UNAVAILABLE(501, "SERVICE_UNAVAILABLE", "Service unavailable");

    private final int code;
    /** i18n key，前端按此查 Messages.get() */
    private final String messageKey;
    /** 默认英文消息，用于日志 / 无 i18n 时的 fallback */
    private final String defaultMsg;

    ErrorCode(int code, String messageKey, String defaultMsg) {
        this.code = code;
        this.messageKey = messageKey;
        this.defaultMsg = defaultMsg;
    }

    /**
     * 通过 code 查找枚举，不存在返回 null
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }
}
