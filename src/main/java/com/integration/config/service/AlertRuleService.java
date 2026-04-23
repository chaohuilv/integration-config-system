package com.integration.config.service;

import com.integration.config.dto.AlertRuleDTO;
import com.integration.config.entity.config.AlertRecord;
import com.integration.config.entity.config.AlertRule;
import com.integration.config.enums.ErrorCode;
import com.integration.config.exception.BusinessException;
import com.integration.config.repository.config.AlertRecordRepository;
import com.integration.config.repository.config.AlertRuleRepository;
import com.integration.config.util.SnowflakeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警规则服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final AlertEvaluationService alertEvaluationService;

    // ==================== CRUD ====================

    public Page<AlertRule> pageQuery(String keyword, String alertType, String status, Pageable pageable) {
        return alertRuleRepository.findByConditions(keyword, alertType, status, pageable);
    }

    public List<AlertRule> getAllActive() {
        return alertRuleRepository.findAllActive();
    }

    public AlertRule getById(Long id) {
        return alertRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "告警规则不存在"));
    }

    @Transactional
    public AlertRule create(AlertRuleDTO dto, Long userId) {
        // 校验规则编码唯一
        if (alertRuleRepository.findByRuleCode(dto.getRuleCode()).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_EXISTS, "规则编码已存在: " + dto.getRuleCode());
        }

        AlertRule rule = toEntity(dto);
        rule.setId(SnowflakeUtil.nextId());
        rule.setCreatedBy(userId);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");

        return alertRuleRepository.save(rule);
    }

    @Transactional
    public AlertRule update(Long id, AlertRuleDTO dto) {
        AlertRule existing = getById(id);
        validateUpdate(existing, dto);

        existing.setRuleName(dto.getRuleName());
        existing.setDescription(dto.getDescription());
        existing.setAlertType(dto.getAlertType());
        existing.setScope(dto.getScope());
        existing.setApiCodes(joinApiCodes(dto.getApiCodes()));
        existing.setThreshold(dto.getThreshold());
        existing.setWindowSeconds(dto.getWindowSeconds());
        existing.setChannels(joinChannels(dto.getChannels()));
        existing.setDingtalkWebhook(dto.getDingtalkWebhook());
        existing.setDingtalkSecret(dto.getDingtalkSecret());
        existing.setWecomWebhook(dto.getWecomWebhook());
        existing.setEmailRecipients(joinEmails(dto.getEmailRecipients()));
        existing.setCooldownSeconds(dto.getCooldownSeconds() != null ? dto.getCooldownSeconds() : 300);
        if (dto.getStatus() != null) {
            existing.setStatus(dto.getStatus());
        }

        return alertRuleRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        AlertRule rule = getById(id);
        alertRuleRepository.delete(rule);
    }

    // ==================== 状态切换 ====================

    @Transactional
    public AlertRule toggleStatus(Long id) {
        AlertRule rule = getById(id);
        rule.setStatus("ACTIVE".equals(rule.getStatus()) ? "INACTIVE" : "ACTIVE");
        return alertRuleRepository.save(rule);
    }

    @Transactional
    public void testAlert(Long id) {
        AlertRule rule = getById(id);
        // 发送一条测试通知
        alertEvaluationService.saveAlertRecord(rule, rule.getThreshold(),
                "【测试告警】这是一条来自 " + rule.getRuleName() + " 的测试通知，指标值仅供参考。");
        // 不真正调用通知（避免干扰），仅验证记录创建
        log.info("[AlertRule] 测试告警记录已创建: rule={}", rule.getRuleName());
    }

    // ==================== 手动触发评估 ====================

    /** 手动触发指定规则的立即评估 */
    public boolean evaluateNow(Long id) {
        AlertRule rule = getById(id);
        return alertEvaluationService.evaluateRule(rule);
    }

    // ==================== 告警记录 ====================

    public Page<AlertRecord> pageQueryRecords(String keyword, String status, String alertType,
                                               LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        return alertRecordRepository.findByConditions(keyword, status, alertType, startTime, endTime, pageable);
    }

    @Transactional
    public void acknowledge(Long id, String acknowledgedBy) {
        AlertRecord record = alertRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "告警记录不存在"));
        record.setStatus("ACKNOWLEDGED");
        record.setAcknowledgedBy(acknowledgedBy);
        record.setAcknowledgedTime(LocalDateTime.now());
        alertRecordRepository.save(record);
    }

    @Transactional
    public void resolve(Long id) {
        AlertRecord record = alertRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "告警记录不存在"));
        record.setStatus("RESOLVED");
        record.setResolvedTime(LocalDateTime.now());
        alertRecordRepository.save(record);
    }

    @Transactional
    public void resolveByRule(Long ruleId) {
        alertRecordRepository.findByRuleCodeAndStatus(null, "FIRING").stream()
                .filter(r -> r.getRuleId() != null && r.getRuleId().equals(ruleId))
                .forEach(r -> {
                    r.setStatus("RESOLVED");
                    r.setResolvedTime(LocalDateTime.now());
                    alertRecordRepository.save(r);
                });
    }

    public Long countFiring() {
        return alertRecordRepository.countFiring();
    }

    // ==================== 工具方法 ====================

    private AlertRule toEntity(AlertRuleDTO dto) {
        return AlertRule.builder()
                .ruleCode(dto.getRuleCode())
                .ruleName(dto.getRuleName())
                .description(dto.getDescription())
                .alertType(dto.getAlertType())
                .scope(dto.getScope() != null ? dto.getScope() : "GLOBAL")
                .apiCodes(joinApiCodes(dto.getApiCodes()))
                .threshold(dto.getThreshold())
                .windowSeconds(dto.getWindowSeconds() != null ? dto.getWindowSeconds() : 300)
                .channels(joinChannels(dto.getChannels()))
                .dingtalkWebhook(dto.getDingtalkWebhook())
                .dingtalkSecret(dto.getDingtalkSecret())
                .wecomWebhook(dto.getWecomWebhook())
                .emailRecipients(joinEmails(dto.getEmailRecipients()))
                .cooldownSeconds(dto.getCooldownSeconds() != null ? dto.getCooldownSeconds() : 300)
                .build();
    }

    private void validateUpdate(AlertRule existing, AlertRuleDTO dto) {
        if (!existing.getRuleCode().equals(dto.getRuleCode())
                && alertRuleRepository.findByRuleCode(dto.getRuleCode()).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_EXISTS, "规则编码已存在: " + dto.getRuleCode());
        }
        if (dto.getScope() != null && "API".equals(dto.getScope())
                && (dto.getApiCodes() == null || dto.getApiCodes().isEmpty())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "指定接口告警时，必须选择至少一个接口");
        }
    }

    private String joinApiCodes(List<String> apiCodes) {
        if (apiCodes == null || apiCodes.isEmpty()) return null;
        return String.join(",", apiCodes);
    }

    private String joinChannels(List<String> channels) {
        if (channels == null || channels.isEmpty()) return null;
        return String.join(",", channels);
    }

    private String joinEmails(List<String> emails) {
        if (emails == null || emails.isEmpty()) return null;
        return String.join(",", emails);
    }
}
