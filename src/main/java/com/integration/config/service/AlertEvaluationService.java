package com.integration.config.service;

import com.integration.config.entity.config.AlertRecord;
import com.integration.config.entity.config.AlertRule;
import com.integration.config.entity.log.InvokeLog;
import com.integration.config.repository.config.AlertRecordRepository;
import com.integration.config.repository.config.AlertRuleRepository;
import com.integration.config.repository.log.InvokeLogRepository;
import com.integration.config.util.SnowflakeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 告警评估与触发服务
 * 定时扫描 InvokeLog，计算指标，判断是否触发告警
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertEvaluationService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final InvokeLogRepository invokeLogRepository;
    private final AlertNotifyService alertNotifyService;

    // ========== 定时评估（每分钟执行一次）==========

    /**
     * 每分钟扫描一次告警规则
     * 支持动态调整 cron 表达式（通过 @Scheduled 的 fixedRate 改为 cron 方式）
     */
    @Scheduled(fixedRate = 60000) // 60秒 = 1分钟
    public void evaluateAllRules() {
        log.debug("[AlertEval] 开始评估告警规则...");
        try {
            List<AlertRule> activeRules = alertRuleRepository.findAllActive();
            for (AlertRule rule : activeRules) {
                try {
                    evaluateRule(rule);
                } catch (Exception e) {
                    log.error("[AlertEval] 评估规则异常: {}", rule.getRuleName(), e);
                }
            }
            log.debug("[AlertEval] 本轮评估完成，共 {} 条规则", activeRules.size());
        } catch (Exception e) {
            log.error("[AlertEval] 评估循环异常", e);
        }
    }

    // ========== 单条规则评估 ==========

    /**
     * 评估单个告警规则
     * @return 是否触发了告警
     */
    public boolean evaluateRule(AlertRule rule) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusSeconds(rule.getWindowSeconds());

        // 冷却判断
        LocalDateTime lastFiring = alertRecordRepository.findLastFiringAlertTime(rule.getRuleCode());
        if (lastFiring != null) {
            long cooldown = rule.getCooldownSeconds() != null ? rule.getCooldownSeconds() : 300;
            if (lastFiring.plusSeconds(cooldown).isAfter(now)) {
                log.debug("[AlertEval] 规则 {} 在冷却期内，跳过", rule.getRuleName());
                return false;
            }
        }

        // 计算指标
        double metricValue = calculateMetric(rule, windowStart, now);

        // 判断是否超过阈值
        boolean triggered = isTriggered(rule, metricValue);
        log.debug("[AlertEval] 规则 {} 指标值={}, 阈值={}, 触发={}",
                rule.getRuleName(), String.format("%.2f", metricValue), rule.getThreshold(), triggered);

        if (triggered) {
            // 构建告警详情
            String detail = buildDetail(rule, metricValue, windowStart, now);
            // 异步发送通知
            alertNotifyService.sendAlert(rule, formatMetricValue(rule, metricValue), detail);
            // 记录告警
            saveAlertRecord(rule, metricValue, detail);
        }

        return triggered;
    }

    // ========== 指标计算 ==========

    /**
     * 根据告警类型计算指标值
     */
    private double calculateMetric(AlertRule rule, LocalDateTime start, LocalDateTime end) {
        return switch (rule.getAlertType()) {
            case "ERROR_RATE" -> calculateErrorRate(rule, start, end);
            case "LATENCY" -> calculateAvgLatency(rule, start, end);
            case "RATE_LIMIT" -> calculateRequestCount(rule, start, end);
            case "CONSECUTIVE_FAIL" -> calculateConsecutiveFails(rule);
            default -> 0.0;
        };
    }

    /** 错误率 = 失败数 / 总数 * 100 */
    private double calculateErrorRate(AlertRule rule, LocalDateTime start, LocalDateTime end) {
        List<String> apiCodes = getTargetApiCodes(rule);
        if (apiCodes.isEmpty()) {
            // 全局错误率
            long total = invokeLogRepository.countAllInWindow(start, end);
            if (total == 0) return 0.0;
            long fail = invokeLogRepository.countFailInWindow(start, end);
            return (double) fail / total * 100;
        } else {
            // 指定接口
            long total = 0, fail = 0;
            for (String apiCode : apiCodes) {
                total += invokeLogRepository.countByApiCodeInWindow(apiCode, start, end);
                fail += invokeLogRepository.countFailByApiCodeInWindow(apiCode, start, end);
            }
            if (total == 0) return 0.0;
            return (double) fail / total * 100;
        }
    }

    /** 平均延迟（毫秒） */
    private double calculateAvgLatency(AlertRule rule, LocalDateTime start, LocalDateTime end) {
        List<String> apiCodes = getTargetApiCodes(rule);
        if (apiCodes.isEmpty()) {
            return invokeLogRepository.avgCostTimeInWindow(start, end);
        } else {
            double sum = 0, count = 0;
            for (String apiCode : apiCodes) {
                Double avg = invokeLogRepository.avgCostTimeByApiCodeInWindow(apiCode, start, end);
                if (avg != null && avg > 0) { sum += avg; count++; }
            }
            return count > 0 ? sum / count : 0.0;
        }
    }

    /** 请求频率（窗口内总请求数） */
    private double calculateRequestCount(AlertRule rule, LocalDateTime start, LocalDateTime end) {
        List<String> apiCodes = getTargetApiCodes(rule);
        if (apiCodes.isEmpty()) {
            return invokeLogRepository.countAllInWindow(start, end);
        } else {
            long sum = 0;
            for (String apiCode : apiCodes) {
                Long c = invokeLogRepository.countByApiCodeInWindow(apiCode, start, end);
                if (c != null) sum += c;
            }
            return sum;
        }
    }

    /** 连续失败次数 */
    private double calculateConsecutiveFails(AlertRule rule) {
        List<String> apiCodes = getTargetApiCodes(rule);
        if (apiCodes.isEmpty()) return 0.0;

        // 取每个接口最近 N 条日志，看连续失败数
        int consecutive = 0;
        for (String apiCode : apiCodes) {
            List<InvokeLog> recent = invokeLogRepository.findRecentByApiCode(apiCode,
                    LocalDateTime.now().minusSeconds(rule.getWindowSeconds()));
            int local = countConsecutiveFails(recent);
            consecutive = Math.max(consecutive, local);
        }
        return consecutive;
    }

    private int countConsecutiveFails(List<InvokeLog> logs) {
        int count = 0;
        for (InvokeLog log : logs) {
            if (Boolean.FALSE.equals(log.getSuccess())) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    // ========== 阈值判断 ==========

    private boolean isTriggered(AlertRule rule, double metricValue) {
        double threshold = rule.getThreshold();
        return switch (rule.getAlertType()) {
            // 错误率和延迟：超过阈值触发
            case "ERROR_RATE", "LATENCY" -> metricValue >= threshold;
            // 限流：超过阈值触发（通常是请求数上限）
            case "RATE_LIMIT" -> metricValue >= threshold;
            // 连续失败：超过阈值触发
            case "CONSECUTIVE_FAIL" -> metricValue >= threshold;
            default -> false;
        };
    }

    private String formatMetricValue(AlertRule rule, double value) {
        return switch (rule.getAlertType()) {
            case "ERROR_RATE" -> String.format("%.2f%%", value);
            case "LATENCY" -> String.format("%.0fms", value);
            default -> String.format("%.0f", value);
        };
    }

    // ========== 告警记录持久化 ==========

    @Transactional
    public void saveAlertRecord(AlertRule rule, double metricValue, String detail) {
        AlertRecord record = AlertRecord.builder()
                .id(SnowflakeUtil.nextId())
                .ruleId(rule.getId())
                .ruleCode(rule.getRuleCode())
                .ruleName(rule.getRuleName())
                .alertType(rule.getAlertType())
                .actualValue(formatMetricValue(rule, metricValue))
                .thresholdValue(formatThresholdValue(rule))
                .apiCode(rule.getScope().equals("GLOBAL") ? null : rule.getApiCodes())
                .detail(detail)
                .channels(rule.getChannels())
                .status("FIRING")
                .alertTime(LocalDateTime.now())
                .build();
        alertRecordRepository.save(record);
        log.info("[AlertEval] 告警记录已保存: rule={}, value={}", rule.getRuleName(), formatMetricValue(rule, metricValue));
    }

    private String formatThresholdValue(AlertRule rule) {
        return switch (rule.getAlertType()) {
            case "ERROR_RATE" -> rule.getThreshold() + "%";
            case "LATENCY" -> rule.getThreshold() + "ms";
            default -> String.valueOf(rule.getThreshold());
        };
    }

    private String buildDetail(AlertRule rule, double metricValue, LocalDateTime start, LocalDateTime end) {
        String timeRange = start.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                + " ~ " + end.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        List<String> apiCodes = getTargetApiCodes(rule);
        String target = apiCodes.isEmpty() ? "全局" : String.join(", ", apiCodes);

        return String.format("%s (%s)，统计窗口：%s，当前值：%s，超过阈值：%s",
                rule.getRuleName(), target, timeRange,
                formatMetricValue(rule, metricValue), formatThresholdValue(rule));
    }

    private List<String> getTargetApiCodes(AlertRule rule) {
        if (rule.getScope() == null || !"API".equals(rule.getScope())) {
            return Collections.emptyList();
        }
        if (rule.getApiCodes() == null || rule.getApiCodes().isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(rule.getApiCodes().split("[,;]"));
    }
}
