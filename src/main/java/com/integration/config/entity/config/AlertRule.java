package com.integration.config.entity.config;

import com.integration.config.converter.EncryptedFieldConverter;
import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 告警规则实体
 * 定义告警触发条件、通知渠道和阈值
 */
@Entity
@Table(name = "ALERT_RULE", indexes = {
    @Index(name = "IDX_ALERT_RULE_CODE", columnList = "RULE_CODE", unique = true),
    @Index(name = "IDX_ALERT_RULE_STATUS", columnList = "STATUS")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule {

    @Id
    @Column(name = "ID")
    private Long id;

    /** 规则编码，唯一标识 */
    @Column(name = "RULE_CODE", nullable = false, unique = true, length = 50)
    private String ruleCode;

    /** 规则名称 */
    @Column(name = "RULE_NAME", nullable = false, length = 100)
    private String ruleName;

    /** 规则描述 */
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** 告警类型：ERROR_RATE / LATENCY / RATE_LIMIT / CONSECUTIVE_FAIL */
    @Column(name = "ALERT_TYPE", nullable = false, length = 30)
    private String alertType;

    /** 作用范围：GLOBAL-全局 / API-指定接口 */
    @Column(name = "SCOPE", nullable = false, length = 20)
    private String scope;

    /** 关联接口编码（scope=API时必填，逗号分隔多个） */
    @Column(name = "API_CODES", columnDefinition = "TEXT")
    private String apiCodes;

    /** 阈值：错误率(0-100百分比) / 延迟(ms) / 频率(次数) / 连续失败(次数) */
    @Column(name = "THRESHOLD", nullable = false)
    private Double threshold;

    /** 统计时间窗口（秒） */
    @Column(name = "WINDOW_SECONDS", nullable = false)
    private Integer windowSeconds;

    /** 通知渠道：DINGTALK / WECOM / EMAIL（逗号分隔） */
    @Column(name = "CHANNELS", nullable = false, length = 200)
    private String channels;

    /** 钉钉群 Webhook URL */
    @Convert(converter = EncryptedFieldConverter.class)
    @Column(name = "DINGTALK_WEBHOOK", columnDefinition = "TEXT")
    private String dingtalkWebhook;

    /** 钉钉签名密钥（可选，用于加签模式） */
    @Convert(converter = EncryptedFieldConverter.class)
    @Column(name = "DINGTALK_SECRET",columnDefinition = "TEXT")
    private String dingtalkSecret;

    /** 企业微信群 Webhook URL */
    @Convert(converter = EncryptedFieldConverter.class)
    @Column(name = "WECOM_WEBHOOK", columnDefinition = "TEXT")
    private String wecomWebhook;

    /** 邮件收件人列表（逗号分隔） */
    @Convert(converter = EncryptedFieldConverter.class)
    @Column(name = "EMAIL_RECIPIENTS", columnDefinition = "TEXT")
    private String emailRecipients;

    /** 告警冷却时间（秒），同一规则在此期间不重复告警 */
    @Column(name = "COOLDOWN_SECONDS")
    @Builder.Default
    private Integer cooldownSeconds = 300;

    /** 状态：ACTIVE-启用 / INACTIVE-停用 */
    @Column(name = "STATUS", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY")
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = SnowflakeUtil.nextId();
        createdAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
