package com.integration.config.enums;

/**
 * 断言运算符枚举
 */
public enum AssertionOperator {
    EQUALS,           // 精确相等
    NOT_EQUALS,       // 不相等
    CONTAINS,         // 包含子串
    NOT_CONTAINS,     // 不包含子串
    GREATER_THAN,     // 大于
    LESS_THAN,        // 小于
    GREATER_OR_EQUAL, // 大于等于
    LESS_OR_EQUAL,    // 小于等于
    REGEX,            // 正则匹配
    EXISTS,           // 字段存在
    NOT_EXISTS,       // 字段不存在
    IS_NULL,          // 值为 null
    NOT_NULL,         // 值不为 null
    IN,               // 在列表中
    JSON_MATCH        // JSON Schema 验证
}