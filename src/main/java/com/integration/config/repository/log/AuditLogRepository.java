package com.integration.config.repository.log;

import com.integration.config.entity.log.AuditSysLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * 审计日志 Repository
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditSysLog, Long>, JpaSpecificationExecutor<AuditSysLog> {
}
