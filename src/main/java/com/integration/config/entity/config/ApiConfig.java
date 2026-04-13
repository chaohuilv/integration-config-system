package com.integration.config.entity.config;

import com.integration.config.enums.ContentType;
import com.integration.config.enums.HttpMethod;
import com.integration.config.enums.Status;
import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 接口配置实体（Config 数据库）
 */
@Entity
@Table(name = "API_CONFIG")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiConfig {

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "CODE", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "METHOD", nullable = false, length = 10)
    private HttpMethod method;

    @Column(name = "URL", nullable = false, length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "CONTENT_TYPE", length = 50)
    private ContentType contentType;

    @Column(name = "HEADERS", columnDefinition = "TEXT")
    private String headers;

    @Column(name = "REQUEST_PARAMS", columnDefinition = "TEXT")
    private String requestParams;

    @Column(name = "REQUEST_BODY", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "AUTH_TYPE", length = 20)
    private String authType;

    @Column(name = "AUTH_INFO", length = 500)
    private String authInfo;

    @Column(name = "TIMEOUT")
    private Integer timeout;

    @Column(name = "RETRY_COUNT")
    private Integer retryCount;

    @Column(name = "ENABLE_CACHE")
    private Boolean enableCache;

    @Column(name = "CACHE_TIME")
    private Integer cacheTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private Status status;

    /** 分组名称 */
    @Column(name = "GROUP_NAME", length = 100)
    private String groupName;

    /** 动态Token - 是否启用 */
    @Column(name = "ENABLE_DYNAMIC_TOKEN")
    private Boolean enableDynamicToken;

    /** 动态Token - 获取Token的接口编码 */
    @Column(name = "TOKEN_API_CODE", length = 50)
    private String tokenApiCode;

    /** 动态Token - Token在响应中的提取路径（JSONPath） */
    @Column(name = "TOKEN_EXTRACT_PATH", length = 200)
    private String tokenExtractPath;

    /** 动态Token - Token传递位置：header / url / body */
    @Column(name = "TOKEN_POSITION", length = 20)
    private String tokenPosition;

    /** 动态Token - Token参数名 */
    @Column(name = "TOKEN_PARAM_NAME", length = 100)
    private String tokenParamName;

    /** 动态Token - Token前缀（如 Bearer ） */
    @Column(name = "TOKEN_PREFIX", length = 50)
    private String tokenPrefix;

    /** 动态Token - Token缓存时间（秒） */
    @Column(name = "TOKEN_CACHE_TIME")
    private Integer tokenCacheTime;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = SnowflakeUtil.nextId();
        }
        createdAt = LocalDateTime.now();
        if (status == null) status = Status.ACTIVE;
        if (timeout == null) timeout = 30000;
        if (retryCount == null) retryCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
