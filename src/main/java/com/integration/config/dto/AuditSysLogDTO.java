package com.integration.config.dto;

import com.integration.config.entity.log.AuditSysLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志 DTO（前端展示用）
 */
@Data
public class AuditSysLogDTO {

    private Long id;
    private String operateType;    // 操作类型
    private String module;         // 模块
    private String description;    // 描述
    private String targetType;     // 目标类型
    private String targetId;      // 目标ID
    private String targetName;     // 目标名称
    private Long userId;          // 操作用户ID
    private String userCode;       // 操作用户编码
    private String userName;       // 操作用户名称
    private String clientIp;        // 客户端IP
    private String result;         // 操作结果
    private String errorMsg;       // 错误信息
    private Long costTime;         // 耗时(ms)
    private LocalDateTime operateTime; // 操作时间
    private String requestUri;      // 请求URI
    private String requestMethod;   // 请求方法
    private String requestParams;   // 请求参数（JSON body 或 query string）
    private String newData;         // 操作后返回的数据

    /** 操作类型标签颜色 */
    public String getTypeTag() {
        return switch (operateType) {
            case "LOGIN", "LOGOUT" -> "tag-blue";
            case "CREATE" -> "tag-green";
            case "UPDATE" -> "tag-orange";
            case "DELETE" -> "tag-red";
            case "IMPORT" -> "tag-purple";
            default -> "tag-gray";
        };
    }

    /** 结果标签颜色 */
    public String getResultTag() {
        return "SUCCESS".equals(result) ? "tag-green" : "tag-red";
    }

    public static AuditSysLogDTO from(AuditSysLog entity) {
        AuditSysLogDTO dto = new AuditSysLogDTO();
        dto.setId(entity.getId());
        dto.setOperateType(entity.getOperateType());
        dto.setModule(entity.getModule());
        dto.setDescription(entity.getDescription());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetId(entity.getTargetId());
        dto.setTargetName(entity.getTargetName());
        dto.setUserId(entity.getUserId());
        dto.setUserCode(entity.getUserCode());
        dto.setUserName(entity.getUserName());
        dto.setClientIp(entity.getClientIp());
        dto.setResult(entity.getResult());
        dto.setErrorMsg(entity.getErrorMsg());
        dto.setCostTime(entity.getCostTime());
        dto.setOperateTime(entity.getOperateTime());
        dto.setRequestUri(entity.getRequestUri());
        dto.setRequestMethod(entity.getRequestMethod());
        dto.setRequestParams(entity.getRequestParams());
        dto.setNewData(entity.getNewData());
        return dto;
    }
}
