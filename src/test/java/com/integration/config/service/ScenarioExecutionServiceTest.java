package com.integration.config.service;

import com.integration.config.dto.InvokeRequestDTO;
import com.integration.config.dto.InvokeResponseDTO;
import com.integration.config.dto.ScenarioExecuteRequestDTO;
import com.integration.config.dto.ScenarioExecuteResultDTO;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.entity.config.Scenario;
import com.integration.config.entity.config.ScenarioStep;
import com.integration.config.entity.log.ScenarioExecution;
import com.integration.config.entity.log.ScenarioStepLog;
import com.integration.config.enums.Status;
import com.integration.config.exception.BusinessException;
import com.integration.config.repository.config.ScenarioRepository;
import com.integration.config.repository.config.ScenarioStepRepository;
import com.integration.config.repository.log.ScenarioExecutionRepository;
import com.integration.config.repository.log.ScenarioStepLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ScenarioExecutionService 单元测试
 * 
 * 测试范围：
 * 1. 场景执行流程（成功/失败）
 * 2. 步骤输入输出映射
 * 3. 失败策略（STOP/CONTINUE）
 * 4. 条件表达式评估
 * 5. 场景缓存读写
 * 6. Token类型参数解析
 */
@ExtendWith(MockitoExtension.class)
class ScenarioExecutionServiceTest {

    @Mock
    private ScenarioRepository scenarioRepository;

    @Mock
    private ScenarioStepRepository scenarioStepRepository;

    @Mock
    private ScenarioExecutionRepository scenarioExecutionRepository;

    @Mock
    private ScenarioStepLogRepository scenarioStepLogRepository;

    @Mock
    private HttpInvokeService httpInvokeService;

    @Mock
    private ApiConfigService apiConfigService;

    @Mock
    private ScenarioCacheService scenarioCacheService;

    @Mock
    private TokenCacheManager tokenCacheManager;

    @InjectMocks
    private ScenarioExecutionService scenarioExecutionService;

    private Scenario mockScenario;
    private List<ScenarioStep> mockSteps;

    @BeforeEach
    void setUp() {
        mockScenario = Scenario.builder()
                .id(1L)
                .code("test-scenario")
                .name("测试场景")
                .status(Status.ACTIVE)
                .failureStrategy("STOP")
                .build();

        mockSteps = new ArrayList<>();
    }

    @Nested
    @DisplayName("场景执行测试")
    class ExecuteScenarioTests {

        @Test
        @DisplayName("单步骤场景执行成功")
        void testSingleStepSuccess() {
            // Given
            ScenarioStep step = createStep("step1", "步骤1", 1, "api-1");
            mockSteps.add(step);

            ScenarioExecuteRequestDTO request = ScenarioExecuteRequestDTO.builder()
                    .scenarioCode("test-scenario")
                    .build();

            when(scenarioRepository.findByCode("test-scenario")).thenReturn(Optional.of(mockScenario));
            when(scenarioStepRepository.findByScenarioIdOrderByStepOrder(1L)).thenReturn(mockSteps);
            when(scenarioExecutionRepository.save(any(ScenarioExecution.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            InvokeResponseDTO invokeResponse = InvokeResponseDTO.builder()
                    .success(true)
                    .statusCode(200)
                    .data(Map.of("userId", 123, "userName", "张三"))
                    .build();
            when(httpInvokeService.invoke(any(InvokeRequestDTO.class))).thenReturn(invokeResponse);

            // When
            ScenarioExecuteResultDTO result = scenarioExecutionService.execute(request, "test-user");

            // Then
            assertTrue(result.getSuccess());
            assertEquals("SUCCESS", result.getStatus());
            assertEquals(1, result.getSteps().size());
            assertEquals("SUCCESS", result.getSteps().get(0).getStatus());
        }

        @Test
        @DisplayName("多步骤顺序执行并传递输出")
        void testMultiStepExecution() {
            // Given
            ScenarioStep step1 = createStep("step1", "获取用户", 1, "api-get-user");
            step1.setOutputMapping("{\"outputs\":{\"userId\":\"$.data.id\"}}");
            
            ScenarioStep step2 = createStep("step2", "获取订单", 2, "api-get-orders");
            step2.setInputMapping("{\"params\":{\"orderId\":{\"type\":\"step\",\"step\":\"step1\",\"path\":\"userId\"}}}");
            
            mockSteps.add(step1);
            mockSteps.add(step2);

            ScenarioExecuteRequestDTO request = ScenarioExecuteRequestDTO.builder()
                    .scenarioCode("test-scenario")
                    .build();

            when(scenarioRepository.findByCode("test-scenario")).thenReturn(Optional.of(mockScenario));
            when(scenarioStepRepository.findByScenarioIdOrderByStepOrder(1L)).thenReturn(mockSteps);
            when(scenarioExecutionRepository.save(any(ScenarioExecution.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Step1 响应
            InvokeResponseDTO response1 = InvokeResponseDTO.builder()
                    .success(true)
                    .statusCode(200)
                    .data(Map.of("id", 100, "name", "订单"))
                    .build();
            
            // Step2 响应
            InvokeResponseDTO response2 = InvokeResponseDTO.builder()
                    .success(true)
                    .statusCode(200)
                    .data(Map.of("orders", List.of(Map.of("id", 1))))
                    .build();

            when(httpInvokeService.invoke(any(InvokeRequestDTO.class)))
                    .thenReturn(response1)
                    .thenReturn(response2);

            // When
            ScenarioExecuteResultDTO result = scenarioExecutionService.execute(request, "test-user");

            // Then
            assertTrue(result.getSuccess());
            assertEquals(2, result.getSteps().size());
            verify(httpInvokeService, times(2)).invoke(any(InvokeRequestDTO.class));
        }

        @Test
        @DisplayName("场景不存在抛出异常")
        void testScenarioNotFound() {
            // Given
            ScenarioExecuteRequestDTO request = ScenarioExecuteRequestDTO.builder()
                    .scenarioCode("non-exist")
                    .build();

            when(scenarioRepository.findByCode("non-exist")).thenReturn(Optional.empty());

            // When & Then
            assertThrows(BusinessException.class, () -> 
                scenarioExecutionService.execute(request, "test-user")
            );
        }

        @Test
        @DisplayName("场景未启用抛出异常")
        void testScenarioNotActive() {
            // Given
            mockScenario.setStatus(Status.INACTIVE);
            
            ScenarioExecuteRequestDTO request = ScenarioExecuteRequestDTO.builder()
                    .scenarioCode("test-scenario")
                    .build();

            when(scenarioRepository.findByCode("test-scenario")).thenReturn(Optional.of(mockScenario));

            // When & Then
            assertThrows(BusinessException.class, () -> 
                scenarioExecutionService.execute(request, "test-user")
            );
        }
    }

    @Nested
    @DisplayName("失败策略测试")
    class FailureStrategyTests {

        @Test
        @DisplayName("STOP策略：第一步失败停止执行")
        void testStopOnFailure() {
            // Given
            mockScenario.setFailureStrategy("STOP");
            
            ScenarioStep step1 = createStep("step1", "步骤1", 1, "api-1");
            ScenarioStep step2 = createStep("step2", "步骤2", 2, "api-2");
            mockSteps.add(step1);
            mockSteps.add(step2);

            ScenarioExecuteRequestDTO request = ScenarioExecuteRequestDTO.builder()
                    .scenarioCode("test-scenario")
                    .build();

            when(scenarioRepository.findByCode("test-scenario")).thenReturn(Optional.of(mockScenario));
            when(scenarioStepRepository.findByScenarioIdOrderByStepOrder(1L)).thenReturn(mockSteps);
            when(scenarioExecutionRepository.save(any(ScenarioExecution.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Step1 失败
            InvokeResponseDTO failResponse = InvokeResponseDTO.builder()
                    .success(false)
                    .statusCode(500)
                    .message("接口异常")
                    .build();
            when(httpInvokeService.invoke(any(InvokeRequestDTO.class))).thenReturn(failResponse);

            // When
            ScenarioExecuteResultDTO result = scenarioExecutionService.execute(request, "test-user");

            // Then
            assertFalse(result.getSuccess());
            assertEquals("FAILED", result.getStatus());
            // 只执行了 step1
            verify(httpInvokeService, times(1)).invoke(any(InvokeRequestDTO.class));
        }

        @Test
        @DisplayName("CONTINUE策略：失败后继续执行")
        void testContinueOnFailure() {
            // Given
            mockScenario.setFailureStrategy("CONTINUE");
            
            ScenarioStep step1 = createStep("step1", "步骤1", 1, "api-1");
            ScenarioStep step2 = createStep("step2", "步骤2", 2, "api-2");
            mockSteps.add(step1);
            mockSteps.add(step2);

            ScenarioExecuteRequestDTO request = ScenarioExecuteRequestDTO.builder()
                    .scenarioCode("test-scenario")
                    .build();

            when(scenarioRepository.findByCode("test-scenario")).thenReturn(Optional.of(mockScenario));
            when(scenarioStepRepository.findByScenarioIdOrderByStepOrder(1L)).thenReturn(mockSteps);
            when(scenarioExecutionRepository.save(any(ScenarioExecution.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            InvokeResponseDTO failResponse = InvokeResponseDTO.builder()
                    .success(false)
                    .statusCode(500)
                    .build();
            InvokeResponseDTO successResponse = InvokeResponseDTO.builder()
                    .success(true)
                    .statusCode(200)
                    .data(Map.of("ok", true))
                    .build();

            when(httpInvokeService.invoke(any(InvokeRequestDTO.class)))
                    .thenReturn(failResponse)
                    .thenReturn(successResponse);

            // When
            ScenarioExecuteResultDTO result = scenarioExecutionService.execute(request, "test-user");

            // Then
            assertFalse(result.getSuccess());
            assertEquals("PARTIAL", result.getStatus()); // 部分成功
            // 两步都执行
            verify(httpInvokeService, times(2)).invoke(any(InvokeRequestDTO.class));
        }
    }

    @Nested
    @DisplayName("条件表达式测试")
    class ConditionExpressionTests {

        @Test
        @DisplayName("条件满足继续执行")
        void testConditionMet() {
            // Given
            ScenarioStep step1 = createStep("step1", "步骤1", 1, "api-1");
            step1.setOutputMapping("{\"outputs\":{\"status\":\"$.data.status\"}}");
            
            ScenarioStep step2 = createStep("step2", "步骤2", 2, "api-2");
            step2.setConditionExpr("{{step:step1.status}} == 'SUCCESS'");
            
            mockSteps.add(step1);
            mockSteps.add(step2);

            ScenarioExecuteRequestDTO request = ScenarioExecuteRequestDTO.builder()
                    .scenarioCode("test-scenario")
                    .build();

            when(scenarioRepository.findByCode("test-scenario")).thenReturn(Optional.of(mockScenario));
            when(scenarioStepRepository.findByScenarioIdOrderByStepOrder(1L)).thenReturn(mockSteps);
            when(scenarioExecutionRepository.save(any(ScenarioExecution.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            InvokeResponseDTO response = InvokeResponseDTO.builder()
                    .success(true)
                    .statusCode(200)
                    .data(Map.of("status", "SUCCESS"))
                    .build();
            when(httpInvokeService.invoke(any(InvokeRequestDTO.class))).thenReturn(response);

            // When
            ScenarioExecuteResultDTO result = scenarioExecutionService.execute(request, "test-user");

            // Then
            assertTrue(result.getSuccess());
            assertEquals(2, result.getSteps().size());
        }

        @Test
        @DisplayName("条件不满足标记失败")
        void testConditionNotMet() {
            // Given
            ScenarioStep step1 = createStep("step1", "步骤1", 1, "api-1");
            step1.setOutputMapping("{\"outputs\":{\"status\":\"$.data.status\"}}");
            
            ScenarioStep step2 = createStep("step2", "步骤2", 2, "api-2");
            step2.setConditionExpr("{{step:step1.status}} == 'FAILED'");
            
            mockSteps.add(step1);
            mockSteps.add(step2);

            ScenarioExecuteRequestDTO request = ScenarioExecuteRequestDTO.builder()
                    .scenarioCode("test-scenario")
                    .build();

            when(scenarioRepository.findByCode("test-scenario")).thenReturn(Optional.of(mockScenario));
            when(scenarioStepRepository.findByScenarioIdOrderByStepOrder(1L)).thenReturn(mockSteps);
            when(scenarioExecutionRepository.save(any(ScenarioExecution.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            InvokeResponseDTO response = InvokeResponseDTO.builder()
                    .success(true)
                    .statusCode(200)
                    .data(Map.of("status", "SUCCESS")) // 不是 FAILED
                    .build();
            when(httpInvokeService.invoke(any(InvokeRequestDTO.class))).thenReturn(response);

            // When
            ScenarioExecuteResultDTO result = scenarioExecutionService.execute(request, "test-user");

            // Then
            assertFalse(result.getSuccess());
            assertEquals("FAILED", result.getSteps().get(1).getStatus());
            assertTrue(result.getSteps().get(1).getErrorMessage().contains("条件表达式不满足"));
        }
    }

    @Nested
    @DisplayName("输入映射测试")
    class InputMappingTests {

        @Test
        @DisplayName("静态参数映射")
        void testStaticParamMapping() {
            // Given
            ScenarioStep step = createStep("step1", "步骤1", 1, "api-1");
            step.setInputMapping("{\"params\":{\"type\":{\"type\":\"static\",\"value\":\"create\"}}}");
            mockSteps.add(step);

            ScenarioExecuteRequestDTO request = ScenarioExecuteRequestDTO.builder()
                    .scenarioCode("test-scenario")
                    .build();

            when(scenarioRepository.findByCode("test-scenario")).thenReturn(Optional.of(mockScenario));
            when(scenarioStepRepository.findByScenarioIdOrderByStepOrder(1L)).thenReturn(mockSteps);
            when(scenarioExecutionRepository.save(any(ScenarioExecution.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            InvokeResponseDTO response = InvokeResponseDTO.builder()
                    .success(true)
                    .statusCode(200)
                    .build();
            when(httpInvokeService.invoke(any(InvokeRequestDTO.class))).thenReturn(response);

            // When
            scenarioExecutionService.execute(request, "test-user");

            // Then
            verify(httpInvokeService).invoke(argThat(req -> 
                req.getParams() != null && "create".equals(req.getParams().get("type"))
            ));
        }

        @Test
        @DisplayName("输入参数映射")
        void testInputParamMapping() {
            // Given
            ScenarioStep step = createStep("step1", "步骤1", 1, "api-1");
            step.setInputMapping("{\"params\":{\"userId\":{\"type\":\"input\",\"value\":\"uid\"}}}");
            mockSteps.add(step);

            ScenarioExecuteRequestDTO request = ScenarioExecuteRequestDTO.builder()
                    .scenarioCode("test-scenario")
                    .params(Map.of("uid", "user-123"))
                    .build();

            when(scenarioRepository.findByCode("test-scenario")).thenReturn(Optional.of(mockScenario));
            when(scenarioStepRepository.findByScenarioIdOrderByStepOrder(1L)).thenReturn(mockSteps);
            when(scenarioExecutionRepository.save(any(ScenarioExecution.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            InvokeResponseDTO response = InvokeResponseDTO.builder()
                    .success(true)
                    .statusCode(200)
                    .build();
            when(httpInvokeService.invoke(any(InvokeRequestDTO.class))).thenReturn(response);

            // When
            scenarioExecutionService.execute(request, "test-user");

            // Then
            verify(httpInvokeService).invoke(argThat(req -> 
                req.getParams() != null && "user-123".equals(req.getParams().get("userId"))
            ));
        }

        @Test
        @DisplayName("Token类型参数从缓存读取")
        void testTokenTypeParam() {
            // Given
            ScenarioStep step = createStep("step1", "步骤1", 1, "api-1");
            step.setInputMapping("{\"params\":{\"token\":{\"type\":\"token\",\"apiCode\":\"token-api\"}}}");
            mockSteps.add(step);

            ScenarioExecuteRequestDTO request = ScenarioExecuteRequestDTO.builder()
                    .scenarioCode("test-scenario")
                    .build();

            when(scenarioRepository.findByCode("test-scenario")).thenReturn(Optional.of(mockScenario));
            when(scenarioStepRepository.findByScenarioIdOrderByStepOrder(1L)).thenReturn(mockSteps);
            when(scenarioExecutionRepository.save(any(ScenarioExecution.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(tokenCacheManager.getCachedToken("token-api")).thenReturn("cached-token-xyz");

            InvokeResponseDTO response = InvokeResponseDTO.builder()
                    .success(true)
                    .statusCode(200)
                    .build();
            when(httpInvokeService.invoke(any(InvokeRequestDTO.class))).thenReturn(response);

            // When
            scenarioExecutionService.execute(request, "test-user");

            // Then
            verify(httpInvokeService).invoke(argThat(req -> 
                req.getParams() != null && "cached-token-xyz".equals(req.getParams().get("token"))
            ));
        }
    }

    @Nested
    @DisplayName("场景缓存测试")
    class ScenarioCacheTests {

        @Test
        @DisplayName("缓存命中跳过API调用")
        void testCacheHitSkipInvoke() {
            // Given
            ScenarioStep step = createStep("step1", "步骤1", 1, "api-1");
            step.setEnableCache(true);
            step.setCacheSeconds(300);
            step.setOutputMapping("{\"outputs\":{\"userId\":\"$.data.id\"}}");
            mockSteps.add(step);

            ScenarioExecuteRequestDTO request = ScenarioExecuteRequestDTO.builder()
                    .scenarioCode("test-scenario")
                    .build();

            when(scenarioRepository.findByCode("test-scenario")).thenReturn(Optional.of(mockScenario));
            when(scenarioStepRepository.findByScenarioIdOrderByStepOrder(1L)).thenReturn(mockSteps);
            when(scenarioExecutionRepository.save(any(ScenarioExecution.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(scenarioCacheService.get("test-scenario", "step1", "userId")).thenReturn(123);

            // When
            ScenarioExecuteResultDTO result = scenarioExecutionService.execute(request, "test-user");

            // Then
            assertTrue(result.getSuccess());
            // 不应该调用HTTP
            verify(httpInvokeService, never()).invoke(any());
        }

        @Test
        @DisplayName("执行成功后写入缓存")
        void testWriteCacheAfterSuccess() {
            // Given
            ScenarioStep step = createStep("step1", "步骤1", 1, "api-1");
            step.setEnableCache(true);
            step.setCacheSeconds(300);
            step.setOutputMapping("{\"outputs\":{\"userId\":\"$.data.id\"}}");
            mockSteps.add(step);

            ScenarioExecuteRequestDTO request = ScenarioExecuteRequestDTO.builder()
                    .scenarioCode("test-scenario")
                    .build();

            when(scenarioRepository.findByCode("test-scenario")).thenReturn(Optional.of(mockScenario));
            when(scenarioStepRepository.findByScenarioIdOrderByStepOrder(1L)).thenReturn(mockSteps);
            when(scenarioExecutionRepository.save(any(ScenarioExecution.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(scenarioCacheService.get(any(), any(), any())).thenReturn(null);

            InvokeResponseDTO response = InvokeResponseDTO.builder()
                    .success(true)
                    .statusCode(200)
                    .data(Map.of("id", 456))
                    .build();
            when(httpInvokeService.invoke(any(InvokeRequestDTO.class))).thenReturn(response);

            // When
            scenarioExecutionService.execute(request, "test-user");

            // Then
            verify(scenarioCacheService).put(eq(1L), eq("test-scenario"), eq("step1"), eq("userId"), eq(456), eq(300));
        }
    }

    // ========== 辅助方法 ==========

    private ScenarioStep createStep(String stepCode, String stepName, int order, String apiCode) {
        ScenarioStep step = new ScenarioStep();
        step.setId((long) order);
        step.setStepCode(stepCode);
        step.setStepName(stepName);
        step.setStepOrder(order);
        step.setApiCode(apiCode);
        return step;
    }
}
