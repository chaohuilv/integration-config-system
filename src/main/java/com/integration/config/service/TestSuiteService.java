package com.integration.config.service;

import com.integration.config.dto.TestSuiteDetailDTO;
import com.integration.config.dto.TestSuiteListDTO;
import com.integration.config.dto.TestSuiteSaveDTO;
import com.integration.config.entity.config.TestSuite;
import com.integration.config.entity.config.TestSuiteCase;
import com.integration.config.enums.ErrorCode;
import com.integration.config.enums.Status;
import com.integration.config.exception.BusinessException;
import com.integration.config.repository.config.TestCaseRepository;
import com.integration.config.repository.config.TestSuiteCaseRepository;
import com.integration.config.repository.config.TestSuiteRepository;
import com.integration.config.util.SnowflakeUtil;
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
import java.util.stream.Collectors;

/**
 * 测试套件 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestSuiteService {

    private final TestSuiteRepository testSuiteRepository;
    private final TestSuiteCaseRepository testSuiteCaseRepository;
    private final TestCaseRepository testCaseRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 分页查询测试套件
     */
    public Page<TestSuiteListDTO> pageQuery(String groupName, String status, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        // 处理空字符串为 null
        String groupParam = StringUtils.hasText(groupName) ? groupName : null;
        String statusParam = StringUtils.hasText(status) ? status : null;
        String keywordParam = StringUtils.hasText(keyword) ? keyword : null;
        
        Page<TestSuite> entityPage = testSuiteRepository.pageQuery(groupParam, statusParam, keywordParam, pageable);
        
        List<TestSuiteListDTO> dtoList = entityPage.getContent().stream()
                .map(this::toListDTO)
                .collect(Collectors.toList());
        
        return new PageImpl<>(dtoList, pageable, entityPage.getTotalElements());
    }

    /**
     * 获取套件详情
     */
    public TestSuiteDetailDTO getDetail(Long id) {
        TestSuite testSuite = testSuiteRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试套件不存在: " + id));
        
        TestSuiteDetailDTO dto = new TestSuiteDetailDTO();
        copyToDetailDTO(testSuite, dto);
        
        // 查询关联的用例ID列表
        List<TestSuiteCase> suiteCases = testSuiteCaseRepository.findByTestSuiteIdOrderByCaseOrderAsc(id);
        dto.setCaseIds(suiteCases.stream()
                .map(TestSuiteCase::getTestCaseId)
                .collect(Collectors.toList()));
        
        return dto;
    }

    /**
     * 根据编码获取详情
     */
    public TestSuiteDetailDTO getDetailByCode(String code) {
        TestSuite testSuite = testSuiteRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试套件不存在: " + code));
        return getDetail(testSuite.getId());
    }

    /**
     * 创建测试套件
     */
    @Transactional
    public TestSuite create(TestSuiteSaveDTO dto) {
        // 校验编码唯一性
        if (testSuiteRepository.existsByCode(dto.getCode())) {
            throw new BusinessException(ErrorCode.ALREADY_EXISTS, "套件编码已存在: " + dto.getCode());
        }
        
        TestSuite testSuite = new TestSuite();
        copyFromDTO(testSuite, dto);
        testSuite.setId(SnowflakeUtil.nextId());
        
        return testSuiteRepository.save(testSuite);
    }

    /**
     * 更新测试套件
     */
    @Transactional
    public TestSuite update(Long id, TestSuiteSaveDTO dto) {
        TestSuite testSuite = testSuiteRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试套件不存在: " + id));
        
        // 校验编码唯一性（排除自身）
        if (!testSuite.getCode().equals(dto.getCode())) {
            if (testSuiteRepository.existsByCode(dto.getCode())) {
                throw new BusinessException(ErrorCode.ALREADY_EXISTS, "套件编码已存在: " + dto.getCode());
            }
        }
        
        copyFromDTO(testSuite, dto);
        return testSuiteRepository.save(testSuite);
    }

    /**
     * 删除测试套件（同时删除关联关系）
     */
    @Transactional
    public void delete(Long id) {
        TestSuite testSuite = testSuiteRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试套件不存在: " + id));
        
        // 删除关联关系
        testSuiteCaseRepository.deleteByTestSuiteId(id);
        
        // 删除套件
        testSuiteRepository.delete(testSuite);
    }

    /**
     * 切换启用/禁用状态
     */
    @Transactional
    public TestSuite toggleStatus(Long id) {
        TestSuite testSuite = testSuiteRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试套件不存在: " + id));
        
        if (testSuite.getStatus() == Status.ACTIVE) {
            testSuite.setStatus(Status.INACTIVE);
        } else {
            testSuite.setStatus(Status.ACTIVE);
        }
        
        return testSuiteRepository.save(testSuite);
    }

    /**
     * 获取所有分组名称
     */
    public List<String> getGroups() {
        return testSuiteRepository.findAll().stream()
                .map(TestSuite::getGroupName)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 保存套件-用例关联（全量替换）
     */
    @Transactional
    public void saveCases(Long testSuiteId, List<Long> caseIds) {
        // 校验套件存在
        TestSuite testSuite = testSuiteRepository.findById(testSuiteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "测试套件不存在: " + testSuiteId));
        
        // 删除旧关联
        testSuiteCaseRepository.deleteByTestSuiteId(testSuiteId);
        
        // 保存新关联
        if (caseIds != null && !caseIds.isEmpty()) {
            // 校验用例存在且唯一
            List<Long> uniqueCaseIds = new ArrayList<>();
            for (int i = 0; i < caseIds.size(); i++) {
                Long caseId = caseIds.get(i);
                
                // 检查用例是否存在
                if (!testCaseRepository.existsById(caseId)) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在: " + caseId);
                }
                
                // 检查重复
                if (uniqueCaseIds.contains(caseId)) {
                    throw new BusinessException(ErrorCode.INVALID_PARAM, "用例ID重复: " + caseId);
                }
                uniqueCaseIds.add(caseId);
                
                // 创建关联
                TestSuiteCase suiteCase = TestSuiteCase.builder()
                        .id(SnowflakeUtil.nextId())
                        .testSuiteId(testSuiteId)
                        .testCaseId(caseId)
                        .caseOrder(i + 1)
                        .build();
                
                testSuiteCaseRepository.save(suiteCase);
            }
        }
    }

    /**
     * 获取套件下的用例ID列表
     */
    public List<Long> getCaseIds(Long testSuiteId) {
        return testSuiteCaseRepository.findByTestSuiteIdOrderByCaseOrderAsc(testSuiteId)
                .stream()
                .map(TestSuiteCase::getTestCaseId)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有启用的套件
     */
    public List<TestSuiteListDTO> getActiveSuites() {
        return testSuiteRepository.findByStatus(Status.ACTIVE).stream()
                .map(this::toListDTO)
                .collect(Collectors.toList());
    }

    // ============ 私有方法 ============

    private void copyFromDTO(TestSuite testSuite, TestSuiteSaveDTO dto) {
        testSuite.setCode(dto.getCode());
        testSuite.setName(dto.getName());
        testSuite.setDescription(dto.getDescription());
        testSuite.setGroupName(dto.getGroupName());
        testSuite.setExecutionMode(StringUtils.hasText(dto.getExecutionMode()) ? dto.getExecutionMode().toUpperCase() : "SEQUENTIAL");
        testSuite.setConcurrency(dto.getConcurrency() != null ? dto.getConcurrency() : 3);
        testSuite.setStopOnFirstFailure(dto.getStopOnFirstFailure() != null ? dto.getStopOnFirstFailure() : false);
        testSuite.setTimeoutMs(dto.getTimeoutMs() != null ? dto.getTimeoutMs() : 300000);
        testSuite.setNotifyOnComplete(dto.getNotifyOnComplete() != null ? dto.getNotifyOnComplete() : true);
        
        // 状态处理
        if (StringUtils.hasText(dto.getStatus())) {
            testSuite.setStatus(Status.valueOf(dto.getStatus().toUpperCase()));
        } else if (testSuite.getStatus() == null) {
            testSuite.setStatus(Status.ACTIVE);
        }
    }

    private TestSuiteListDTO toListDTO(TestSuite testSuite) {
        TestSuiteListDTO dto = new TestSuiteListDTO();
        dto.setId(testSuite.getId());
        dto.setCode(testSuite.getCode());
        dto.setName(testSuite.getName());
        dto.setDescription(testSuite.getDescription());
        dto.setGroupName(testSuite.getGroupName());
        dto.setStatus(testSuite.getStatus() != null ? testSuite.getStatus().name() : null);
        dto.setExecutionMode(testSuite.getExecutionMode());
        dto.setConcurrency(testSuite.getConcurrency());
        dto.setStopOnFirstFailure(testSuite.getStopOnFirstFailure());
        dto.setTimeoutMs(testSuite.getTimeoutMs());
        dto.setNotifyOnComplete(testSuite.getNotifyOnComplete());
        dto.setCreatedAt(testSuite.getCreatedAt() != null ? testSuite.getCreatedAt().format(DATE_FORMATTER) : null);
        dto.setCreatedByName(testSuite.getCreatedByName());
        
        // 查询用例数量
        dto.setCaseCount(testSuiteCaseRepository.countByTestSuiteId(testSuite.getId()));
        
        return dto;
    }

    private void copyToDetailDTO(TestSuite testSuite, TestSuiteDetailDTO dto) {
        dto.setId(testSuite.getId());
        dto.setCode(testSuite.getCode());
        dto.setName(testSuite.getName());
        dto.setDescription(testSuite.getDescription());
        dto.setGroupName(testSuite.getGroupName());
        dto.setStatus(testSuite.getStatus() != null ? testSuite.getStatus().name() : null);
        dto.setExecutionMode(testSuite.getExecutionMode());
        dto.setConcurrency(testSuite.getConcurrency());
        dto.setStopOnFirstFailure(testSuite.getStopOnFirstFailure());
        dto.setTimeoutMs(testSuite.getTimeoutMs());
        dto.setNotifyOnComplete(testSuite.getNotifyOnComplete());
        dto.setCreatedAt(testSuite.getCreatedAt() != null ? testSuite.getCreatedAt().format(DATE_FORMATTER) : null);
        dto.setCreatedByName(testSuite.getCreatedByName());
        dto.setCaseCount(testSuiteCaseRepository.countByTestSuiteId(testSuite.getId()));
    }
}
