package com.integration.config.dto;

import lombok.Data;

/**
 * IP 白名单规则 DTO
 */
@Data
public class IpWhitelistRuleDTO {

    private Long id;
    private String ruleCode;
    private String ruleName;
    private String description;
    private String scope;       // GLOBAL / API
    private String apiCodes;
    private String ipList;
    private String matchMode;   // WHITELIST / BLACKLIST
    private String status;      // ACTIVE / INACTIVE
    private Integer priority;
    private String createdAt;
    private String updatedAt;
    private String createdBy;

    // 统计：关联接口数
    private Integer apiCount;
}
