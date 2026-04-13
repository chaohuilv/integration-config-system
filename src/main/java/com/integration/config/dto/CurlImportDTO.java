package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Curl 命令解析结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurlImportDTO {

    /** 解析状态 */
    private boolean success;

    /** 错误信息 */
    private String message;

    /** 接口名称 */
    private String name;

    /** 接口编码（自动生成） */
    private String code;

    /** 目标URL */
    private String url;

    /** HTTP 方法 */
    private String method;

    /** 请求头列表 */
    private Map<String, String> headers;

    /** 请求体 */
    private String body;

    /** 请求参数（Query Params，JSON 字符串） */
    private String requestParams;

    /** 认证类型（从 Header 中自动识别） */
    private String authType;

    /** 认证参数名 */
    private String authParamName;

    /** 认证值 */
    private String authValue;

    /** 分组名称 */
    private String groupName;

    public static CurlImportDTO error(String message) {
        return CurlImportDTO.builder()
                .success(false)
                .message(message)
                .build();
    }
}
