package com.integration.config.service;

import com.integration.config.dto.EnvironmentDTO;
import com.integration.config.entity.config.Environment;
import com.integration.config.repository.config.EnvironmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 环境配置服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;

    /**
     * 创建环境配置
     */
    @Transactional
    public EnvironmentDTO create(EnvironmentDTO dto) {
        // 检查系统名称+环境是否重复（同一系统同一环境只能有一条）
        if (environmentRepository.findBySystemNameAndEnvName(dto.getSystemName(), dto.getEnvName()).isPresent()) {
            throw new IllegalArgumentException("系统【" + dto.getSystemName() + "】下已存在【" + dto.getEnvName() + "】环境配置");
        }

        Environment entity = Environment.builder()
                .systemName(dto.getSystemName())
                .envName(dto.getEnvName())
                .baseUrl(dto.getBaseUrl())
                .description(dto.getDescription())
                .status(dto.getStatus() != null ? dto.getStatus() : "ACTIVE")
                .urlReplace(dto.getUrlReplace() != null ? dto.getUrlReplace() : true)
                .build();

        // 如果设为启用，同系统其他环境自动停用
        if ("ACTIVE".equals(entity.getStatus())) {
            deactivateSameSystem(entity.getSystemName());
        }

        entity = environmentRepository.save(entity);
        log.info("创建环境配置: 系统={}, 环境={}, BaseURL={}", dto.getSystemName(), dto.getEnvName(), dto.getBaseUrl());
        return toDTO(entity);
    }

    /**
     * 更新环境配置
     */
    @Transactional
    public EnvironmentDTO update(Long id, EnvironmentDTO dto) {
        Environment entity = environmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("环境配置不存在"));

        // 检查系统名称+环境是否重复（排除自己）
        Optional<Environment> existing = environmentRepository.findBySystemNameAndEnvName(dto.getSystemName(), dto.getEnvName());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new IllegalArgumentException("系统【" + dto.getSystemName() + "】下已存在【" + dto.getEnvName() + "】环境配置");
        }

        String oldSystem = entity.getSystemName();
        entity.setSystemName(dto.getSystemName());
        entity.setEnvName(dto.getEnvName());
        entity.setBaseUrl(dto.getBaseUrl());
        entity.setDescription(dto.getDescription());
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        if (dto.getUrlReplace() != null) {
            entity.setUrlReplace(dto.getUrlReplace());
        }

        // 如果设为启用，同系统其他环境自动停用（注意：如果改了系统名，也要停老系统的）
        if ("ACTIVE".equals(entity.getStatus())) {
            deactivateSameSystem(entity.getSystemName(), id);
            // 如果改了系统名，老系统也要停用
            if (!oldSystem.equals(entity.getSystemName())) {
                deactivateSameSystem(oldSystem);
            }
        }

        entity = environmentRepository.save(entity);
        log.info("更新环境配置: id={}", id);
        return toDTO(entity);
    }

    /**
     * 删除环境配置
     */
    @Transactional
    public void delete(Long id) {
        if (!environmentRepository.existsById(id)) {
            throw new IllegalArgumentException("环境配置不存在");
        }
        environmentRepository.deleteById(id);
        log.info("删除环境配置: id={}", id);
    }

    /**
     * 根据ID获取环境配置
     */
    public EnvironmentDTO getById(Long id) {
        return environmentRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new IllegalArgumentException("环境配置不存在"));
    }

    /**
     * 分页查询
     */
    public Page<EnvironmentDTO> list(String systemName, String status, Pageable pageable) {
        return environmentRepository.findByConditions(systemName, status, pageable)
                .map(this::toDTO);
    }

    /**
     * 获取所有系统名称列表（去重，用于分组下拉）
     */
    public List<String> getAllSystemNames() {
        return environmentRepository.findAllSystemNames();
    }

    /**
     * 获取指定系统下所有环境
     */
    public List<EnvironmentDTO> getBySystemName(String systemName) {
        return environmentRepository.findBySystemName(systemName).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据系统名称查找该系统当前启用的环境
     * 用于接口调用时的 URL 域名替换
     */
    public Optional<EnvironmentDTO> getActiveEnvironment(String systemName) {
        return environmentRepository.findBySystemNameAndStatus(systemName, "ACTIVE")
                .stream().findFirst()
                .map(this::toDTO);
    }

    /**
     * 停用同系统的所有其他环境（新建/更新时调用）
     */
    private void deactivateSameSystem(String systemName) {
        deactivateSameSystem(systemName, null);
    }

    private void deactivateSameSystem(String systemName, Long excludeId) {
        List<Environment> actives = environmentRepository.findBySystemNameAndStatus(systemName, "ACTIVE");
        for (Environment env : actives) {
            if (excludeId == null || !env.getId().equals(excludeId)) {
                env.setStatus("INACTIVE");
                environmentRepository.save(env);
                log.info("自动停用同系统环境: 系统={}, 环境={}", systemName, env.getEnvName());
            }
        }
    }

    private EnvironmentDTO toDTO(Environment entity) {
        return EnvironmentDTO.builder()
                .id(entity.getId())
                .systemName(entity.getSystemName())
                .envName(entity.getEnvName())
                .baseUrl(entity.getBaseUrl())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .urlReplace(entity.getUrlReplace())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
