package com.integration.config.entity.config;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色实体
 * 预置角色：ADMIN（管理员）、DEVELOPER（开发者）、READONLY（只读）
 */
@Entity
@Table(name = "SYS_ROLE", indexes = {
    @Index(name = "IDX_ROLE_CODE", columnList = "CODE", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    /** 主键ID */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 角色编码，唯一标识 */
    @Column(name = "CODE", nullable = false, unique = true, length = 50)
    private String code;

    /** 角色名称 */
    @Column(name = "NAME", nullable = false, length = 50)
    private String name;

    /** 角色描述 */
    @Column(name = "DESCRIPTION", length = 200)
    private String description;

    /** 状态：ACTIVE-启用 / INACTIVE-禁用 */
    @Column(name = "STATUS", length = 20)
    private String status;

    /** 是否为系统预置角色（不可删除） */
    @Column(name = "IS_SYSTEM")
    private Boolean isSystem;

    /** 排序号 */
    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    /** 创建时间 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = SnowflakeUtil.nextId();
        }
        createdAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
        if (isSystem == null) isSystem = false;
        if (sortOrder == null) sortOrder = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
