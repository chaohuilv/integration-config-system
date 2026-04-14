package com.integration.config.dto;

import com.integration.config.enums.ContentType;
import com.integration.config.enums.HttpMethod;
import com.integration.config.enums.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接口配置 DTO（请求）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiConfigDTO {

    private Long id;

    /**
     * 接口名称
     */
    @NotBlank(message = "接口名称不能为空")
    private String name;

    /**
     * 接口编码
     */
    @NotBlank(message = "接口编码不能为空")
    private String code;

    /**
     * 接口描述
     */
    private String description;

    /**
     * 请求方法
     */
    @NotNull(message = "请求方法不能为空")
    private HttpMethod method;

    /**
     * 目标URL
     */
    @NotBlank(message = "目标URL不能为空")
    private String url;

    /**
     * Content-Type
     */
    private ContentType contentType;

    /**
     * 请求头（JSON字符串）
     */
    private String headers;

    /**
     * 请求参数（JSON字符串）
     */
    private String requestParams;

    /**
     * 请求体模板（JSON字符串）
     */
    private String requestBody;

    /**
     * 认证方式
     */
    private String authType;

    /**
     * 认证信息
     */
    private String authInfo;

    /**
     * 超时时间（毫秒）
     */
    private Integer timeout;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 是否启用缓存
     */
    private Boolean enableCache;

    /**
     * 缓存时间（秒）
     */
    private Integer cacheTime;

    /**
     * 状态
     */
    private Status status;

    /**
     * 分组名称
     */
    private String groupName;

    // ========== 动态Token配置 ==========

    /**
     * 是否启用动态Token
     */
    private Boolean enableDynamicToken;

    /**
     * 获取Token的接口编码
     */
    private String tokenApiCode;

    /**
     * Token在响应中的提取路径（JSONPath）
     */
    private String tokenExtractPath;

    /**
     * Token传递位置：header/url/body
     */
    private String tokenPosition;

    /**
     * Token参数名
     */
    private String tokenParamName;

    /**
     * Token前缀
     */
    private String tokenPrefix;

    /**
     * Token缓存时间（秒）
     */
    private Integer tokenCacheTime;

    // ========== 审计字段（只读，由服务层填充） ==========

    /** 创建人ID */
    private Long createdById;

    /** 创建人名称 */
    private String createdByName;

    /** 更新人ID */
    private Long updatedById;

    /** 更新人名称 */
    private String updatedByName;
}
