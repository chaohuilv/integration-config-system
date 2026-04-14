package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建用户请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserDTO {

    /** 用户编码（登录用，必填） */
    private String userCode;

    /** 用户名（显示用） */
    private String username;

    /** 密码 */
    private String password;

    /** 显示名称 */
    private String displayName;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 状态 */
    private String status;
}
