package com.integration.config.entity.config;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户-角色关联实体
 */
@Entity
@Table(name = "SYS_USER_ROLE", indexes = {
    @Index(name = "IDX_USER_ROLE_USER", columnList = "USER_ID"),
    @Index(name = "IDX_USER_ROLE_ROLE", columnList = "ROLE_ID")
}, uniqueConstraints = {
    @UniqueConstraint(name = "UK_USER_ROLE", columnNames = {"USER_ID", "ROLE_ID"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

    /** 主键ID */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 用户ID */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 角色ID */
    @Column(name = "ROLE_ID", nullable = false)
    private Long roleId;

    /** 创建时间 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = SnowflakeUtil.nextId();
        }
        createdAt = LocalDateTime.now();
    }
}
