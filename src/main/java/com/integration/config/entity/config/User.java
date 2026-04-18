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
 * 用户实体（Config 数据库）
 */
@Entity
@Table(name = "SYS_USER", indexes = {
    @Index(name = "IDX_USER_CODE", columnList = "USER_CODE", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 用户编码，唯一，用于登录 */
    @Column(name = "USER_CODE", nullable = false, unique = true, length = 50)
    private String userCode;

    /** 用户名称，用于显示 */
    @Column(name = "USERNAME", nullable = false, length = 50)
    private String username;

    /** 密码（加密存储） */
    @Column(name = "PASSWORD", nullable = false, length = 100)
    private String password;

    /** 显示名称 */
    @Column(name = "DISPLAY_NAME", length = 50)
    private String displayName;

    /** 邮箱 */
    @Column(name = "EMAIL", length = 100)
    private String email;

    /** 手机号 */
    @Column(name = "PHONE", length = 20)
    private String phone;

    /** 状态：ACTIVE-启用 / INACTIVE-禁用 */
    @Column(name = "STATUS", length = 20)
    private String status;

    /** 最后登录时间 */
    @Column(name = "LAST_LOGIN_TIME")
    private LocalDateTime lastLoginTime;

    /** 最后登录IP */
    @Column(name = "LAST_LOGIN_IP", length = 50)
    private String lastLoginIp;

    /** 创建时间 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** 创建人ID */
    @Column(name = "CREATED_BY")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = SnowflakeUtil.nextId();
        }
        createdAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
