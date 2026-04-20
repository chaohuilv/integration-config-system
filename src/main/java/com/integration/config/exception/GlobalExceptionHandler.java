package com.integration.config.exception;

import com.integration.config.enums.ErrorCode;
import com.integration.config.vo.ResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局统一异常处理
 *
 * 设计原则：
 * <ul>
 *   <li>所有异常统一由 handleBusiness 转换为 {@link ResultVO}</li>
 *   <li>各 handler 只负责抛出 BusinessException，handleBusiness 统一处理</li>
 *   <li>已知业务异常不打印堆栈，未知异常打印完整堆栈</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ==================== 参数/校验失败 (400) ====================

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[参数错误] {}", e.getMessage());
        throw new BusinessException(ErrorCode.INVALID_PARAM, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("validation failed");
        log.warn("[校验失败] {}", detail);
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, detail);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleBind(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("bind failed");
        log.warn("[绑定失败] {}", detail);
        throw new BusinessException(ErrorCode.BIND_FAILED, detail);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String detail = "Parameter '" + e.getName() + "' type mismatch, expected " + e.getRequiredType().getSimpleName();
        log.warn("[类型不匹配] {}", detail);
        throw new BusinessException(ErrorCode.INVALID_PARAM, detail);
    }

    // ==================== 业务异常 ====================

    @ExceptionHandler(BusinessException.class)
    public ResultVO<Void> handleBusiness(BusinessException e) {
        log.warn("[业务异常] code={}, msg={}", e.getErrorCode().getCode(), e.getMessage());
        return ResultVO.error(e.getErrorCode(), e.getMessage());
    }

    // ==================== 服务端异常 (500) ====================

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void handleGeneral(Exception e) {
        // 已知业务异常不打印堆栈
        if (e instanceof BusinessException
                || e instanceof IllegalArgumentException
                || e instanceof IllegalStateException) {
            log.warn("[系统异常-已知] {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
        log.error("[系统异常-未知]", e);
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "服务器内部错误");
    }

    // ==================== 便捷构造方法 ====================

    /** 快捷抛出业务异常（直接 throw，省去 new） */
    public static void throwBiz(ErrorCode errorCode, String detail) {
        throw new BusinessException(errorCode, detail);
    }

    /** 快捷抛出业务异常（使用默认消息） */
    public static void throwBiz(ErrorCode errorCode) {
        throw new BusinessException(errorCode);
    }
}
