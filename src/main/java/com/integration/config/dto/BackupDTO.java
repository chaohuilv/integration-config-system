package com.integration.config.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 备份数据 DTO
 * 包含所有需要备份的配置数据
 */
@Data
public class BackupDTO {
    
    /** 备份格式版本 */
    private String version = "1.0";
    
    /** 导出时间 */
    private LocalDateTime exportedAt;
    
    /** 导出人 */
    private String exportedBy;
    
    /** 备份数据 */
    private BackupData data;
    
    @Data
    public static class BackupData {
        /** 菜单列表 */
        private List<Map<String, Object>> menus;
        
        /** 权限列表 */
        private List<Map<String, Object>> permissions;
        
        /** 角色列表 */
        private List<Map<String, Object>> roles;
        
        /** 角色菜单关联 */
        private List<Map<String, Object>> roleMenus;
        
        /** 角色权限关联 */
        private List<Map<String, Object>> rolePermissions;
        
        /** 用户列表 */
        private List<Map<String, Object>> users;
        
        /** 用户角色关联 */
        private List<Map<String, Object>> userRoles;
        
        /** 接口配置列表 */
        private List<Map<String, Object>> apiConfigs;
        
        /** 接口角色关联 */
        private List<Map<String, Object>> apiRoles;
        
        /** 环境配置列表 */
        private List<Map<String, Object>> environments;
        
        /** 场景列表 */
        private List<Map<String, Object>> scenarios;
        
        /** 场景步骤列表 */
        private List<Map<String, Object>> scenarioSteps;
        
        /** Mock配置列表 */
        private List<Map<String, Object>> mockConfigs;
        
        /** 告警规则列表 */
        private List<Map<String, Object>> alertRules;
        
        /** IP白名单规则列表 */
        private List<Map<String, Object>> ipWhitelistRules;
        
        /** 测试用例列表 */
        private List<Map<String, Object>> testCases;
        
        /** 测试用例步骤列表 */
        private List<Map<String, Object>> testCaseSteps;
        
        /** 测试套件列表 */
        private List<Map<String, Object>> testSuites;
        
        /** 测试套件用例关联列表 */
        private List<Map<String, Object>> testSuiteCases;
    }
}
