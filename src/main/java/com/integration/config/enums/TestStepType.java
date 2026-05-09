package com.integration.config.enums;

/**
 * 测试步骤类型枚举
 */
public enum TestStepType {
    API,       // 直接调用 API
    SCENARIO,  // 执行场景编排
    MOCK       // 验证 Mock 响应
}