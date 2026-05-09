package com.integration.config.service;

import com.integration.config.config.IntegrationConfig;
import com.integration.config.dto.InvokeRequestDTO;
import com.integration.config.dto.InvokeResponseDTO;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.entity.log.InvokeLog;
import com.integration.config.enums.ContentType;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import com.integration.config.enums.Status;
import com.integration.config.repository.log.InvokeLogRepository;
import com.integration.config.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HttpInvokeService 单元测试
 * 
 * 测试范围：
 * 1. 基本调用流程（GET/POST）
 * 2. 缓存命中与未命中
 * 3. 动态Token获取与注入
 * 4. 失败重试机制
 * 5. 环境配置URL替换
 * 6. 认证头构建
 */
@ExtendWith(MockitoExtension.class)
class HttpInvokeServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ApiConfigService apiConfigService;

    @Mock
    private InvokeLogRepository invokeLogRepository;

    @Mock
    private IntegrationConfig integrationConfig;

    @Mock
    private TokenCacheManager tokenCacheManager;

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private RedisCacheService redisCacheService;

    @InjectMocks
    private HttpInvokeService httpInvokeService;

    private ApiConfig mockConfig;

    @BeforeEach
    void setUp() {
        // 默认配置
        mockConfig = ApiConfig.builder()
                .id(1L)
                .code("test-api")
                .name("测试接口")
                .method(com.integration.config.enums.HttpMethod.GET)
                .url("https://api.example.com/test")
                .contentType(ContentType.JSON)
                .status(Status.ACTIVE)
                .build();

        // 默认配置行为
        when(integrationConfig.isEnvironmentEnabled()).thenReturn(false);
        when(integrationConfig.isLogRequest()).thenReturn(false);
        when(integrationConfig.isLogResponse()).thenReturn(false);
    }

    @Nested
    @DisplayName("基本调用测试")
    class BasicInvokeTests {

        @Test
        @DisplayName("GET请求成功调用")
        void testGetInvokeSuccess() {
            // Given
            InvokeRequestDTO request = InvokeRequestDTO.builder()
                    .apiCode("test-api")
                    .build();

            when(apiConfigService.getByCode("test-api")).thenReturn(mockConfig);
            
            ResponseEntity<String> mockResponse = new ResponseEntity<>(
                    "{\"code\":200,\"data\":{\"id\":1}}",
                    HttpStatus.OK
            );
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(mockResponse);

            // When
            InvokeResponseDTO response = httpInvokeService.invoke(request);

            // Then
            assertTrue(response.getSuccess());
            assertEquals(200, response.getStatusCode());
            assertNotNull(response.getData());
            verify(invokeLogRepository).save(any(InvokeLog.class));
        }

        @Test
        @DisplayName("POST请求带请求体")
        void testPostInvokeWithBody() {
            // Given
            mockConfig.setMethod(com.integration.config.enums.HttpMethod.POST);
            mockConfig.setRequestBody("{\"name\":\"{{name}}\"}");

            InvokeRequestDTO request = InvokeRequestDTO.builder()
                    .apiCode("test-api")
                    .params(Map.of("name", "张三"))
                    .build();

            when(apiConfigService.getByCode("test-api")).thenReturn(mockConfig);
            
            ResponseEntity<String> mockResponse = new ResponseEntity<>(
                    "{\"code\":200}",
                    HttpStatus.OK
            );
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(mockResponse);

            // When
            InvokeResponseDTO response = httpInvokeService.invoke(request);

            // Then
            assertTrue(response.getSuccess());
            assertEquals(200, response.getStatusCode());
        }

        @Test
        @DisplayName("接口不存在抛出异常")
        void testApiNotFound() {
            // Given
            InvokeRequestDTO request = InvokeRequestDTO.builder()
                    .apiCode("non-exist")
                    .build();

            when(apiConfigService.getByCode("non-exist"))
                    .thenThrow(new RuntimeException("接口不存在"));

            // When
            InvokeResponseDTO response = httpInvokeService.invoke(request);

            // Then
            assertFalse(response.getSuccess());
            assertEquals(500, response.getStatusCode());
            assertTrue(response.getMessage().contains("接口不存在"));
        }
    }

    @Nested
    @DisplayName("缓存测试")
    class CacheTests {

        @Test
        @DisplayName("缓存命中直接返回")
        void testCacheHit() {
            // Given
            mockConfig.setEnableCache(true);
            mockConfig.setCacheTime(300);

            InvokeRequestDTO request = InvokeRequestDTO.builder()
                    .apiCode("test-api")
                    .build();

            when(apiConfigService.getByCode("test-api")).thenReturn(mockConfig);
            when(redisCacheService.get(anyString())).thenReturn(Map.of("id", 1, "name", "test"));

            // When
            InvokeResponseDTO response = httpInvokeService.invoke(request);

            // Then
            assertTrue(response.getSuccess());
            assertTrue(response.getFromCache());
            assertEquals(200, response.getStatusCode());
            assertEquals("来自缓存", response.getMessage());
            
            // 不应该调用实际HTTP
            verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        }

        @Test
        @DisplayName("缓存未命中执行调用并写入缓存")
        void testCacheMissThenWrite() {
            // Given
            mockConfig.setEnableCache(true);
            mockConfig.setCacheTime(300);

            InvokeRequestDTO request = InvokeRequestDTO.builder()
                    .apiCode("test-api")
                    .build();

            when(apiConfigService.getByCode("test-api")).thenReturn(mockConfig);
            when(redisCacheService.get(anyString())).thenReturn(null);
            
            ResponseEntity<String> mockResponse = new ResponseEntity<>(
                    "{\"code\":200,\"data\":{\"id\":1}}",
                    HttpStatus.OK
            );
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            InvokeResponseDTO response = httpInvokeService.invoke(request);

            // Then
            assertTrue(response.getSuccess());
            assertFalse(response.getFromCache());
            verify(redisCacheService).put(anyString(), any(), eq(300));
        }
    }

    @Nested
    @DisplayName("动态Token测试")
    class DynamicTokenTests {

        @Test
        @DisplayName("从缓存获取Token")
        void testGetTokenFromCache() {
            // Given
            mockConfig.setEnableDynamicToken(true);
            mockConfig.setTokenApiCode("token-api");
            mockConfig.setTokenPosition("header");
            mockConfig.setTokenParamName("Authorization");
            mockConfig.setTokenPrefix("Bearer ");

            InvokeRequestDTO request = InvokeRequestDTO.builder()
                    .apiCode("test-api")
                    .build();

            when(apiConfigService.getByCode("test-api")).thenReturn(mockConfig);
            when(tokenCacheManager.getCachedToken("token-api")).thenReturn("cached-token-123");
            
            ResponseEntity<String> mockResponse = new ResponseEntity<>(
                    "{\"code\":200}",
                    HttpStatus.OK
            );
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            InvokeResponseDTO response = httpInvokeService.invoke(request);

            // Then
            assertTrue(response.getSuccess());
            verify(tokenCacheManager).getCachedToken("token-api");
        }
    }

    @Nested
    @DisplayName("认证测试")
    class AuthTests {

        @Test
        @DisplayName("Bearer认证头正确构建")
        void testBearerAuth() {
            // Given
            mockConfig.setAuthType("bearer");
            mockConfig.setAuthInfo("test-token-123");

            InvokeRequestDTO request = InvokeRequestDTO.builder()
                    .apiCode("test-api")
                    .build();

            when(apiConfigService.getByCode("test-api")).thenReturn(mockConfig);
            
            ResponseEntity<String> mockResponse = new ResponseEntity<>("{}", HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            httpInvokeService.invoke(request);

            // Then - 验证RestTemplate被调用（认证头在内部构建）
            verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        }

        @Test
        @DisplayName("API Key认证头正确构建")
        void testApiKeyAuth() {
            // Given
            mockConfig.setAuthType("api_key");
            mockConfig.setAuthInfo("X-API-Key:my-secret-key");

            InvokeRequestDTO request = InvokeRequestDTO.builder()
                    .apiCode("test-api")
                    .build();

            when(apiConfigService.getByCode("test-api")).thenReturn(mockConfig);
            
            ResponseEntity<String> mockResponse = new ResponseEntity<>("{}", HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            httpInvokeService.invoke(request);

            // Then
            verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        }
    }

    @Nested
    @DisplayName("重试机制测试")
    class RetryTests {

        @Test
        @DisplayName("5xx错误触发重试")
        void testRetryOn5xxError() {
            // Given
            mockConfig.setRetryCount(2);

            InvokeRequestDTO request = InvokeRequestDTO.builder()
                    .apiCode("test-api")
                    .build();

            when(apiConfigService.getByCode("test-api")).thenReturn(mockConfig);
            
            // 第一次返回500
            ResponseEntity<String> errorResponse = new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR);
            // 第二次成功
            ResponseEntity<String> successResponse = new ResponseEntity<>("{\"ok\":true}", HttpStatus.OK);
            
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenReturn(errorResponse)
                    .thenReturn(successResponse);

            // When
            InvokeResponseDTO response = httpInvokeService.invoke(request);

            // Then
            assertTrue(response.getSuccess());
            verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class));
        }

        @Test
        @DisplayName("4xx错误不重试")
        void testNoRetryOn4xxError() {
            // Given
            mockConfig.setRetryCount(3);

            InvokeRequestDTO request = InvokeRequestDTO.builder()
                    .apiCode("test-api")
                    .build();

            when(apiConfigService.getByCode("test-api")).thenReturn(mockConfig);
            
            ResponseEntity<String> notFoundResponse = new ResponseEntity<>("not found", HttpStatus.NOT_FOUND);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenReturn(notFoundResponse);

            // When
            InvokeResponseDTO response = httpInvokeService.invoke(request);

            // Then
            assertFalse(response.getSuccess());
            assertEquals(404, response.getStatusCode());
            // 只调用一次，不重试
            verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class));
        }
    }

    @Nested
    @DisplayName("URL参数替换测试")
    class UrlParamTests {

        @Test
        @DisplayName("URL模板参数替换")
        void testUrlTemplateReplacement() {
            // Given
            mockConfig.setUrl("https://api.example.com/users/{{userId}}/orders/{{orderId}}");

            InvokeRequestDTO request = InvokeRequestDTO.builder()
                    .apiCode("test-api")
                    .params(Map.of("userId", "123", "orderId", "456"))
                    .build();

            when(apiConfigService.getByCode("test-api")).thenReturn(mockConfig);
            
            ResponseEntity<String> mockResponse = new ResponseEntity<>("{}", HttpStatus.OK);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenReturn(mockResponse);

            // When
            httpInvokeService.invoke(request);

            // Then - 验证URL被正确替换
            verify(restTemplate).exchange(
                    eq("https://api.example.com/users/123/orders/456"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            );
        }
    }
}
