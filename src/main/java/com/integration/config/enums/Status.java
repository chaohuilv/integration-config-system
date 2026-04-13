package com.integration.config.enums;

/**
 * 接口配置状态枚举
 */
public enum Status {
    ACTIVE("启用"),
    INACTIVE("禁用");

    private final String description;

    Status(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
