package com.integration.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 环境配置 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentDTO {

    private Long id;

    /**
     * 系统名称（对应接口配置的 groupName）
     */
    @NotBlank(message = "系统名称不能为空")
    private String systemName;

    /**
     * 环境名称：DEV/TEST/PRE/PROD
     */
    @NotBlank(message = "环境不能为空")
    private String envName;

    /**
     * 基础URL
     */
    @NotBlank(message = "Base URL不能为空")
    private String baseUrl;

    /**
     * 描述
     */
    private String description;

    /**
     * 状态：ACTIVE 启用，INACTIVE 禁用
     */
    private String status;

    /**
     * 是否启用域名替换
     */
    private Boolean urlReplace;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}