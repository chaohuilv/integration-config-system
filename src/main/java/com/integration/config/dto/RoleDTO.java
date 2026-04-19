package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色DTO（含统计信息）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {

    private Long id;
    private String name;
    private String code;
    private String description;
    private String status;
    private Integer sortOrder;
    private Boolean isSystem;

    /** 关联用户数 */
    private Integer userCount;
    /** 关联接口数 */
    private Integer apiCount;
    /** 关联菜单数 */
    private Integer menuCount;
    /** 关联权限数 */
    private Integer permissionCount;
}
