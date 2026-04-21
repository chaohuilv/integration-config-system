package com.integration.config.entity.log;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 接口调用日志实体（Log 数据库）
 */
@Entity
@Table(name = "INVOKE_LOG", indexes = {
    @Index(name = "IDX_API_CODE", columnList = "API_CODE"),
    @Index(name = "IDX_INVOKE_TIME", columnList = "INVOKE_TIME")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeLog {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 调用的接口编码 */
    @Column(name = "API_CODE", nullable = false, length = 50)
    private String apiCode;

    /** 调用时的完整请求 URL（含域名替换后的最终地址） */
    @Column(name = "REQUEST_URL", columnDefinition = "TEXT")
    private String requestUrl;

    /** 调用时传入的URL参数，JSON格式 */
    @Column(name = "REQUEST_PARAMS", columnDefinition = "TEXT")
    private String requestParams;

    /** 调用时实际发送的请求头，JSON格式 */
    @Column(name = "REQUEST_HEADERS", columnDefinition = "TEXT")
    private String requestHeaders;

    /** 调用时实际发送的请求体内容 */
    @Column(name = "REQUEST_BODY", columnDefinition = "TEXT")
    private String requestBody;

    /** 目标接口返回的HTTP状态码 */
    @Column(name = "RESPONSE_STATUS")
    private Integer responseStatus;

    /** 目标接口返回的响应数据（截断存储） */
    @Column(name = "RESPONSE_DATA", columnDefinition = "TEXT")
    private String responseData;

    /** 调用是否成功 */
    @Column(name = "SUCCESS")
    private Boolean success;

    /** 调用失败时的错误信息 */
    @Column(name = "ERROR_MESSAGE", columnDefinition = "TEXT")
    private String errorMessage;

    /** 调用耗时（毫秒） */
    @Column(name = "COST_TIME")
    private Long costTime;

    /** 调用时间 */
    @Column(name = "INVOKE_TIME", nullable = false)
    private LocalDateTime invokeTime;

    /** 调用方IP地址 */
    @Column(name = "CLIENT_IP", length = 50)
    private String clientIp;

    /** 链路追踪ID，用于关联请求 */
    @Column(name = "TRACE_ID", length = 64)
    private String traceId;

    /** 重试次数（0=首次调用，1=第1次重试，2=第2次重试...） */
    @Column(name = "RETRY_ATTEMPT")
    private Integer retryAttempt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = SnowflakeUtil.nextId();
        }
        if (invokeTime == null) {
            invokeTime = LocalDateTime.now();
        }
    }
}
