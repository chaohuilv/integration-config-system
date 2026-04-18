package com.integration.config.entity.config;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色菜单关联实体
 */
@Entity
@Table(name = "SYS_ROLE_MENU", indexes = {
    @Index(name = "IDX_RM_ROLE", columnList = "ROLE_ID"),
    @Index(name = "IDX_RM_MENU", columnList = "MENU_ID")
}, uniqueConstraints = {
    @UniqueConstraint(name = "UK_ROLE_MENU", columnNames = {"ROLE_ID", "MENU_ID"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleMenu {

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "ROLE_ID", nullable = false)
    private Long roleId;

    @Column(name = "MENU_ID", nullable = false)
    private Long menuId;

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
