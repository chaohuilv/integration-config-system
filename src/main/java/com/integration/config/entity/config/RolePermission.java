package com.integration.config.entity.config;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色权限关联实体
 */
@Entity
@Table(name = "SYS_ROLE_PERMISSION", indexes = {
    @Index(name = "IDX_RP_ROLE", columnList = "ROLE_ID"),
    @Index(name = "IDX_RP_PERM", columnList = "PERMISSION_ID")
}, uniqueConstraints = {
    @UniqueConstraint(name = "UK_ROLE_PERM", columnNames = {"ROLE_ID", "PERMISSION_ID"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "ROLE_ID", nullable = false)
    private Long roleId;

    @Column(name = "PERMISSION_ID", nullable = false)
    private Long permissionId;

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
