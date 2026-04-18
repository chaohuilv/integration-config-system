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
 * 权限实体
 * 权限类型：MENU（菜单权限）、BUTTON（按钮权限）
 */
@Entity
@Table(name = "SYS_PERMISSION", indexes = {
    @Index(name = "IDX_PERM_CODE", columnList = "CODE", unique = true),
    @Index(name = "IDX_PERM_MENU", columnList = "MENU_ID"),
    @Index(name = "IDX_PERM_MODULE", columnList = "MODULE")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    /** 主键ID */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 权限编码，如 api:add, user:delete */
    @Column(name = "CODE", nullable = false, unique = true, length = 100)
    private String code;

    /** 权限名称 */
    @Column(name = "NAME", nullable = false, length = 50)
    private String name;

    /** 所属菜单ID */
    @Column(name = "MENU_ID")
    private Long menuId;

    /** 权限类型：MENU / BUTTON */
    @Column(name = "TYPE", length = 20)
    private String type;

    /** 所属模块：api / environment / user / role / log / system */
    @Column(name = "MODULE", length = 50)
    private String module;

    /** 描述 */
    @Column(name = "DESCRIPTION", length = 200)
    private String description;

    /** 排序号 */
    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    /** 创建时间 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = SnowflakeUtil.nextId();
        }
        createdAt = LocalDateTime.now();
        if (type == null) type = "BUTTON";
        if (sortOrder == null) sortOrder = 0;
    }
}
