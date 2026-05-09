package com.integration.config.service;

import com.integration.config.entity.config.AlertRecord;
import com.integration.config.entity.config.AlertRule;
import com.integration.config.entity.log.InvokeLog;
import com.integration.config.repository.config.AlertRecordRepository;
import com.integration.config.repository.config.AlertRuleRepository;
import com.integration.config.repository.log.InvokeLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AlertEvaluationService 单元测试
 * 
 * 测试范围：
 * 1. 错误率告警（ERROR_RATE）
 * 2. 延迟告警（LATENCY）
 * 3. 请求频率告警（RATE_LIMIT）
 * 4. 连续失败告警（CONSECUTIVE_FAIL）
 * 5. 冷却期判断
 * 6. 告警记录保存
 */
@ExtendWith(MockitoExtension.class)
class AlertEvaluationServiceTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Mock
    private AlertRecordRepository alertRecordRepository;

    @Mock
    private InvokeLogRepository invokeLogRepository;

    @Mock
    private AlertNotifyService alertNotifyService;

    @InjectMocks
    private AlertEvaluationService alertEvaluationService;

    private AlertRule mockRule;

    @BeforeEach
    void setUp() {
        mockRule = AlertRule.builder()
                .id(1L)
                .ruleCode("test-rule")
                .ruleName("测试告警规则")
                .alertType("ERROR_RATE")
                .scope("GLOBAL")
                .threshold(10.0)
                .windowSeconds(300)
                .channels("EMAIL")
                .cooldownSeconds(300)
                .status("ACTIVE")
                .build();
    }

    @Nested
    @DisplayName("错误率告警测试")
    class ErrorRateTests {

        @Test
        @DisplayName("错误率超过阈值触发告警")
        void testErrorRateAboveThreshold() {
            // Given
            when(alertRecordRepository.findLastFiringAlertTime("test-rule")).thenReturn(null);
            when(invokeLogRepository.countAllInWindow(any(), any())).thenReturn(100L);
            when(invokeLogRepository.countFailInWindow(any(), any())).thenReturn(15L); // 15% 错误率

            // When
            boolean triggered = alertEvaluationService.evaluateRule(mockRule);

            // Then
            assertTrue(triggered);
            verify(alertNotifyService).sendAlert(eq(mockRule), anyString(), anyString());
            verify(alertRecordRepository).save(any(AlertRecord.class));
        }

        @Test
        @DisplayName("错误率未超过阈值不触发")
        void testErrorRateBelowThreshold() {
            // Given
            when(alertRecordRepository.findLastFiringAlertTime("test-rule")).thenReturn(null);
            when(invokeLogRepository.countAllInWindow(any(), any())).thenReturn(100L);
            when(invokeLogRepository.countFailInWindow(any(), any())).thenReturn(5L); // 5% 错误率

            // When
            boolean triggered = alertEvaluationService.evaluateRule(mockRule);

            // Then
            assertFalse(triggered);
            verify(alertNotifyService, never()).sendAlert(any(), any(), any());
        }

        @Test
        @DisplayName("指定接口错误率计算")
        void testApiSpecificErrorRate() {
            // Given
            mockRule.setScope("API");
            mockRule.setApiCodes("api-1,api-2");

            when(alertRecordRepository.findLastFiringAlertTime("test-rule")).thenReturn(null);
            
            // api-1: 50 total, 10 fail = 20%
            when(invokeLogRepository.countByApiCodeInWindow(eq("api-1"), any(), any()))
                    .thenReturn(50L);
            when(invokeLogRepository.countFailByApiCodeInWindow(eq("api-1"), any(), any()))
                    .thenReturn(10L);
            // api-2: 50 total, 0 fail
            when(invokeLogRepository.countByApiCodeInWindow(eq("api-2"), any(), any()))
                    .thenReturn(50L);
            when(invokeLogRepository.countFailByApiCodeInWindow(eq("api-2"), any(), any()))
                    .thenReturn(0L);

            // When
            boolean triggered = alertEvaluationService.evaluateRule(mockRule);

            // Then
            assertTrue(triggered); // 总错误率 = 10/100 = 10%，刚好等于阈值
        }

        @Test
        @DisplayName("无请求时不触发")
        void testNoRequests() {
            // Given
            when(alertRecordRepository.findLastFiringAlertTime("test-rule")).thenReturn(null);
            when(invokeLogRepository.countAllInWindow(any(), any())).thenReturn(0L);

            // When
            boolean triggered = alertEvaluationService.evaluateRule(mockRule);

            // Then
            assertFalse(triggered);
        }
    }

    @Nested
    @DisplayName("延迟告警测试")
    class LatencyTests {

        @Test
        @DisplayName("平均延迟超过阈值触发告警")
        void testLatencyAboveThreshold() {
            // Given
            mockRule.setAlertType("LATENCY");
            mockRule.setThreshold(1000.0); // 1000ms

            when(alertRecordRepository.findLastFiringAlertTime("test-rule")).thenReturn(null);
            when(invokeLogRepository.avgCostTimeInWindow(any(), any())).thenReturn(1500.0);

            // When
            boolean triggered = alertEvaluationService.evaluateRule(mockRule);

            // Then
            assertTrue(triggered);
            verify(alertNotifyService).sendAlert(eq(mockRule), contains("ms"), anyString());
        }

        @Test
        @DisplayName("延迟正常不触发")
        void testLatencyNormal() {
            // Given
            mockRule.setAlertType("LATENCY");
            mockRule.setThreshold(1000.0);

            when(alertRecordRepository.findLastFiringAlertTime("test-rule")).thenReturn(null);
            when(invokeLogRepository.avgCostTimeInWindow(any(), any())).thenReturn(500.0);

            // When
            boolean triggered = alertEvaluationService.evaluateRule(mockRule);

            // Then
            assertFalse(triggered);
        }
    }

    @Nested
    @DisplayName("请求频率告警测试")
    class RateLimitTests {

        @Test
        @DisplayName("请求频率超过限制触发告警")
        void testRateLimitExceeded() {
            // Given
            mockRule.setAlertType("RATE_LIMIT");
            mockRule.setThreshold(1000.0); // 1000 次/窗口

            when(alertRecordRepository.findLastFiringAlertTime("test-rule")).thenReturn(null);
            when(invokeLogRepository.countAllInWindow(any(), any())).thenReturn(1500L);

            // When
            boolean triggered = alertEvaluationService.evaluateRule(mockRule);

            // Then
            assertTrue(triggered);
        }

        @Test
        @DisplayName("请求频率正常不触发")
        void testRateLimitNormal() {
            // Given
            mockRule.setAlertType("RATE_LIMIT");
            mockRule.setThreshold(1000.0);

            when(alertRecordRepository.findLastFiringAlertTime("test-rule")).thenReturn(null);
            when(invokeLogRepository.countAllInWindow(any(), any())).thenReturn(800L);

            // When
            boolean triggered = alertEvaluationService.evaluateRule(mockRule);

            // Then
            assertFalse(triggered);
        }
    }

    @Nested
    @DisplayName("连续失败告警测试")
    class ConsecutiveFailTests {

        @Test
        @DisplayName("连续失败超过阈值触发告警")
        void testConsecutiveFailExceeded() {
            // Given
            mockRule.setAlertType("CONSECUTIVE_FAIL");
            mockRule.setThreshold(3.0);
            mockRule.setScope("API");
            mockRule.setApiCodes("api-1");

            when(alertRecordRepository.findLastFiringAlertTime("test-rule")).thenReturn(null);
            
            // 模拟连续5次失败
            List<InvokeLog> recentLogs = Arrays.asList(
                    createFailLog(),
                    createFailLog(),
                    createFailLog(),
                    createFailLog(),
                    createFailLog()
            );
            when(invokeLogRepository.findRecentByApiCode(eq("api-1"), any())).thenReturn(recentLogs);

            // When
            boolean triggered = alertEvaluationService.evaluateRule(mockRule);

            // Then
            assertTrue(triggered);
        }

        @Test
        @DisplayName("中间有成功则重置计数")
        void testConsecutiveFailReset() {
            // Given
            mockRule.setAlertType("CONSECUTIVE_FAIL");
            mockRule.setThreshold(3.0);
            mockRule.setScope("API");
            mockRule.setApiCodes("api-1");

            when(alertRecordRepository.findLastFiringAlertTime("test-rule")).thenReturn(null);
            
            // 失败2次后成功，再失败1次 → 连续失败 = 1
            List<InvokeLog> recentLogs = Arrays.asList(
                    createFailLog(),
                    createFailLog(),
                    createSuccessLog(),
                    createFailLog()
            );
            when(invokeLogRepository.findRecentByApiCode(eq("api-1"), any())).thenReturn(recentLogs);

            // When
            boolean triggered = alertEvaluationService.evaluateRule(mockRule);

            // Then
            assertFalse(triggered);
        }
    }

    @Nested
    @DisplayName("冷却期测试")
    class CooldownTests {

        @Test
        @DisplayName("在冷却期内不重复告警")
        void testInCooldown() {
            // Given
            // 最近告警时间在冷却期内
            LocalDateTime lastAlert = LocalDateTime.now().minusSeconds(100); // 100秒前
            when(alertRecordRepository.findLastFiringAlertTime("test-rule")).thenReturn(lastAlert);
            // 即使指标超过阈值
            when(invokeLogRepository.countAllInWindow(any(), any())).thenReturn(100L);
            when(invokeLogRepository.countFailInWindow(any(), any())).thenReturn(50L);

            // When
            boolean triggered = alertEvaluationService.evaluateRule(mockRule);

            // Then
            assertFalse(triggered);
            verify(alertNotifyService, never()).sendAlert(any(), any(), any());
        }

        @Test
        @DisplayName("冷却期过后可以再次告警")
        void testAfterCooldown() {
            // Given
            // 最近告警时间在冷却期外（400秒前，冷却期300秒）
            LocalDateTime lastAlert = LocalDateTime.now().minusSeconds(400);
            when(alertRecordRepository.findLastFiringAlertTime("test-rule")).thenReturn(lastAlert);
            when(invokeLogRepository.countAllInWindow(any(), any())).thenReturn(100L);
            when(invokeLogRepository.countFailInWindow(any(), any())).thenReturn(50L);

            // When
            boolean triggered = alertEvaluationService.evaluateRule(mockRule);

            // Then
            assertTrue(triggered);
            verify(alertNotifyService).sendAlert(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("定时评估测试")
    class ScheduledEvaluationTests {

        @Test
        @DisplayName("evaluateAllRules遍历所有活跃规则")
        void testEvaluateAllRules() {
            // Given
            AlertRule rule1 = AlertRule.builder().id(1L).ruleCode("rule-1").alertType("ERROR_RATE").threshold(10.0).windowSeconds(300).build();
            AlertRule rule2 = AlertRule.builder().id(2L).ruleCode("rule-2").alertType("LATENCY").threshold(1000.0).windowSeconds(300).build();
            
            when(alertRuleRepository.findAllActive()).thenReturn(Arrays.asList(rule1, rule2));
            when(alertRecordRepository.findLastFiringAlertTime(any())).thenReturn(null);
            when(invokeLogRepository.countAllInWindow(any(), any())).thenReturn(100L);
            when(invokeLogRepository.countFailInWindow(any(), any())).thenReturn(5L);
            when(invokeLogRepository.avgCostTimeInWindow(any(), any())).thenReturn(500.0);

            // When
            alertEvaluationService.evaluateAllRules();

            // Then
            verify(alertRuleRepository).findAllActive();
            verify(invokeLogRepository, atLeastOnce()).countAllInWindow(any(), any());
        }

        @Test
        @DisplayName("无活跃规则时不执行评估")
        void testNoActiveRules() {
            // Given
            when(alertRuleRepository.findAllActive()).thenReturn(Collections.emptyList());

            // When
            alertEvaluationService.evaluateAllRules();

            // Then
            verify(invokeLogRepository, never()).countAllInWindow(any(), any());
        }

        @Test
        @DisplayName("单个规则异常不影响其他规则")
        void testExceptionDoesNotBreakLoop() {
            // Given
            AlertRule rule1 = AlertRule.builder().id(1L).ruleCode("rule-1").alertType("ERROR_RATE").threshold(10.0).windowSeconds(300).build();
            AlertRule rule2 = AlertRule.builder().id(2L).ruleCode("rule-2").alertType("LATENCY").threshold(1000.0).windowSeconds(300).build();
            
            when(alertRuleRepository.findAllActive()).thenReturn(Arrays.asList(rule1, rule2));
            
            // rule1 抛出异常
            when(alertRecordRepository.findLastFiringAlertTime("rule-1"))
                    .thenThrow(new RuntimeException("测试异常"));
            
            // rule2 正常
            when(alertRecordRepository.findLastFiringAlertTime("rule-2")).thenReturn(null);
            when(invokeLogRepository.avgCostTimeInWindow(any(), any())).thenReturn(500.0);

            // When
            alertEvaluationService.evaluateAllRules();

            // Then - rule2 仍然被评估
            verify(invokeLogRepository).avgCostTimeInWindow(any(), any());
        }
    }

    // ========== 辅助方法 ==========

    private InvokeLog createFailLog() {
        InvokeLog log = new InvokeLog();
        log.setSuccess(false);
        return log;
    }

    private InvokeLog createSuccessLog() {
        InvokeLog log = new InvokeLog();
        log.setSuccess(true);
        return log;
    }
}
