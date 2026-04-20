package com.integration.config.exception;

import com.integration.config.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常
 *
 * 示例
 * <pre>
 * // 抛出预定义枚举
 * throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
 *
 * // 抛出 + 条件断言
 * BusinessException.throwIf(condition, ErrorCode.NOT_FOUND, "detail");
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMsg());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    /** 兼容旧 int code 构造器（@deprecated） */
    @Deprecated
    public BusinessException(int code, String messageKey, String message) {
        super(message);
        this.errorCode = ErrorCode.fromCode(code);
    }

    /** 兼容旧 int code 构造器（@deprecated） */
    @Deprecated
    public BusinessException(int code, String message) {
        this(code, null, message);
    }
}
