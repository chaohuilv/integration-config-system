package com.integration.config.entity.config;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * Mock 配置实体
 *
 * <p>用于配置本地 Mock 服务，提供 API 响应模拟，支持：
 * <ul>
 *   <li>请求匹配规则（路径、方法、请求头、参数、请求体）</li>
 *   <li>响应模板（支持动态变量如 {{randomInt}}、{{timestamp}}）</li>
 *   <li>模拟延迟、状态码、响应头</li>
 * </ul>
 */
@Data
@Entity
@Table(name = "MOCK_CONFIG",
       indexes = {
           @Index(name = "idx_mock_code", columnList = "CODE", unique = true),
           @Index(name = "idx_mock_path_method", columnList = "PATH, METHOD"),
           @Index(name = "idx_mock_group", columnList = "GROUP_NAME"),
           @Index(name = "idx_mock_enabled", columnList = "ENABLED")
       })
@Comment("Mock 配置表")
public class MockConfig {

    @Id
    @Column(name = "ID", nullable = false)
    @Comment("主键 ID")
    private Long id;

    /** Mock 编码（业务唯一标识） */
    @Column(name = "CODE", length = 50, nullable = false, unique = true)
    @Comment("Mock 编码")
    private String code;

    /** Mock 名称 */
    @Column(name = "NAME", length = 100, nullable = false)
    @Comment("Mock 名称")
    private String name;

    /** API 路径，支持路径变量如 /api/user/{id} */
    @Column(name = "PATH", length = 255, nullable = false)
    @Comment("API 路径")
    private String path;

    /** HTTP 方法：GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS */
    @Column(name = "METHOD", length = 10, nullable = false)
    @Comment("HTTP 方法")
    private String method;

    /** 响应状态码，默认 200 */
    @Column(name = "STATUS_CODE", nullable = false)
    @Comment("响应状态码")
    private Integer statusCode = 200;

    /** 响应体模板（支持模板语法） */
    @Column(name = "RESPONSE_BODY", columnDefinition = "TEXT")
    @Comment("响应体模板")
    private String responseBody;

    /** 响应头配置，JSON 格式：{"Content-Type": "application/json"} */
    @Column(name = "RESPONSE_HEADERS", columnDefinition = "TEXT")
    @Comment("响应头配置（JSON）")
    private String responseHeaders;

    /** 模拟延迟（毫秒），默认 0 */
    @Column(name = "DELAY_MS", nullable = false)
    @Comment("模拟延迟（毫秒）")
    private Integer delayMs = 0;

    /** 匹配规则配置，JSON 格式 */
    @Column(name = "MATCH_RULES", columnDefinition = "TEXT")
    @Comment("匹配规则配置（JSON）")
    private String matchRules;

    /** 是否启用 */
    @Column(name = "ENABLED", nullable = false)
    @Comment("是否启用")
    private Boolean enabled = true;

    /** 分组名称 */
    @Column(name = "GROUP_NAME", length = 50)
    @Comment("分组名称")
    private String groupName;

    /** 优先级（数值越小优先级越高），默认 100 */
    @Column(name = "PRIORITY", nullable = false)
    @Comment("优先级")
    private Integer priority = 100;

    /** 命中次数统计 */
    @Column(name = "HIT_COUNT", nullable = false)
    @Comment("命中次数")
    private Integer hitCount = 0;

    /** 最后命中时间 */
    @Column(name = "LAST_HIT_TIME")
    @Comment("最后命中时间")
    private LocalDateTime lastHitTime;

    /** 描述 */
    @Column(name = "DESCRIPTION", length = 500)
    @Comment("描述")
    private String description;

    /** 创建时间 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    @Comment("创建时间")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "UPDATED_AT", nullable = false)
    @Comment("更新时间")
    private LocalDateTime updatedAt;

    /** 创建人 */
    @Column(name = "CREATED_BY", length = 50)
    @Comment("创建人")
    private String createdBy;

    /** 更新人 */
    @Column(name = "UPDATED_BY", length = 50)
    @Comment("更新人")
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.id == null) this.id = SnowflakeUtil.nextId();
        if (this.statusCode == null) this.statusCode = 200;
        if (this.delayMs == null) this.delayMs = 0;
        if (this.enabled == null) this.enabled = true;
        if (this.priority == null) this.priority = 100;
        if (this.hitCount == null) this.hitCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
