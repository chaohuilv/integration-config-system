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

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "API_CODE", nullable = false, length = 50)
    private String apiCode;

    @Column(name = "REQUEST_PARAMS", columnDefinition = "TEXT")
    private String requestParams;

    @Column(name = "REQUEST_HEADERS", columnDefinition = "TEXT")
    private String requestHeaders;

    @Column(name = "REQUEST_BODY", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "RESPONSE_STATUS")
    private Integer responseStatus;

    @Column(name = "RESPONSE_DATA", columnDefinition = "TEXT")
    private String responseData;

    @Column(name = "SUCCESS")
    private Boolean success;

    @Column(name = "ERROR_MESSAGE", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "COST_TIME")
    private Long costTime;

    @Column(name = "INVOKE_TIME", nullable = false)
    private LocalDateTime invokeTime;

    @Column(name = "CLIENT_IP", length = 50)
    private String clientIp;

    @Column(name = "TRACE_ID", length = 64)
    private String traceId;

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
