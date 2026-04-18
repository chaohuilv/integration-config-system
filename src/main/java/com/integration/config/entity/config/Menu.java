package com.integration.config.entity.config;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 菜单实体
 */
@Entity
@Table(name = "SYS_MENU", indexes = {
    @Index(name = "IDX_MENU_CODE", columnList = "CODE", unique = true),
    @Index(name = "IDX_MENU_PARENT", columnList = "PARENT_ID")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Menu {

    /** 主键ID */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 菜单编码，唯一标识 */
    @Column(name = "CODE", nullable = false, unique = true, length = 50)
    private String code;

    /** 菜单名称 */
    @Column(name = "NAME", nullable = false, length = 50)
    private String name;

    /** 图标（emoji 或图标类名） */
    @Column(name = "ICON", length = 50)
    private String icon;

    /** 前端路由路径 */
    @Column(name = "PATH", length = 100)
    private String path;

    /** 页面文件路径（如 pages/api_list.html） */
    @Column(name = "PAGE_FILE", length = 200)
    private String pageFile;

    /** 父菜单ID，null表示顶级菜单 */
    @Column(name = "PARENT_ID")
    private Long parentId;

    /** 所属分组 */
    @Column(name = "SECTION", length = 50)
    private String section;

    /** 排序号 */
    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    /** 状态：ACTIVE-启用 / INACTIVE-禁用 */
    @Column(name = "STATUS", length = 20)
    private String status;

    /** 页面类型：LIST-列表页（显示在菜单） / FORM-表单详情页（不显示在菜单） */
    @Column(name = "PAGE_TYPE", length = 20)
    private String pageType;

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
        if (sortOrder == null) sortOrder = 0;
        if (pageType == null) pageType = "LIST"; // 默认为列表页
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
