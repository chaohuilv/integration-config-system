package com.integration.config.enums;

/**
 * Content-Type 枚举
 */
public enum ContentType {
    JSON("application/json"),
    FORM("application/x-www-form-urlencoded"),
    MULTIPART("multipart/form-data"),
    XML("application/xml"),
    TEXT("text/plain");

    private final String value;

    ContentType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
