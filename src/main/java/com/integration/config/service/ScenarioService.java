package com.integration.config.service;

import com.integration.config.dto.ScenarioDTO;
import com.integration.config.dto.ScenarioStepDTO;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.entity.config.Scenario;
import com.integration.config.entity.config.ScenarioStep;
import com.integration.config.enums.ErrorCode;
import com.integration.config.enums.Status;
import com.integration.config.exception.BusinessException;
import com.integration.config.repository.config.ApiConfigRepository;
import com.integration.config.repository.config.ScenarioRepository;
import com.integration.config.repository.config.ScenarioStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 场景管理 Service
 * 负责场景和步骤的 CRUD 管理
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioStepRepository scenarioStepRepository;
    private final ApiConfigRepository apiConfigRepository;

    // ==================== 场景管理 ====================

    /**
     * 分页查询场景
     */
    public Page<Scenario> pageQuery(String groupName, String status, String keyword, Pageable pageable) {
        // 处理空字符串参数
        groupName = (groupName != null && groupName.isEmpty()) ? null : groupName;
        status = (status != null && status.isEmpty()) ? null : status;
        keyword = (keyword != null && keyword.isEmpty()) ? null : keyword;

        // 状态转换
        return scenarioRepository.pageQuery(groupName, status, keyword, pageable);
    }

    /**
     * 查询场景详情（含步骤）
     */
    public ScenarioDTO getDetail(Long id) {
        Scenario scenario = scenarioRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "场景不存在"));

        ScenarioDTO dto = toDTO(scenario);
        List<ScenarioStep> steps = scenarioStepRepository.findByScenarioIdOrderByStepOrder(id);
        dto.setStepCount(steps.size());
        return dto;
    }

    /**
     * 查询场景步骤列表
     */
    public List<ScenarioStepDTO> getSteps(Long scenarioId) {
        if (!scenarioRepository.existsById(scenarioId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "场景不存在");
        }

        List<ScenarioStep> steps = scenarioStepRepository.findByScenarioIdOrderByStepOrder(scenarioId);
        return steps.stream().map(this::toStepDTO).collect(Collectors.toList());
    }

    /**
     * 创建场景（含步骤）
     */
    @Transactional(transactionManager = "configTransactionManager")
    public Scenario create(ScenarioDTO dto, List<ScenarioStepDTO> stepDTOs, Long userId, String userName) {
        // 检查编码唯一性
        if (dto.getCode() != null && scenarioRepository.existsByCode(dto.getCode())) {
            throw new BusinessException(ErrorCode.ALREADY_EXISTS, "场景编码已存在: " + dto.getCode());
        }

        Scenario scenario = Scenario.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .description(dto.getDescription())
                .groupName(dto.getGroupName())
                .failureStrategy(dto.getFailureStrategy() != null ? dto.getFailureStrategy() : "STOP")
                .timeoutSeconds(dto.getTimeoutSeconds() != null ? dto.getTimeoutSeconds() : 300)
                .status(dto.getStatus() != null ? Status.valueOf(dto.getStatus()) : Status.ACTIVE)
                .createdById(userId)
                .createdByName(userName)
                .updatedById(userId)
                .updatedByName(userName)
                .build();

        scenario = scenarioRepository.save(scenario);

        // 创建步骤
        if (stepDTOs != null && !stepDTOs.isEmpty()) {
            saveSteps(scenario.getId(), stepDTOs);
        }

        log.info("[ScenarioService] 创建场景: {} ({}), 共 {} 步", scenario.getName(), scenario.getCode(),
                stepDTOs != null ? stepDTOs.size() : 0);
        return scenario;
    }

    /**
     * 更新场景（含步骤，全量替换）
     */
    @Transactional(transactionManager = "configTransactionManager")
    public Scenario update(Long id, ScenarioDTO dto, List<ScenarioStepDTO> stepDTOs, Long userId, String userName) {
        Scenario scenario = scenarioRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "场景不存在"));

        // 检查编码唯一性（排除自身）
        if (dto.getCode() != null && !dto.getCode().equals(scenario.getCode())) {
            if (scenarioRepository.existsByCode(dto.getCode())) {
                throw new BusinessException(ErrorCode.ALREADY_EXISTS, "场景编码已存在: " + dto.getCode());
            }
            scenario.setCode(dto.getCode());
        }

        if (dto.getName() != null) scenario.setName(dto.getName());
        if (dto.getDescription() != null) scenario.setDescription(dto.getDescription());
        if (dto.getGroupName() != null) scenario.setGroupName(dto.getGroupName());
        if (dto.getFailureStrategy() != null) scenario.setFailureStrategy(dto.getFailureStrategy());
        if (dto.getTimeoutSeconds() != null) scenario.setTimeoutSeconds(dto.getTimeoutSeconds());
        if (dto.getStatus() != null) {
            try {
                scenario.setStatus(Status.valueOf(dto.getStatus()));
            } catch (IllegalArgumentException e) {
                log.warn("无效的状态值: {}", dto.getStatus());
            }
        }

        scenario.setUpdatedById(userId);
        scenario.setUpdatedByName(userName);
        scenario = scenarioRepository.save(scenario);

        // 全量替换步骤
        if (stepDTOs != null) {
            scenarioStepRepository.deleteByScenarioId(id);
            if (!stepDTOs.isEmpty()) {
                saveSteps(id, stepDTOs);
            }
        }

        log.info("[ScenarioService] 更新场景: {} ({})", scenario.getName(), scenario.getCode());
        return scenario;
    }

    /**
     * 切换场景状态
     */
    @Transactional(transactionManager = "configTransactionManager")
    public Scenario toggleStatus(Long id, Long userId, String userName) {
        Scenario scenario = scenarioRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "场景不存在"));

        if (scenario.getStatus() == Status.ACTIVE) {
            scenario.setStatus(Status.INACTIVE);
        } else {
            scenario.setStatus(Status.ACTIVE);
        }

        scenario.setUpdatedById(userId);
        scenario.setUpdatedByName(userName);
        scenario = scenarioRepository.save(scenario);

        log.info("[ScenarioService] 场景 {} 状态切换为: {}", scenario.getCode(), scenario.getStatus());
        return scenario;
    }

    /**
     * 删除场景（含步骤）
     */
    @Transactional(transactionManager = "configTransactionManager")
    public void delete(Long id) {
        Scenario scenario = scenarioRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "场景不存在"));

        // 删除步骤
        scenarioStepRepository.deleteByScenarioId(id);
        // 删除场景
        scenarioRepository.delete(scenario);

        log.info("[ScenarioService] 删除场景: {} ({})", scenario.getName(), scenario.getCode());
    }

    /**
     * 查询所有分组名称
     */
    public List<String> getGroupNames() {
        return scenarioRepository.findAll().stream()
                .map(Scenario::getGroupName)
                .filter(name -> name != null && !name.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 查询所有启用的场景
     */
    public List<Scenario> getActiveScenarios() {
        return scenarioRepository.findByStatus(Status.ACTIVE);
    }

    // ==================== 步骤管理 ====================

    /**
     * 添加步骤
     */
    @Transactional(transactionManager = "configTransactionManager")
    public ScenarioStep addStep(Long scenarioId, ScenarioStepDTO dto) {
        if (!scenarioRepository.existsById(scenarioId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "场景不存在");
        }

        // 检查步骤编码唯一性
        if (scenarioStepRepository.findByScenarioIdAndStepCode(scenarioId, dto.getStepCode()).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_EXISTS, "步骤编码已存在: " + dto.getStepCode());
        }

        // 如果未指定顺序，取最大值 +1
        if (dto.getStepOrder() == null) {
            Integer maxOrder = scenarioStepRepository.findMaxStepOrder(scenarioId);
            dto.setStepOrder(maxOrder != null ? maxOrder + 1 : 1);
        }

        ScenarioStep step = ScenarioStep.builder()
                .scenarioId(scenarioId)
                .stepCode(dto.getStepCode())
                .stepName(dto.getStepName())
                .stepOrder(dto.getStepOrder())
                .apiCode(dto.getApiCode())
                .inputMapping(dto.getInputMapping())
                .outputMapping(dto.getOutputMapping())
                .conditionExpr(dto.getConditionExpr())
                .skipOnError(dto.getSkipOnError() != null ? dto.getSkipOnError() : 0)
                .retryCount(dto.getRetryCount() != null ? dto.getRetryCount() : 0)
                .enableCache(dto.getEnableCache() != null ? dto.getEnableCache() : false)
                .cacheSeconds(dto.getCacheSeconds())
                .cacheKeys(dto.getCacheKeys())
                .build();

        step = scenarioStepRepository.save(step);
        log.info("[ScenarioService] 添加步骤: {} -> {}", scenarioId, step.getStepCode());
        return step;
    }

    /**
     * 更新步骤
     */
    @Transactional(transactionManager = "configTransactionManager")
    public ScenarioStep updateStep(Long stepId, ScenarioStepDTO dto) {
        ScenarioStep step = scenarioStepRepository.findById(stepId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "步骤不存在"));

        if (dto.getStepCode() != null) step.setStepCode(dto.getStepCode());
        if (dto.getStepName() != null) step.setStepName(dto.getStepName());
        if (dto.getStepOrder() != null) step.setStepOrder(dto.getStepOrder());
        if (dto.getApiCode() != null) step.setApiCode(dto.getApiCode());
        if (dto.getInputMapping() != null) step.setInputMapping(dto.getInputMapping());
        if (dto.getOutputMapping() != null) step.setOutputMapping(dto.getOutputMapping());
        step.setConditionExpr(dto.getConditionExpr()); // 允许置空
        if (dto.getSkipOnError() != null) step.setSkipOnError(dto.getSkipOnError());
        if (dto.getRetryCount() != null) step.setRetryCount(dto.getRetryCount());
        if (dto.getEnableCache() != null) step.setEnableCache(dto.getEnableCache());
        if (dto.getCacheSeconds() != null) step.setCacheSeconds(dto.getCacheSeconds());
        step.setCacheKeys(dto.getCacheKeys()); // 允许置空

        step = scenarioStepRepository.save(step);
        log.info("[ScenarioService] 更新步骤: {}", step.getStepCode());
        return step;
    }

    /**
     * 删除步骤
     */
    @Transactional(transactionManager = "configTransactionManager")
    public void deleteStep(Long stepId) {
        if (!scenarioStepRepository.existsById(stepId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "步骤不存在");
        }
        scenarioStepRepository.deleteById(stepId);
        log.info("[ScenarioService] 删除步骤: {}", stepId);
    }

    // ==================== 私有方法 ====================

    private void saveSteps(Long scenarioId, List<ScenarioStepDTO> stepDTOs) {
        for (int i = 0; i < stepDTOs.size(); i++) {
            ScenarioStepDTO dto = stepDTOs.get(i);
            ScenarioStep step = ScenarioStep.builder()
                    .scenarioId(scenarioId)
                    .stepCode(dto.getStepCode())
                    .stepName(dto.getStepName())
                    .stepOrder(dto.getStepOrder() != null ? dto.getStepOrder() : i + 1)
                    .apiCode(dto.getApiCode())
                    .inputMapping(dto.getInputMapping())
                    .outputMapping(dto.getOutputMapping())
                    .conditionExpr(dto.getConditionExpr())
                    .skipOnError(dto.getSkipOnError() != null ? dto.getSkipOnError() : 0)
                    .retryCount(dto.getRetryCount() != null ? dto.getRetryCount() : 0)
                    .enableCache(dto.getEnableCache() != null ? dto.getEnableCache() : false)
                    .cacheSeconds(dto.getCacheSeconds())
                    .cacheKeys(dto.getCacheKeys())
                    .build();
            scenarioStepRepository.save(step);
        }
    }

    private ScenarioDTO toDTO(Scenario scenario) {
        return ScenarioDTO.builder()
                .id(scenario.getId())
                .code(scenario.getCode())
                .name(scenario.getName())
                .description(scenario.getDescription())
                .groupName(scenario.getGroupName())
                .failureStrategy(scenario.getFailureStrategy())
                .timeoutSeconds(scenario.getTimeoutSeconds())
                .status(scenario.getStatus() != null ? scenario.getStatus().name() : null)
                .createdAt(scenario.getCreatedAt())
                .updatedAt(scenario.getUpdatedAt())
                .createdByName(scenario.getCreatedByName())
                .updatedByName(scenario.getUpdatedByName())
                .stepCount((int) scenarioStepRepository.countByScenarioId(scenario.getId()))
                .build();
    }

    private ScenarioStepDTO toStepDTO(ScenarioStep step) {
        ScenarioStepDTO dto = ScenarioStepDTO.builder()
                .id(step.getId())
                .scenarioId(step.getScenarioId())
                .stepCode(step.getStepCode())
                .stepName(step.getStepName())
                .stepOrder(step.getStepOrder())
                .apiCode(step.getApiCode())
                .inputMapping(step.getInputMapping())
                .outputMapping(step.getOutputMapping())
                .conditionExpr(step.getConditionExpr())
                .skipOnError(step.getSkipOnError())
                .retryCount(step.getRetryCount())
                .enableCache(step.getEnableCache())
                .cacheSeconds(step.getCacheSeconds())
                .cacheKeys(step.getCacheKeys())
                .createdAt(step.getCreatedAt())
                .updatedAt(step.getUpdatedAt())
                .build();

        // 填充接口名称
        try {
            apiConfigRepository.findByCode(step.getApiCode())
                    .ifPresent(api -> dto.setApiName(api.getName()));
        } catch (Exception e) {
            log.debug("查询接口名称失败: {}", step.getApiCode());
        }

        return dto;
    }
}
