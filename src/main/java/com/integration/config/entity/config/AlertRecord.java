package com.integration.config.entity.config;

import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 告警记录实体
 * 记录每次触发的告警事件，用于历史查询和追溯
 */
@Entity
@Table(name = "ALERT_RECORD", indexes = {
    @Index(name = "IDX_ALERT_RECORD_RULE", columnList = "RULE_ID"),
    @Index(name = "IDX_ALERT_RECORD_TIME", columnList = "ALERT_TIME"),
    @Index(name = "IDX_ALERT_RECORD_STATUS", columnList = "STATUS")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRecord {

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "RULE_ID", nullable = false)
    private Long ruleId;

    @Column(name = "RULE_CODE", nullable = false, length = 50)
    private String ruleCode;

    @Column(name = "RULE_NAME", length = 100)
    private String ruleName;

    @Column(name = "ALERT_TYPE", length = 30)
    private String alertType;

    /** 触发时的实际值（如 15.3%） */
    @Column(name = "ACTUAL_VALUE", length = 50)
    private String actualValue;

    @Column(name = "THRESHOLD_VALUE", length = 50)
    private String thresholdValue;

    /** 触发接口编码（全局告警为空） */
    @Column(name = "API_CODE", length = 50)
    private String apiCode;

    /** 告警摘要 */
    @Column(name = "DETAIL", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "CHANNELS", length = 200)
    private String channels;

    /** 通知结果：SUCCESS / PARTIAL / FAILED */
    @Column(name = "NOTIFY_RESULT", length = 20)
    private String notifyResult;

    @Column(name = "NOTIFY_DETAIL", columnDefinition = "TEXT")
    private String notifyDetail;

    /** 状态：FIRING-告警中 / RESOLVED-已恢复 / ACKNOWLEDGED-已确认 */
    @Column(name = "STATUS", length = 20)
    @Builder.Default
    private String status = "FIRING";

    @Column(name = "ALERT_TIME", nullable = false)
    private LocalDateTime alertTime;

    @Column(name = "RESOLVED_TIME")
    private LocalDateTime resolvedTime;

    @Column(name = "ACKNOWLEDGED_BY", length = 50)
    private String acknowledgedBy;

    @Column(name = "ACKNOWLEDGED_TIME")
    private LocalDateTime acknowledgedTime;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = SnowflakeUtil.nextId();
        if (status == null) status = "FIRING";
    }
}
