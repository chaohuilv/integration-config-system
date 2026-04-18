package com.integration.config.entity.log;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.integration.config.util.SnowflakeUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统审计日志实体（Log 数据库）
 * 记录谁在什么时间操作了什么
 */
@Entity
@Table(name = "AUDIT_LOG", indexes = {
    @Index(name = "IDX_AUDIT_USER", columnList = "USER_CODE"),
    @Index(name = "IDX_AUDIT_TIME", columnList = "OPERATE_TIME"),
    @Index(name = "IDX_AUDIT_TYPE", columnList = "OPERATE_TYPE"),
    @Index(name = "IDX_AUDIT_TARGET", columnList = "TARGET_TYPE,TARGET_ID")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditSysLog {

    /** 主键ID，雪花算法生成 */
    @Id
    @Column(name = "ID")
    private Long id;

    /** 操作类型：CREATE/UPDATE/DELETE/LOGIN/LOGOUT/QUERY/IMPORT/EXPORT/ENABLE/DISABLE */
    @Column(name = "OPERATE_TYPE", nullable = false, length = 20)
    private String operateType;

    /** 操作模块：API_CONFIG/USER/ENVIRONMENT/SYSTEM */
    @Column(name = "MODULE", length = 30)
    private String module;

    /** 操作描述 */
    @Column(name = "DESCRIPTION", length = 4000)
    private String description;

    /** 操作对象类型 */
    @Column(name = "TARGET_TYPE", length = 30)
    private String targetType;

    /** 操作对象ID */
    @Column(name = "TARGET_ID", length = 50)
    private String targetId;

    /** 操作对象名称/标识 */
    @Column(name = "TARGET_NAME", length = 100)
    private String targetName;

    /** 操作前数据（JSON格式，用于UPDATE/DELETE） */
    @Column(name = "OLD_DATA", columnDefinition = "TEXT")
    private String oldData;

    /** 操作后数据（JSON格式，用于CREATE/UPDATE） */
    @Column(name = "NEW_DATA", columnDefinition = "TEXT")
    private String newData;

    /** 操作用户ID */
    @Column(name = "USER_ID")
    private Long userId;

    /** 操作用户编码 */
    @Column(name = "USER_CODE", length = 50)
    private String userCode;

    /** 操作用户名称 */
    @Column(name = "USER_NAME", length = 50)
    private String userName;

    /** 客户端IP地址 */
    @Column(name = "CLIENT_IP", length = 50)
    private String clientIp;

    /** 用户代理（浏览器/设备信息） */
    @Column(name = "USER_AGENT", length = 500)
    private String userAgent;

    /** 请求方法：GET/POST/PUT/DELETE */
    @Column(name = "REQUEST_METHOD", length = 10)
    private String requestMethod;

    /** 请求URI */
    @Column(name = "REQUEST_URI", length = 200)
    private String requestUri;

    /** 请求参数（JSON格式） */
    @Column(name = "REQUEST_PARAMS", columnDefinition = "TEXT")
    private String requestParams;

    /** 操作结果：SUCCESS/FAIL */
    @Column(name = "RESULT", length = 10)
    private String result;

    /** 错误信息（操作失败时） */
    @Column(name = "ERROR_MSG", columnDefinition = "TEXT")
    private String errorMsg;

    /** 操作耗时（毫秒） */
    @Column(name = "COST_TIME")
    private Long costTime;

    /** 操作时间 */
    @Column(name = "OPERATE_TIME", nullable = false)
    private LocalDateTime operateTime;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = SnowflakeUtil.nextId();
        }
        if (operateTime == null) {
            operateTime = LocalDateTime.now();
        }
    }
}
