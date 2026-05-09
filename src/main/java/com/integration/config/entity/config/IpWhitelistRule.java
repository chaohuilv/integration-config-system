package com.integration.config.entity.config;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * IP 白名单规则实体
 * 支持全局规则和按接口级别的 IP 访问控制
 */
@Entity
@Table(name = "IP_WHITELIST_RULE", indexes = {
    @Index(name = "IDX_IP_RULE_CODE", columnList = "RULE_CODE", unique = true),
    @Index(name = "IDX_IP_RULE_STATUS", columnList = "STATUS"),
    @Index(name = "IDX_IP_RULE_SCOPE", columnList = "SCOPE")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpWhitelistRule {

    /** 主键 ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 规则编码，唯一标识 */
    @Column(name = "RULE_CODE", nullable = false, unique = true, length = 50)
    private String ruleCode;

    /** 规则名称 */
    @Column(name = "RULE_NAME", nullable = false, length = 100)
    private String ruleName;

    /** 规则描述 */
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** 作用范围：GLOBAL-全局 / API-指定接口 */
    @Column(name = "SCOPE", nullable = false, length = 20)
    private String scope;

    /** 关联接口编码（scope=API 时必填，多个用逗号分隔） */
    @Column(name = "API_CODES", columnDefinition = "TEXT")
    private String apiCodes;

    /**
     * IP 列表（支持格式）：
     * <ul>
     *   <li>单个 IP：192.168.1.100</li>
     *   <li>CIDR 网段：10.0.0.0/8, 172.16.0.0/16</li>
     *   <li>IP 段：192.168.1.1-192.168.1.255</li>
     *   <li>特殊标识：localhost, 127.0.0.1</li>
     * </ul>
     * 多个规则用换行或逗号分隔
     */
    @Column(name = "IP_LIST", columnDefinition = "TEXT", nullable = false)
    private String ipList;

    /** 匹配模式：WHITELIST-白名单（仅允许）/ BLACKLIST-黑名单（拒绝） */
    @Column(name = "MATCH_MODE", length = 20)
    @Builder.Default
    private String matchMode = "WHITELIST";

    /** 状态：ACTIVE-启用 / INACTIVE-停用 */
    @Column(name = "STATUS", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /** 优先级（数值越小优先级越高），默认 100 */
    @Column(name = "PRIORITY")
    @Builder.Default
    private Integer priority = 100;

    /** 创建时间 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** 创建人 */
    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = SnowflakeUtil.nextId();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
        if (matchMode == null) matchMode = "WHITELIST";
        if (priority == null) priority = 100;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
