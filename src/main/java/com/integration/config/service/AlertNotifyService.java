package com.integration.config.service;

import com.integration.config.entity.config.AlertRule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 告警通知服务
 * 支持钉钉、企业微信、邮件三种通知渠道
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertNotifyService {

    private final Optional<JavaMailSender> mailSenderOpt;
    @Value("${spring.mail.username:}")
    private String mailFromAddress;

    private RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        this.restTemplate = new RestTemplate();
    }

    // ==================== 统一通知入口 ====================

    /**
     * 发送告警通知（异步，并行推送所有渠道）
     *
     * @param rule       告警规则
     * @param actualValue 实际指标值
     * @param detail      告警详情
     * @return 通知结果描述
     */
    @Async
    public CompletableFuture<String> sendAlert(AlertRule rule, String actualValue, String detail) {
        log.info("[AlertNotify] 开始发送告警通知: rule={}, actualValue={}, channels={}",
                rule.getRuleName(), actualValue, rule.getChannels());

        List<String> channels = parseChannels(rule.getChannels());
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        StringBuilder results = new StringBuilder();

        for (String channel : channels) {
            switch (channel.trim().toUpperCase()) {
                case "DINGTALK" -> futures.add(sendDingTalkAsync(rule, actualValue, detail)
                        .thenApply(r -> { results.append("钉钉:").append(r ? "成功" : "失败").append(" "); return r; }));
                case "WECOM" -> futures.add(sendWeComAsync(rule, actualValue, detail)
                        .thenApply(r -> { results.append("企微:").append(r ? "成功" : "失败").append(" "); return r; }));
                case "EMAIL" -> futures.add(sendEmailAsync(rule, actualValue, detail)
                        .thenApply(r -> { results.append("邮件:").append(r ? "成功" : "失败").append(" "); return r; }));
                default -> log.warn("[AlertNotify] 未知通知渠道: {}", channel);
            }
        }

        // 等待所有渠道完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        boolean allSuccess = futures.stream()
                .map(CompletableFuture::join)
                .reduce(true, Boolean::logicalAnd);

        String result = allSuccess ? "全部成功" : results.toString();
        log.info("[AlertNotify] 告警通知发送完成: {}", result);
        return CompletableFuture.completedFuture(result);
    }

    // ==================== 钉钉通知 ====================

    private CompletableFuture<Boolean> sendDingTalkAsync(AlertRule rule, String actualValue, String detail) {
        return CompletableFuture.supplyAsync(() -> sendDingTalk(rule, actualValue, detail));
    }

    private boolean sendDingTalk(AlertRule rule, String actualValue, String detail) {
        if (rule.getDingtalkWebhook() == null || rule.getDingtalkWebhook().isBlank()) {
            log.warn("[AlertNotify] 钉钉Webhook未配置，跳过: {}", rule.getRuleName());
            return false;
        }

        try {
            String webhook = rule.getDingtalkWebhook();
            // 如果配置了签名密钥，使用加签模式
            String sign = null;
            if (rule.getDingtalkSecret() != null && !rule.getDingtalkSecret().isBlank()) {
                sign = generateDingTalkSign(rule.getDingtalkSecret());
                webhook = webhook + (webhook.contains("?") ? "&" : "?") + "sign=" + sign;
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msgtype", "markdown");

            Map<String, Object> markdown = new LinkedHashMap<>();
            String title = buildAlertTitle(rule, actualValue);
            markdown.put("title", title);
            markdown.put("text", buildDingTalkText(rule, actualValue, detail));
            body.put("markdown", markdown);

            // 安全设置
            if (sign == null) {
                Map<String, Object> at = new HashMap<>();
                at.put("atMobiles", Collections.emptyList());
                at.put("isAtAll", "false");
                body.put("at", at);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            var response = restTemplate.postForEntity(webhook, entity, String.class);
            boolean success = response.getStatusCode().is2xxSuccessful();

            if (success) {
                log.info("[AlertNotify] 钉钉通知发送成功: {}", rule.getRuleName());
            } else {
                log.error("[AlertNotify] 钉钉通知发送失败: {}, response={}", rule.getRuleName(), response.getBody());
            }
            return success;

        } catch (Exception e) {
            log.error("[AlertNotify] 钉钉通知异常: {}", rule.getRuleName(), e);
            return false;
        }
    }

    private String generateDingTalkSign(String secret) {
        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = Base64.getEncoder().encodeToString(signData);
            return "timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            log.error("[AlertNotify] 钉钉签名生成失败", e);
            return null;
        }
    }

    // ==================== 企业微信通知 ====================

    private CompletableFuture<Boolean> sendWeComAsync(AlertRule rule, String actualValue, String detail) {
        return CompletableFuture.supplyAsync(() -> sendWeCom(rule, actualValue, detail));
    }

    private boolean sendWeCom(AlertRule rule, String actualValue, String detail) {
        if (rule.getWecomWebhook() == null || rule.getWecomWebhook().isBlank()) {
            log.warn("[AlertNotify] 企微Webhook未配置，跳过: {}", rule.getRuleName());
            return false;
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msgtype", "markdown");

            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("content", buildWeComText(rule, actualValue, detail));
            body.put("markdown", markdown);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            var response = restTemplate.postForEntity(rule.getWecomWebhook(), entity, String.class);
            boolean success = response.getStatusCode().is2xxSuccessful();

            if (success) {
                log.info("[AlertNotify] 企微通知发送成功: {}", rule.getRuleName());
            } else {
                log.error("[AlertNotify] 企微通知发送失败: {}, response={}", rule.getRuleName(), response.getBody());
            }
            return success;

        } catch (Exception e) {
            log.error("[AlertNotify] 企微通知异常: {}", rule.getRuleName(), e);
            return false;
        }
    }

    // ==================== 邮件通知 ====================

    private CompletableFuture<Boolean> sendEmailAsync(AlertRule rule, String actualValue, String detail) {
        return CompletableFuture.supplyAsync(() -> sendEmail(rule, actualValue, detail));
    }

    private boolean sendEmail(AlertRule rule, String actualValue, String detail) {
        if (!mailSenderOpt.isPresent()) {
            log.warn("[AlertNotify] 邮件Sender未配置（可能缺少 spring-boot-starter-mail 依赖）");
            return false;
        }

        String recipients = rule.getEmailRecipients();
        if (recipients == null || recipients.isBlank()) {
            log.warn("[AlertNotify] 邮件收件人未配置，跳过: {}", rule.getRuleName());
            return false;
        }

        try {
            JavaMailSender mailSender = mailSenderOpt.get();
            SimpleMailMessage message = new SimpleMailMessage();
            message.setSubject(buildAlertTitle(rule, actualValue));
            message.setText(buildEmailText(rule, actualValue, detail));
            message.setTo(recipients.split("[,;]"));
            message.setFrom(mailFromAddress); // 使用 application.yml 中 spring.mail.username 配置的发件人地址

            mailSender.send(message);
            log.info("[AlertNotify] 邮件通知发送成功: {} -> {}", rule.getRuleName(), recipients);
            return true;

        } catch (Exception e) {
            log.error("[AlertNotify] 邮件通知异常: {}", rule.getRuleName(), e);
            return false;
        }
    }

    // ==================== 内容构建 ====================

    private String buildAlertTitle(AlertRule rule, String actualValue) {
        String typeLabel = switch (rule.getAlertType()) {
            case "ERROR_RATE" -> "错误率";
            case "LATENCY" -> "延迟";
            case "RATE_LIMIT" -> "限流";
            case "CONSECUTIVE_FAIL" -> "连续失败";
            default -> rule.getAlertType();
        };
        return String.format("🚨 【%s】%s 告警", typeLabel, rule.getRuleName());
    }

    private String buildDingTalkText(AlertRule rule, String actualValue, String detail) {
        String typeLabel = getTypeLabel(rule.getAlertType());
        String thresholdStr = formatThreshold(rule);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return String.format("""
                ### 🚨 接口集成平台告警

                **告警名称**: %s
                **告警类型**: %s
                **告警级别**: %s

                ---

                **当前指标值**: %s
                **告警阈值**: %s
                **统计窗口**: %s秒

                **详情**: %s

                **触发时间**: %s
                """,
                rule.getRuleName(), typeLabel, getSeverityEmoji(rule),
                actualValue, thresholdStr, rule.getWindowSeconds(),
                detail != null ? detail : "无", now);
    }

    private String buildWeComText(AlertRule rule, String actualValue, String detail) {
        String typeLabel = getTypeLabel(rule.getAlertType());
        String thresholdStr = formatThreshold(rule);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return String.format("""
                接口集成平台 > 告警通知

                **告警名称**: %s
                **告警类型**: %s
                **告警级别**: %s

                **当前指标值**: %s
                **告警阈值**: %s
                **统计窗口**: %s秒

                **详情**: %s

                **触发时间**: %s
                """,
                rule.getRuleName(), typeLabel, getSeverityEmoji(rule),
                actualValue, thresholdStr, rule.getWindowSeconds(),
                detail != null ? detail : "无", now);
    }

    private String buildEmailText(AlertRule rule, String actualValue, String detail) {
        String typeLabel = getTypeLabel(rule.getAlertType());
        String thresholdStr = formatThreshold(rule);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return String.format("""
                接口集成平台 - 告警通知
                ==========================

                告警名称: %s
                告警类型: %s

                当前指标值: %s
                告警阈值: %s
                统计窗口: %s秒

                详情: %s

                触发时间: %s
                """,
                rule.getRuleName(), typeLabel,
                actualValue, thresholdStr, rule.getWindowSeconds(),
                detail != null ? detail : "无", now);
    }

    private String getTypeLabel(String alertType) {
        return switch (alertType) {
            case "ERROR_RATE" -> "错误率告警";
            case "LATENCY" -> "延迟告警";
            case "RATE_LIMIT" -> "限流告警";
            case "CONSECUTIVE_FAIL" -> "连续失败告警";
            default -> alertType;
        };
    }

    private String getSeverityEmoji(AlertRule rule) {
        // 根据阈值严重程度判断级别
        double t = rule.getThreshold();
        return switch (rule.getAlertType()) {
            case "ERROR_RATE" -> t >= 50 ? "🔴 严重" : t >= 20 ? "🟠 警告" : "🟡 注意";
            case "LATENCY" -> t >= 10000 ? "🔴 严重" : t >= 5000 ? "🟠 警告" : "🟡 注意";
            case "CONSECUTIVE_FAIL" -> t >= 10 ? "🔴 严重" : t >= 5 ? "🟠 警告" : "🟡 注意";
            default -> "🟡 一般";
        };
    }

    private String formatThreshold(AlertRule rule) {
        return switch (rule.getAlertType()) {
            case "ERROR_RATE" -> rule.getThreshold() + "%";
            case "LATENCY" -> rule.getThreshold() + "ms";
            default -> String.valueOf(rule.getThreshold());
        };
    }

    private List<String> parseChannels(String channels) {
        if (channels == null || channels.isBlank()) return Collections.emptyList();
        return Arrays.asList(channels.split("[,;]"));
    }
}
