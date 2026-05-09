package com.integration.config.dto;

import lombok.Data;

/**
 * 批量执行请求 DTO
 */
@Data
public class TestBatchRunDTO {
    /** 指定用例ID列表 */
    private java.util.List<Long> caseIds;

    /** 指定套件ID列表 */
    private java.util.List<Long> suiteIds;

    /** 按标签筛选 */
    private java.util.List<String> tags;

    /** 第一个失败即停止 */
    private Boolean stopOnFirstFailure;

    /** 触发来源：MANUAL / SCHEDULED / API */
    private String triggerSource;
}