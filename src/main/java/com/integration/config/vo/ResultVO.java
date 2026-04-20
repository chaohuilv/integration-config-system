package com.integration.config.vo;

import com.integration.config.enums.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应结构
 *
 * 错误通过 throw BusinessException.of(ErrorCode.xxx, detail) 抛出，
 * 由 GlobalExceptionHandler 统一转换为 Result 响应。
 *
 * @param <T> data 类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultVO<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** HTTP 状态码 */
    private int code;
    /** 英文消息（用于日志/调试） */
    private String message;
    /** i18n key（前端用此查 Messages.get()） */
    private String messageKey;
    /** 响应数据 */
    private T data;

    // ==================== 成功 ====================

    /** 无数据的成功响应 */
    public static <T> ResultVO<T> success() {
        return ResultVO.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getDefaultMsg())
                .messageKey(ErrorCode.SUCCESS.getMessageKey())
                .build();
    }

    /** 带数据的成功响应 */
    public static <T> ResultVO<T> success(T data) {
        return ResultVO.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getDefaultMsg())
                .messageKey(ErrorCode.SUCCESS.getMessageKey())
                .data(data)
                .build();
    }

    /**
     * 带 i18n messageKey 的成功响应
     *
     * @param messageKey i18n key，前端展示用
     * @param data       响应数据
     */
    public static <T> ResultVO<T> success(String messageKey, T data) {
        return ResultVO.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(messageKey)
                .messageKey(messageKey)
                .data(data)
                .build();
    }

    /**
     * 带 i18n messageKey，无数据的成功响应
     *
     * @param messageKey i18n key
     */
    public static ResultVO<Void> success(String messageKey) {
        return ResultVO.<Void>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(messageKey)
                .messageKey(messageKey)
                .build();
    }

    // ==================== 错误（仅供 GlobalExceptionHandler 使用） ====================

    /**
     * 从 ErrorCode 构建错误响应（GlobalExceptionHandler 专用）
     */
    public static <T> ResultVO<T> error(ErrorCode errorCode, String detail) {
        return ResultVO.<T>builder()
                .code(errorCode.getCode())
                .messageKey(errorCode.getMessageKey())
                .message(detail)
                .build();
    }

    /**
     * 从 ErrorCode 构建错误响应，无 detail（GlobalExceptionHandler 专用）
     */
    public static <T> ResultVO<T> error(ErrorCode errorCode) {
        return ResultVO.<T>builder()
                .code(errorCode.getCode())
                .messageKey(errorCode.getMessageKey())
                .message(errorCode.getDefaultMsg())
                .build();
    }
}
