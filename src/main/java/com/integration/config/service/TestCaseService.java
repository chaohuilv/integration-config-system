package com.integration.config.service;

import com.integration.config.dto.TestCaseDetailDTO;
import com.integration.config.dto.TestCaseListDTO;
import com.integration.config.dto.TestCaseSaveDTO;
import com.integration.config.dto.TestCaseStepDTO;
import com.integration.config.entity.config.TestCase;
import com.integration.config.entity.config.TestCaseStep;
import com.integration.config.enums.ErrorCode;
import com.integration.config.enums.Status;
import com.integration.config.exception.BusinessException;
import com.integration.config.repository.config.TestCaseRepository;
import com.integration.config.repository.config.TestCaseStepRepository;
import com.integration.config.util.SnowflakeUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 测试用例 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final TestCaseStepRepository testCaseStepRepository;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 分页查询测试用例
     */
    public Page<TestCaseListDTO> pageQuery(String groupName, String status, String keyword, String tags, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        // 处理空字符串为 null
        String groupParam = StringUtils.hasText(groupName) ? groupName : null;
        String statusParam = StringUtils.hasText(status) ? status : null;
        String keywordParam = StringUtils.hasText(keyword) ? keyword : null;
        String tagsParam = StringUtils.hasText(tags) ? tags : null;
        
        Page<TestCase> entityPage = testCaseRepository.pageQuery(groupParam, statusParam, keywordParam, tagsParam, pageable);
        
        List<TestCaseListDTO> dtoList = entityPage.getContent().stream()
                .map(this::toListDTO)
                .collect(Collectors.toList());
        
        return new PageImpl<>(dtoList, pageable, entityPage.getTotalElements());
    }

    /**
     * 获取用例详情
     */
    public TestCaseDetailDTO getDetail(Long id) {
        TestCase testCase = testCaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        
        TestCaseDetailDTO dto = toDetailDTO(testCase);
        
        // 查询步骤
        List<TestCaseStep> steps = testCaseStepRepository.findByTestCaseIdOrderByStepOrderAsc(id);
        dto.setSteps(steps.stream().map(this::toStepDTO).collect(Collectors.toList()));
        
        return dto;
    }

    /**
     * 根据编码获取详情
     */
    public TestCaseDetailDTO getDetailByCode(String code) {
        TestCase testCase = testCaseRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + code));
        return getDetail(testCase.getId());
    }

    /**
     * 创建测试用例
     */
    @Transactional
    public TestCase create(TestCaseSaveDTO dto) {
        // 校验编码唯一性
        if (testCaseRepository.existsByCode(dto.getCode())) {
            throw new BusinessException(ErrorCode.ALREADY_EXISTS, "用例编码已存在: " + dto.getCode());
        }
        
        TestCase testCase = new TestCase();
        copyFromDTO(testCase, dto);
        testCase.setId(SnowflakeUtil.nextId());
        
        return testCaseRepository.save(testCase);
    }

    /**
     * 更新测试用例
     */
    @Transactional
    public TestCase update(Long id, TestCaseSaveDTO dto) {
        TestCase testCase = testCaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        
        // 校验编码唯一性（排除自身）
        if (!testCase.getCode().equals(dto.getCode()) && 
            testCaseRepository.existsByCodeAndIdNot(dto.getCode(), id)) {
            throw new BusinessException(ErrorCode.ALREADY_EXISTS, "用例编码已存在: " + dto.getCode());
        }
        
        copyFromDTO(testCase, dto);
        return testCaseRepository.save(testCase);
    }

    /**
     * 删除测试用例（同时删除步骤）
     */
    @Transactional
    public void delete(Long id) {
        TestCase testCase = testCaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        
        // 删除关联的步骤
        testCaseStepRepository.deleteByTestCaseId(id);
        
        // 删除用例
        testCaseRepository.delete(testCase);
    }

    /**
     * 切换启用/禁用状态
     */
    @Transactional
    public TestCase toggleStatus(Long id) {
        TestCase testCase = testCaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + id));
        
        if (testCase.getStatus() == Status.ACTIVE) {
            testCase.setStatus(Status.INACTIVE);
        } else {
            testCase.setStatus(Status.ACTIVE);
        }
        
        return testCaseRepository.save(testCase);
    }

    /**
     * 获取所有分组名称
     */
    public List<String> getGroups() {
        return testCaseRepository.findAll().stream()
                .map(TestCase::getGroupName)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 获取所有启用的用例（用于套件选择）
     */
    public List<TestCaseListDTO> getActiveCases() {
        return testCaseRepository.findByStatus(Status.ACTIVE).stream()
                .map(this::toListDTO)
                .collect(Collectors.toList());
    }

    /**
     * 保存用例步骤（全量替换）
     */
    @Transactional
    public void saveSteps(Long testCaseId, List<TestCaseStepDTO> stepDTOs) {
        // 校验用例存在
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + testCaseId));
        
        // 删除旧步骤
        testCaseStepRepository.deleteByTestCaseId(testCaseId);
        
        // 保存新步骤
        if (stepDTOs != null && !stepDTOs.isEmpty()) {
            // 校验步骤编码唯一性
            List<String> stepCodes = new ArrayList<>();
            for (int i = 0; i < stepDTOs.size(); i++) {
                TestCaseStepDTO dto = stepDTOs.get(i);
                if (!StringUtils.hasText(dto.getStepCode())) {
                    throw new BusinessException(ErrorCode.INVALID_PARAM, "第" + (i + 1) + "步的编码不能为空");
                }
                if (stepCodes.contains(dto.getStepCode())) {
                    throw new BusinessException(ErrorCode.INVALID_PARAM, "步骤编码重复: " + dto.getStepCode());
                }
                stepCodes.add(dto.getStepCode());
                
                // 校验步骤类型
                if (!StringUtils.hasText(dto.getStepType())) {
                    throw new BusinessException(ErrorCode.INVALID_PARAM, "第" + (i + 1) + "步的类型不能为空");
                }
                
                TestCaseStep step = new TestCaseStep();
                step.setTestCaseId(testCaseId);
                step.setStepOrder(i + 1);
                step.setStepCode(dto.getStepCode());
                step.setStepName(dto.getStepName());
                step.setStepType(dto.getStepType().toUpperCase());
                step.setTargetCode(dto.getTargetCode());
                step.setTargetId(dto.getTargetId());
                step.setRequestOverride(dto.getRequestOverride());
                step.setAssertions(dto.getAssertions());
                step.setSkipOnError(dto.getSkipOnError() != null ? dto.getSkipOnError() : 0);
                step.setTimeoutMs(dto.getTimeoutMs());
                step.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
                
                testCaseStepRepository.save(step);
            }
        }
    }

    /**
     * 获取用例步骤
     */
    public List<TestCaseStepDTO> getSteps(Long testCaseId) {
        List<TestCaseStep> steps = testCaseStepRepository.findByTestCaseIdOrderByStepOrderAsc(testCaseId);
        return steps.stream().map(this::toStepDTO).collect(Collectors.toList());
    }

    // ============ 私有方法 ============

    private void copyFromDTO(TestCase testCase, TestCaseSaveDTO dto) {
        testCase.setCode(dto.getCode());
        testCase.setName(dto.getName());
        testCase.setDescription(dto.getDescription());
        testCase.setGroupName(dto.getGroupName());
        testCase.setPriority(dto.getPriority() != null ? dto.getPriority() : 3);
        testCase.setTags(dto.getTags());
        testCase.setTimeoutMs(dto.getTimeoutMs() != null ? dto.getTimeoutMs() : 60000);
        testCase.setFailureStrategy(StringUtils.hasText(dto.getFailureStrategy()) ? dto.getFailureStrategy() : "STOP");
        testCase.setNotifyOnFailure(dto.getNotifyOnFailure() != null ? dto.getNotifyOnFailure() : false);
        
        // 状态处理
        if (StringUtils.hasText(dto.getStatus())) {
            testCase.setStatus(Status.valueOf(dto.getStatus().toUpperCase()));
        } else if (testCase.getStatus() == null) {
            testCase.setStatus(Status.ACTIVE);
        }
    }

    private TestCaseListDTO toListDTO(TestCase testCase) {
        TestCaseListDTO dto = new TestCaseListDTO();
        dto.setId(testCase.getId());
        dto.setCode(testCase.getCode());
        dto.setName(testCase.getName());
        dto.setDescription(testCase.getDescription());
        dto.setGroupName(testCase.getGroupName());
        dto.setStatus(testCase.getStatus() != null ? testCase.getStatus().name() : null);
        dto.setPriority(testCase.getPriority());
        dto.setTags(testCase.getTags());
        dto.setTimeoutMs(testCase.getTimeoutMs());
        dto.setFailureStrategy(testCase.getFailureStrategy());
        dto.setNotifyOnFailure(testCase.getNotifyOnFailure());
        dto.setCreatedAt(testCase.getCreatedAt() != null ? testCase.getCreatedAt().format(DATE_FORMATTER) : null);
        dto.setCreatedByName(testCase.getCreatedByName());
        
        // 查询步骤数量
        dto.setStepCount(testCaseStepRepository.countByTestCaseId(testCase.getId()));
        
        return dto;
    }

    private TestCaseStepDTO toStepDTO(TestCaseStep step) {
        TestCaseStepDTO dto = new TestCaseStepDTO();
        dto.setId(step.getId());
        dto.setTestCaseId(step.getTestCaseId());
        dto.setStepOrder(step.getStepOrder());
        dto.setStepCode(step.getStepCode());
        dto.setStepName(step.getStepName());
        dto.setStepType(step.getStepType());
        dto.setTargetCode(step.getTargetCode());
        dto.setTargetId(step.getTargetId());
        dto.setRequestOverride(step.getRequestOverride());
        dto.setAssertions(step.getAssertions());
        dto.setSkipOnError(step.getSkipOnError());
        dto.setTimeoutMs(step.getTimeoutMs());
        dto.setEnabled(step.getEnabled());
        return dto;
    }

    private TestCaseDetailDTO toDetailDTO(TestCase testCase) {
        TestCaseDetailDTO dto = new TestCaseDetailDTO();
        dto.setId(testCase.getId());
        dto.setCode(testCase.getCode());
        dto.setName(testCase.getName());
        dto.setDescription(testCase.getDescription());
        dto.setGroupName(testCase.getGroupName());
        dto.setStatus(testCase.getStatus() != null ? testCase.getStatus().name() : null);
        dto.setPriority(testCase.getPriority());
        dto.setTags(testCase.getTags());
        dto.setTimeoutMs(testCase.getTimeoutMs());
        dto.setFailureStrategy(testCase.getFailureStrategy());
        dto.setNotifyOnFailure(testCase.getNotifyOnFailure());
        dto.setCreatedAt(testCase.getCreatedAt() != null ? testCase.getCreatedAt().format(DATE_FORMATTER) : null);
        dto.setCreatedByName(testCase.getCreatedByName());
        dto.setStepCount(testCaseStepRepository.countByTestCaseId(testCase.getId()));
        return dto;
    }
}
