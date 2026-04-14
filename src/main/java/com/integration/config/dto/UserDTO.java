package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息DTO（不含敏感信息）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    /** 用户ID */
    private Long id;

    /** 用户编码（登录用） */
    private String userCode;

    /** 用户名（显示用） */
    private String username;

    /** 显示名称 */
    private String displayName;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 状态 */
    private String status;

    /** 最后登录时间 */
    private String lastLoginTime;

    /** 创建时间 */
    private String createdAt;
}
