package com.integration.config.entity.config;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 接口名称，用于展示 */
    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    /** 接口唯一编码，调用时的唯一标识 */
    @Column(name = "CODE", nullable = false, unique = true, length = 50)
    private String code;

    /** 接口描述信息 */
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** HTTP请求方法：GET/POST/PUT/DELETE/PATCH */
    @Enumerated(EnumType.STRING)
    @Column(name = "METHOD", nullable = false, length = 10)
    private HttpMethod method;

    /** 目标接口URL地址，支持{{paramName}}占位符 */
    @Column(name = "URL", nullable = false, length = 500)
    private String url;

    /** 请求内容类型：JSON/FORM/XML/TEXT */
    @Enumerated(EnumType.STRING)
    @Column(name = "CONTENT_TYPE", length = 50)
    private ContentType contentType;

    /** 请求头配置，格式：Key: Value（每行一个） */
    @Column(name = "HEADERS", columnDefinition = "TEXT")
    private String headers;

    /** URL查询参数配置，JSON格式，例：{"page":1,"size":10} */
    @Column(name = "REQUEST_PARAMS", columnDefinition = "TEXT")
    private String requestParams;

    /** 请求体内容，支持{{paramName}}占位符替换 */
    @Column(name = "REQUEST_BODY", columnDefinition = "TEXT")
    private String requestBody;

    /** 认证类型：NONE/BASIC/BEARER/API_KEY/DYNAMIC */
    @Column(name = "AUTH_TYPE", length = 20)
    private String authType;

    /** 认证信息：
     * BASIC: base64(username:password)
     * BEARER: Token值
     * API_KEY: key值或key:value格式
     * DYNAMIC: 空（动态Token由下方字段控制）
     */
    @Column(name = "AUTH_INFO", length = 500)
    private String authInfo;

    /** 请求超时时间（毫秒），默认30000 */
    @Column(name = "TIMEOUT")
    private Integer timeout;

    /** 失败重试次数，默认0不重试 */
    @Column(name = "RETRY_COUNT")
    private Integer retryCount;

    /** 是否启用响应缓存 */
    @Column(name = "ENABLE_CACHE")
    private Boolean enableCache;

    /** 缓存有效期（秒） */
    @Column(name = "CACHE_TIME")
    private Integer cacheTime;

    /** 状态：ACTIVE-启用 / INACTIVE-禁用 */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private Status status;

    /** 分组名称，用于接口分类管理 */
    @Column(name = "GROUP_NAME", length = 100)
    private String groupName;

    // ==================== 动态Token相关字段 ====================

    /** 是否启用动态Token（Token过期需定期刷新） */
    @Column(name = "ENABLE_DYNAMIC_TOKEN")
    private Boolean enableDynamicToken;

    /** 获取Token的接口编码（需在系统中已配置） */
    @Column(name = "TOKEN_API_CODE", length = 50)
    private String tokenApiCode;

    /** Token提取路径，支持JSONPath，例：$.data.accessToken */
    @Column(name = "TOKEN_EXTRACT_PATH", length = 200)
    private String tokenExtractPath;

    /** Token传递位置：header-请求头 / url-URL参数 / body-请求体 */
    @Column(name = "TOKEN_POSITION", length = 20)
    private String tokenPosition;

    /** Token参数名：
     * header模式：请求头名称，如Authorization
     * url模式：URL参数名称，如access_token
     */
    @Column(name = "TOKEN_PARAM_NAME", length = 100)
    private String tokenParamName;

    /** Token前缀，如Bearer（会自动拼接：Bearer + Token值） */
    @Column(name = "TOKEN_PREFIX", length = 50)
    private String tokenPrefix;

    /** Token缓存时间（秒），缓存过期后自动重新获取 */
    @Column(name = "TOKEN_CACHE_TIME")
    private Integer tokenCacheTime;

    // ==================== 审计字段 ====================

    /** 记录创建时间 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 记录最后更新时间 */
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** 创建人ID */
    @Column(name = "CREATED_BY_ID")
    private Long createdById;

    /** 创建人名称 */
    @Column(name = "CREATED_BY_NAME", length = 50)
    private String createdByName;

    /** 更新人ID */
    @Column(name = "UPDATED_BY_ID")
    private Long updatedById;

    /** 更新人名称 */
    @Column(name = "UPDATED_BY_NAME", length = 50)
    private String updatedByName;

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
