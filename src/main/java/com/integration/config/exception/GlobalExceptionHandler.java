package com.integration.config.exception;

import com.integration.config.enums.ErrorCode;
import com.integration.config.vo.ResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ResultVO<Void>> handleBusiness(BusinessException e) {
        log.warn("[业务异常] code={}, msg={}", e.getErrorCode().getCode(), e.getMessage());
        HttpStatus status = e.getErrorCode().getCode() == 429
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ResultVO.error(e.getErrorCode(), e.getMessage()));
    }

    // ==================== 数据库约束异常 (优雅报错) ====================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResultVO<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        String userMsg = parseDbConstraintMessage(e);
        ErrorCode errorCode = resolveDbErrorCode(e);
        log.warn("[数据库约束异常] code={} msg={}", errorCode.getCode(), userMsg);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ResultVO.error(errorCode, userMsg));
    }

    /**
     * 解析数据库约束异常为用户友好消息
     *
     * 支持的数据库：MySQL / H2 / PostgreSQL
     *
     * 识别规则：
     *   - 字段长度超限：Data too long / value too long / String data right truncation
     *   - 唯一约束冲突：Duplicate entry / unique constraint / uk_xxx
     *   - 非空约束违反：cannot be null / NULL not allowed
     */
    private static String parseDbConstraintMessage(DataIntegrityViolationException e) {
        String msg = extractRootMessage(e);
        if (msg == null) {
            return "数据保存失败，请检查输入内容";
        }
        String lower = msg.toLowerCase();

        // --- 字段长度超限 ---
        if (lower.contains("data too long") || lower.contains("value too long")
                || lower.contains("string data right truncation")
                || lower.contains("stringdata right truncation")
                || lower.contains("could not resize")
                || lower.contains("data exception") && lower.contains("length")) {
            // H2: Value too long for column "PHONE CHARACTER VARYING(20)"
            String fieldName = extractH2Column(msg, "for column");
            if (fieldName == null) {
                fieldName = extractFieldName(msg, "column", "'");
            }
            return fieldName != null
                    ? "字段 [" + fieldName + "] 的值过长，请缩短后重试"
                    : "输入内容过长，请检查各字段长度";
        }

        // --- 唯一约束冲突 ---
        if (lower.contains("duplicate entry") || lower.contains("unique constraint")
                || lower.contains("duplicate key") || lower.contains("violates unique constraint")
                || lower.contains("already exists") && lower.contains("unique")) {
            String fieldName = extractFieldName(msg, "for key", "'");
            if (fieldName == null) {
                fieldName = extractFieldName(msg, "unique constraint", "\"");
            }
            return fieldName != null
                    ? "字段 [" + fieldName + "] 的值已存在，请修改后重试"
                    : "数据重复，该值已被占用";
        }

        // --- 非空约束违反 ---
        if (lower.contains("cannot be null") || lower.contains("null not allowed")
                || lower.contains("does not allow nulls") || lower.contains("may not be null")) {
            String fieldName = extractFieldName(msg, "column", "'");
            return fieldName != null
                    ? "字段 [" + fieldName + "] 不能为空"
                    : "必填字段不能为空";
        }

        // --- 外键约束 ---
        if (lower.contains("foreign key constraint") || lower.contains("integrity constraint")
                || lower.contains("cannot add or update") && lower.contains("foreign key")) {
            return "关联数据不存在，请检查关联字段";
        }

        return "数据保存失败，请检查输入内容";
    }

    /**
     * 根据异常特征判断 ErrorCode
     */
    private static ErrorCode resolveDbErrorCode(DataIntegrityViolationException e) {
        String msg = extractRootMessage(e);
        if (msg == null) return ErrorCode.DB_CONSTRAINT_VIOLATION;
        String lower = msg.toLowerCase();

        if (lower.contains("data too long") || lower.contains("value too long")
                || lower.contains("string data right truncation")
                || lower.contains("stringdata right truncation")) {
            return ErrorCode.DB_FIELD_TOO_LONG;
        }
        if (lower.contains("duplicate entry") || lower.contains("unique constraint")
                || lower.contains("duplicate key")) {
            return ErrorCode.DB_UNIQUE_VIOLATION;
        }
        if (lower.contains("cannot be null") || lower.contains("null not allowed")
                || lower.contains("does not allow nulls")) {
            return ErrorCode.DB_NOT_NULL_VIOLATION;
        }
        return ErrorCode.DB_CONSTRAINT_VIOLATION;
    }

    /**
     * 提取根异常消息（跳过 Spring Data 包装层）
     */
    private static String extractRootMessage(Throwable e) {
        Throwable current = e;
        String deepest = e.getMessage();
        while (current != null) {
            if (current.getMessage() != null) {
                deepest = current.getMessage();
            }
            current = current.getCause();
        }
        return deepest;
    }

    /**
     * H2 专用：从异常消息中提取列名（H2 用双引号且可能带类型）
     * 例: for column "PHONE CHARACTER VARYING(20)" → PHONE
     */
    private static String extractH2Column(String msg, String marker) {
        int idx = msg.toLowerCase().indexOf(marker.toLowerCase());
        if (idx < 0) return null;
        int start = idx + marker.length();
        int q1 = msg.indexOf('"', start);
        if (q1 < 0) return null;
        int q2 = msg.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        String raw = msg.substring(q1 + 1, q2).trim();
        // 去掉类型信息："PHONE CHARACTER VARYING(20)" → "PHONE"
        int spaceIdx = raw.indexOf(' ');
        if (spaceIdx > 0) {
            raw = raw.substring(0, spaceIdx);
        }
        return friendlyColumnName(raw);
    }

    /**
     * 从异常消息中提取字段名
     *
     * @param msg      原始消息
     * @param marker   标记词（如 "for key"）
     * @param quote    引号字符（如 '\'' 或 '"'）
     * @return 字段名或 null
     */
    private static String extractFieldName(String msg, String marker, String quote) {
        int idx = msg.toLowerCase().indexOf(marker.toLowerCase());
        if (idx < 0) return null;
        int start = idx + marker.length();
        int q1 = msg.indexOf(quote, start);
        if (q1 < 0) return null;
        int q2 = msg.indexOf(quote, q1 + 1);
        if (q2 < 0) return null;
        String raw = msg.substring(q1 + 1, q2);
        // 数据库列名通常是大写下划线格式，转为友好名称
        return friendlyColumnName(raw);
    }

    /**
     * 数据库列名转友好名称
     * USER_CODE → 用户编码, PHONE → 手机号, AUTH_INFO → 认证信息
     * 未知列名直接返回大写原文
     */
    private static String friendlyColumnName(String columnName) {
        if (columnName == null || columnName.isBlank()) return columnName;
        // 已知的友好名称映射
        switch (columnName.toUpperCase()) {
            // ApiConfig
            case "API_CODE":   return "接口编码";
            case "API_NAME":   return "接口名称";
            case "URL":        return "请求地址";
            case "HEADERS":    return "请求头";
            case "REQUEST_BODY": return "请求体";
            case "AUTH_INFO":  return "认证信息";
            case "TOKEN":      return "Token";
            case "API_KEY":    return "API Key";
            // User
            case "USER_CODE":  return "用户编码";
            case "USERNAME":   return "用户名称";
            case "EMAIL":      return "邮箱";
            case "PHONE":      return "手机号";
            case "PASSWORD":   return "密码";
            case "DISPLAY_NAME": return "显示名称";
            // Role
            case "ROLE_NAME":  return "角色名称";
            case "ROLE_CODE":  return "角色编码";
            // Scenario
            case "SCENARIO_NAME": return "场景名称";
            case "SCENARIO_CODE": return "场景编码";
            case "GROUP_NAME": return "分组名称";
            // Environment
            case "ENV_NAME":   return "环境名称";
            case "ENV_CODE":   return "环境编码";
            case "BASE_URL":   return "基础地址";
            case "API_KEY_VAL": return "API Key";
            // TokenCache
            case "TOKEN_API_CODE": return "Token 接口编码";
            default: break;
        }
        // 对未映射的列名，做简单的下划线拆分：LAST_LOGIN_IP → LAST LOGIN IP
        return columnName.replace("_", " ");
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
