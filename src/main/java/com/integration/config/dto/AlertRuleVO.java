package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警规则查询 DTO（返回给前端）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleVO {

    private Long id;
    private String ruleCode;
    private String ruleName;
    private String description;
    private String alertType;
    private String alertTypeLabel;
    private String scope;
    private String scopeLabel;
    private List<String> apiCodes;
    private Double threshold;
    private String thresholdLabel;
    private Integer windowSeconds;
    private List<String> channels;
    private String status;
    private String statusLabel;
    private String cooldownLabel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 告警类型中英文映射 */
    public static String alertTypeLabel(String type) {
        return switch (type) {
            case "ERROR_RATE" -> "错误率告警";
            case "LATENCY" -> "延迟告警";
            case "RATE_LIMIT" -> "频率超限";
            case "CONSECUTIVE_FAIL" -> "连续失败";
            default -> type;
        };
    }

    /** 阈值格式化 */
    public static String thresholdLabel(String alertType, Double threshold) {
        return switch (alertType) {
            case "ERROR_RATE" -> threshold + "%";
            case "LATENCY" -> threshold + "ms";
            case "RATE_LIMIT" -> threshold + " 次/" + "窗口";
            case "CONSECUTIVE_FAIL" -> threshold + " 次连续";
            default -> String.valueOf(threshold);
        };
    }

    /** 冷却时间格式化 */
    public static String cooldownLabel(Integer seconds) {
        if (seconds == null) return "5 分钟";
        if (seconds < 60) return seconds + " 秒";
        return (seconds / 60) + " 分钟";
    }

    /** 状态标签 */
    public static String statusLabel(String status) {
        return "ACTIVE".equals(status) ? "启用" : "停用";
    }

    /** 范围标签 */
    public static String scopeLabel(String scope) {
        return "GLOBAL".equals(scope) ? "全局" : "指定接口";
    }
}
