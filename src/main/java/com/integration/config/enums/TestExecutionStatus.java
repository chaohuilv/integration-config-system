package com.integration.config.enums;

/**
 * 测试执行状态枚举
 */
public enum TestExecutionStatus {
    PENDING,    // 等待执行
    RUNNING,    // 执行中
    SUCCESS,     // 成功
    FAILED,      // 失败
    TIMEOUT,     // 超时
    SKIPPED      // 跳过
}