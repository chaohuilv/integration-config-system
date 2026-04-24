package com.integration.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 场景 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioDTO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String groupName;
    private String failureStrategy;
    private Integer timeoutSeconds;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdByName;
    private String updatedByName;

    /**
     * 步骤数量（仅列表展示用）
     */
    private Integer stepCount;
}
