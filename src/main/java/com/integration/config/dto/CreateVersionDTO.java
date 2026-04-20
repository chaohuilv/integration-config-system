package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建接口新版本的请求 DTO
 *
 * 版本信息（code/version/baseCode/latestVersion/deprecated）由系统在 createNewVersion() 中自动生成，
 * 此处只接收需要覆盖的业务字段（name/description/url 等）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVersionDTO {

    /** 新版本的名称（选填，不填则沿用源接口名称 + 版本号） */
    private String name;

    /** 新版本的描述（选填） */
    private String description;

    /** 新版本的目标 URL（选填） */
    private String url;

    /** 新版本的分段（选填） */
    private String groupName;

    /** 其他字段可按需扩展 */
}
