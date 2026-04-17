package com.integration.config.service;

import com.integration.config.dto.AuditSysLogDTO;
import com.integration.config.entity.log.AuditSysLog;
import com.integration.config.repository.log.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审计日志 Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditSysLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * 条件分页查询
     */
    public Page<AuditSysLogDTO> search(
            String userCode,
            String operateType,
            String module,
            String targetType,
            String result,
            String keyword,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int page,
            int size
    ) {
        Specification<AuditSysLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userCode != null && !userCode.isBlank()) {
                predicates.add(cb.like(root.get("userCode"), "%" + userCode.trim() + "%"));
            }
            if (operateType != null && !operateType.isBlank()) {
                predicates.add(cb.equal(root.get("operateType"), operateType));
            }
            if (module != null && !module.isBlank()) {
                predicates.add(cb.equal(root.get("module"), module));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (result != null && !result.isBlank()) {
                predicates.add(cb.equal(root.get("result"), result));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("description"), kw),
                        cb.like(root.get("userName"), kw),
                        cb.like(root.get("clientIp"), kw)
                ));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("operateTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("operateTime"), endTime));
            }

            query.orderBy(cb.desc(root.get("operateTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AuditSysLog> pageResult = auditLogRepository.findAll(
                spec,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "operateTime"))
        );

        return pageResult.map(AuditSysLogDTO::from);
    }

    /**
     * 批量删除
     */
    @Transactional
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        auditLogRepository.deleteAllById(ids);
        return ids.size();
    }

    /**
     * 根据ID列表查询（用于导出）
     */
    public List<AuditSysLogDTO> listByIds(List<Long> ids) {
        return auditLogRepository.findAllById(ids).stream()
                .map(AuditSysLogDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID获取详情
     */
    public AuditSysLogDTO getById(Long id) {
        return auditLogRepository.findById(id)
                .map(AuditSysLogDTO::from)
                .orElse(null);
    }

    /**
     * 导出全部（按条件）
     */
    public List<AuditSysLogDTO> export(
            String userCode,
            String operateType,
            String module,
            String targetType,
            String result,
            String keyword,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        Specification<AuditSysLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userCode != null && !userCode.isBlank()) {
                predicates.add(cb.like(root.get("userCode"), "%" + userCode.trim() + "%"));
            }
            if (operateType != null && !operateType.isBlank()) {
                predicates.add(cb.equal(root.get("operateType"), operateType));
            }
            if (module != null && !module.isBlank()) {
                predicates.add(cb.equal(root.get("module"), module));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (result != null && !result.isBlank()) {
                predicates.add(cb.equal(root.get("result"), result));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("description"), kw),
                        cb.like(root.get("userName"), kw),
                        cb.like(root.get("clientIp"), kw)
                ));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("operateTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("operateTime"), endTime));
            }

            query.orderBy(cb.desc(root.get("operateTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "operateTime"))
                .stream()
                .map(AuditSysLogDTO::from)
                .collect(Collectors.toList());
    }
}
