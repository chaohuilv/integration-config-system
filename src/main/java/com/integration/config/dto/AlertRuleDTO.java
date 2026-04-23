package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 告警规则 DTO（用于创建/更新请求）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleDTO {

    private Long id;
    private String ruleCode;
    private String ruleName;
    private String description;

    /** 告警类型：ERROR_RATE / LATENCY / RATE_LIMIT / CONSECUTIVE_FAIL */
    private String alertType;

    /** 作用范围：GLOBAL / API */
    private String scope;

    /** 关联接口编码列表（scope=API时） */
    private List<String> apiCodes;

    private Double threshold;
    private Integer windowSeconds;

    /** 通知渠道列表 */
    private List<String> channels;

    private String dingtalkWebhook;
    private String dingtalkSecret;
    private String wecomWebhook;
    private List<String> emailRecipients;

    /** 冷却时间（秒），默认300 */
    private Integer cooldownSeconds;

    private String status;
}
