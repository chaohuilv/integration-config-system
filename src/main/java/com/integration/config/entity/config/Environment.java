package com.integration.config.entity.config;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 环境配置实体
 * 绑定到接口配置的分组（groupName）
 */
@Entity
@Table(name = "t_environment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 系统名称（对应接口配置的 groupName）
     */
    @Column(name = "system_name", nullable = false, length = 100)
    private String systemName;

    /**
     * 环境名称：DEV/TEST/PRE/PROD
     */
    @Column(name = "env_name", nullable = false, length = 20)
    private String envName;

    /**
     * 基础URL
     */
    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    /**
     * 描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 状态：ACTIVE 启用，INACTIVE 禁用
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * 是否启用域名替换：true 启用，false 禁用
     * 启用时，接口调用会自动用此环境的 baseUrl 替换原始 URL 的域名
     */
    @Column(name = "url_replace", nullable = false)
    @Builder.Default
    private Boolean urlReplace = true;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}