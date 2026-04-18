package com.integration.config.entity.config;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 接口-角色关联实体
 * 控制哪些角色可以调用哪些接口
 */
@Entity
@Table(name = "API_ROLE", indexes = {
    @Index(name = "IDX_API_ROLE_API", columnList = "API_ID"),
    @Index(name = "IDX_API_ROLE_ROLE", columnList = "ROLE_ID")
}, uniqueConstraints = {
    @UniqueConstraint(name = "UK_API_ROLE", columnNames = {"API_ID", "ROLE_ID"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiRole {

    /** 主键ID */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 接口配置ID */
    @Column(name = "API_ID", nullable = false)
    private Long apiId;

    /** 角色ID */
    @Column(name = "ROLE_ID", nullable = false)
    private Long roleId;

    /** 创建时间 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 创建人ID */
    @Column(name = "CREATED_BY")
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = SnowflakeUtil.nextId();
        }
        createdAt = LocalDateTime.now();
    }
}
