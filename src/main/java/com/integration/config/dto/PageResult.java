package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private Long total;
    private Integer page;
    private Integer size;
    private List<T> records;

    // 统计字段（用于调用日志）
    private Long successCount;
    private Long failCount;
    private String successRate;

    public static <T> PageResult<T> of(List<T> records, Long total, Integer page, Integer size) {
        return PageResult.<T>builder()
                .records(records)
                .total(total)
                .page(page)
                .size(size)
                .build();
    }

    public static <T> PageResult<T> ofWithStats(List<T> records, Long total, Integer page, Integer size,
                                                 Long successCount, Long failCount) {
        String rate = total > 0 ? String.format("%.1f%%", (successCount * 100.0 / total)) : "0%";
        return PageResult.<T>builder()
                .records(records)
                .total(total)
                .page(page)
                .size(size)
                .successCount(successCount)
                .failCount(failCount)
                .successRate(rate)
                .build();
    }
}
