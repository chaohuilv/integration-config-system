package com.integration.config.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.integration.config.dto.BackupDTO;
import com.integration.config.entity.config.*;
import com.integration.config.repository.config.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 备份恢复服务
 * 全量配置 JSON 导出/导入
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final ObjectMapper objectMapper;
    
    // 配置相关 Repository
    private final MenuRepository menuRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ApiConfigRepository apiConfigRepository;
    private final ApiRoleRepository apiRoleRepository;
    private final EnvironmentRepository environmentRepository;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioStepRepository scenarioStepRepository;
    private final MockConfigRepository mockConfigRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final IpWhitelistRuleRepository ipWhitelistRuleRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestCaseStepRepository testCaseStepRepository;
    private final TestSuiteRepository testSuiteRepository;
    private final TestSuiteCaseRepository testSuiteCaseRepository;

    /**
     * 导出全量配置为 JSON
     */
    public String exportAll(String exportedBy) {
        log.info("开始导出全量配置，导出人: {}", exportedBy);
        
        BackupDTO backupDTO = new BackupDTO();
        backupDTO.setVersion("1.0");
        backupDTO.setExportedAt(LocalDateTime.now());
        backupDTO.setExportedBy(exportedBy);
        
        BackupDTO.BackupData data = new BackupDTO.BackupData();
        
        // 导出所有配置数据
        data.setMenus(toMapList(menuRepository.findAll()));
        data.setPermissions(toMapList(permissionRepository.findAll()));
        data.setRoles(toMapList(roleRepository.findAll()));
        data.setRoleMenus(toMapList(roleMenuRepository.findAll()));
        data.setRolePermissions(toMapList(rolePermissionRepository.findAll()));
        data.setUsers(toMapList(userRepository.findAll()));
        data.setUserRoles(toMapList(userRoleRepository.findAll()));
        data.setApiConfigs(toMapList(apiConfigRepository.findAll()));
        data.setApiRoles(toMapList(apiRoleRepository.findAll()));
        data.setEnvironments(toMapList(environmentRepository.findAll()));
        data.setScenarios(toMapList(scenarioRepository.findAll()));
        data.setScenarioSteps(toMapList(scenarioStepRepository.findAll()));
        data.setMockConfigs(toMapList(mockConfigRepository.findAll()));
        data.setAlertRules(toMapList(alertRuleRepository.findAll()));
        data.setIpWhitelistRules(toMapList(ipWhitelistRuleRepository.findAll()));
        data.setTestCases(toMapList(testCaseRepository.findAll()));
        data.setTestCaseSteps(toMapList(testCaseStepRepository.findAll()));
        data.setTestSuites(toMapList(testSuiteRepository.findAll()));
        data.setTestSuiteCases(toMapList(testSuiteCaseRepository.findAll()));
        
        backupDTO.setData(data);
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            
            String json = mapper.writeValueAsString(backupDTO);
            log.info("导出完成，总大小: {} bytes", json.length());
            return json;
        } catch (Exception e) {
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取导出统计信息
     */
    public Map<String, Long> getExportStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("menus", (long) menuRepository.findAll().size());
        stats.put("permissions", (long) permissionRepository.findAll().size());
        stats.put("roles", (long) roleRepository.findAll().size());
        stats.put("users", (long) userRepository.findAll().size());
        stats.put("apiConfigs", (long) apiConfigRepository.findAll().size());
        stats.put("environments", (long) environmentRepository.findAll().size());
        stats.put("scenarios", (long) scenarioRepository.findAll().size());
        stats.put("scenarioSteps", (long) scenarioStepRepository.findAll().size());
        stats.put("mockConfigs", (long) mockConfigRepository.findAll().size());
        stats.put("alertRules", (long) alertRuleRepository.findAll().size());
        stats.put("ipWhitelistRules", (long) ipWhitelistRuleRepository.findAll().size());
        stats.put("testCases", (long) testCaseRepository.findAll().size());
        stats.put("testCaseSteps", (long) testCaseStepRepository.findAll().size());
        stats.put("testSuites", (long) testSuiteRepository.findAll().size());
        stats.put("testSuiteCases", (long) testSuiteCaseRepository.findAll().size());
        return stats;
    }

    /**
     * 导入全量配置
     */
    @Transactional
    public ImportResult importAll(String json, boolean clearExisting) {
        log.info("开始导入配置，清空现有数据: {}", clearExisting);
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            
            BackupDTO backupDTO = mapper.readValue(json, BackupDTO.class);
            BackupDTO.BackupData data = backupDTO.getData();
            
            ImportResult result = new ImportResult();
            long startTime = System.currentTimeMillis();
            
            if (clearExisting) {
                clearAllData();
            }
            
            // ID映射：旧ID -> 新ID
            Map<Long, Long> menuIdMap = new HashMap<>();
            Map<Long, Long> permIdMap = new HashMap<>();
            Map<Long, Long> roleIdMap = new HashMap<>();
            Map<Long, Long> userIdMap = new HashMap<>();
            Map<Long, Long> apiIdMap = new HashMap<>();
            Map<Long, Long> scenarioIdMap = new HashMap<>();
            Map<Long, Long> testCaseIdMap = new HashMap<>();
            Map<Long, Long> testSuiteIdMap = new HashMap<>();
            
            // 1. 基础配置
            result.addCounts("menus", importMenus(data.getMenus(), menuIdMap));
            result.addCounts("permissions", importPermissions(data.getPermissions(), permIdMap));
            result.addCounts("roles", importRoles(data.getRoles(), roleIdMap));
            result.addCounts("users", importUsers(data.getUsers(), userIdMap));
            
            // 2. 业务配置
            result.addCounts("environments", importEnvironments(data.getEnvironments()));
            result.addCounts("apiConfigs", importApiConfigs(data.getApiConfigs(), apiIdMap));
            result.addCounts("scenarios", importScenarios(data.getScenarios(), scenarioIdMap));
            result.addCounts("mockConfigs", importMockConfigs(data.getMockConfigs()));
            result.addCounts("alertRules", importAlertRules(data.getAlertRules()));
            result.addCounts("ipWhitelistRules", importIpWhitelistRules(data.getIpWhitelistRules()));
            result.addCounts("testCases", importTestCases(data.getTestCases(), testCaseIdMap));
            result.addCounts("testSuites", importTestSuites(data.getTestSuites(), testSuiteIdMap));
            
            // 3. 关联配置（使用 code 匹配，避免跨系统 ID 冲突）
            result.addCounts("roleMenus", importRoleMenus(data.getRoleMenus(), roleIdMap, menuIdMap));
            result.addCounts("rolePermissions", importRolePermissions(data.getRolePermissions(), roleIdMap));
            result.addCounts("userRoles", importUserRoles(data.getUserRoles(), userIdMap, roleIdMap));
            result.addCounts("apiRoles", importApiRoles(data.getApiRoles(), apiIdMap, roleIdMap));
            result.addCounts("scenarioSteps", importScenarioSteps(data.getScenarioSteps(), scenarioIdMap));
            result.addCounts("testCaseSteps", importTestCaseSteps(data.getTestCaseSteps(), testCaseIdMap));
            result.addCounts("testSuiteCases", importTestSuiteCases(data.getTestSuiteCases(), testSuiteIdMap, testCaseIdMap));
            
            result.setSuccess(true);
            result.setMessage("导入完成");
            result.setCostMs(System.currentTimeMillis() - startTime);
            
            log.info("导入完成，耗时: {}ms", result.getCostMs());
            return result;
            
        } catch (Exception e) {
            log.error("导入失败", e);
            throw new RuntimeException("导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清空所有配置数据
     */
    private void clearAllData() {
        log.info("清空所有配置数据...");
        testSuiteCaseRepository.deleteAll();
        testCaseStepRepository.deleteAll();
        testSuiteRepository.deleteAll();
        testCaseRepository.deleteAll();
        ipWhitelistRuleRepository.deleteAll();
        alertRuleRepository.deleteAll();
        mockConfigRepository.deleteAll();
        scenarioStepRepository.deleteAll();
        scenarioRepository.deleteAll();
        apiRoleRepository.deleteAll();
        apiConfigRepository.deleteAll();
        environmentRepository.deleteAll();
        userRoleRepository.deleteAll();
        rolePermissionRepository.deleteAll();
        roleMenuRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
        menuRepository.deleteAll();
        log.info("清空完成");
    }

    // ==================== 各表导入方法 ====================

    private int importMenus(List<Map<String, Object>> list, Map<Long, Long> idMap) {
        if (list == null || list.isEmpty()) return 0;
        List<Menu> entities = new ArrayList<>();
        for (Map<String, Object> map : list) {
            Long oldId = parseLong(map.get("id"));
            Menu menu = mapToEntity(map, Menu.class);
            menu.setId(null);
            Menu saved = menuRepository.save(menu);
            idMap.put(oldId, saved.getId());
            entities.add(saved);
        }
        return entities.size();
    }

    private int importPermissions(List<Map<String, Object>> list, Map<Long, Long> idMap) {
        if (list == null || list.isEmpty()) return 0;
        List<Permission> entities = new ArrayList<>();
        for (Map<String, Object> map : list) {
            Long oldId = parseLong(map.get("id"));
            Permission perm = mapToEntity(map, Permission.class);
            perm.setId(null);
            Permission saved = permissionRepository.save(perm);
            idMap.put(oldId, saved.getId());
            entities.add(saved);
        }
        return entities.size();
    }

    private int importRoles(List<Map<String, Object>> list, Map<Long, Long> idMap) {
        if (list == null || list.isEmpty()) return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            Long oldId = parseLong(map.get("id"));
            Role role = mapToEntity(map, Role.class);
            role.setId(null);
            Role saved = roleRepository.save(role);
            idMap.put(oldId, saved.getId());
            count++;
        }
        return count;
    }

    private int importUsers(List<Map<String, Object>> list, Map<Long, Long> idMap) {
        if (list == null || list.isEmpty()) return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            Long oldId = parseLong(map.get("id"));
            User user = mapToEntity(map, User.class);
            user.setId(null);
            User saved = userRepository.save(user);
            idMap.put(oldId, saved.getId());
            count++;
        }
        return count;
    }

    private int importEnvironments(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return 0;
        List<Environment> entities = new ArrayList<>();
        for (Map<String, Object> map : list) {
            Environment env = mapToEntity(map, Environment.class);
            env.setId(null);
            entities.add(env);
        }
        environmentRepository.saveAll(entities);
        return entities.size();
    }

    private int importApiConfigs(List<Map<String, Object>> list, Map<Long, Long> idMap) {
        if (list == null || list.isEmpty()) return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            Long oldId = parseLong(map.get("id"));
            ApiConfig api = mapToEntity(map, ApiConfig.class);
            api.setId(null);
            ApiConfig saved = apiConfigRepository.save(api);
            idMap.put(oldId, saved.getId());
            count++;
        }
        return count;
    }

    private int importScenarios(List<Map<String, Object>> list, Map<Long, Long> idMap) {
        if (list == null || list.isEmpty()) return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            Long oldId = parseLong(map.get("id"));
            Scenario scenario = mapToEntity(map, Scenario.class);
            scenario.setId(null);
            Scenario saved = scenarioRepository.save(scenario);
            idMap.put(oldId, saved.getId());
            count++;
        }
        return count;
    }

    private int importMockConfigs(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return 0;
        List<MockConfig> entities = new ArrayList<>();
        for (Map<String, Object> map : list) {
            MockConfig mock = mapToEntity(map, MockConfig.class);
            mock.setId(null);
            entities.add(mock);
        }
        mockConfigRepository.saveAll(entities);
        return entities.size();
    }

    private int importAlertRules(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return 0;
        List<AlertRule> entities = new ArrayList<>();
        for (Map<String, Object> map : list) {
            AlertRule rule = mapToEntity(map, AlertRule.class);
            rule.setId(null);
            entities.add(rule);
        }
        alertRuleRepository.saveAll(entities);
        return entities.size();
    }

    private int importIpWhitelistRules(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return 0;
        List<IpWhitelistRule> entities = new ArrayList<>();
        for (Map<String, Object> map : list) {
            IpWhitelistRule rule = mapToEntity(map, IpWhitelistRule.class);
            rule.setId(null);
            entities.add(rule);
        }
        ipWhitelistRuleRepository.saveAll(entities);
        return entities.size();
    }

    private int importTestCases(List<Map<String, Object>> list, Map<Long, Long> idMap) {
        if (list == null || list.isEmpty()) return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            Long oldId = parseLong(map.get("id"));
            TestCase tc = mapToEntity(map, TestCase.class);
            tc.setId(null);
            TestCase saved = testCaseRepository.save(tc);
            idMap.put(oldId, saved.getId());
            count++;
        }
        return count;
    }

    private int importTestSuites(List<Map<String, Object>> list, Map<Long, Long> idMap) {
        if (list == null || list.isEmpty()) return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            Long oldId = parseLong(map.get("id"));
            TestSuite ts = mapToEntity(map, TestSuite.class);
            ts.setId(null);
            TestSuite saved = testSuiteRepository.save(ts);
            idMap.put(oldId, saved.getId());
            count++;
        }
        return count;
    }

    // ==================== 关联表导入方法 ====================

    private int importRoleMenus(List<Map<String, Object>> list, Map<Long, Long> roleIdMap, Map<Long, Long> menuIdMap) {
        if (list == null || list.isEmpty()) return 0;
        List<RoleMenu> entities = new ArrayList<>();
        for (Map<String, Object> map : list) {
            Long oldRoleId = parseLong(map.get("roleId"));
            Long oldMenuId = parseLong(map.get("menuId"));
            Long newRoleId = roleIdMap.get(oldRoleId);
            Long newMenuId = menuIdMap.get(oldMenuId);
            if (newRoleId != null && newMenuId != null) {
                RoleMenu rm = new RoleMenu();
                rm.setRoleId(newRoleId);
                rm.setMenuId(newMenuId);
                entities.add(rm);
            }
        }
        roleMenuRepository.saveAll(entities);
        return entities.size();
    }

    private int importRolePermissions(List<Map<String, Object>> list, Map<Long, Long> roleIdMap) {
        if (list == null || list.isEmpty()) return 0;
        // 通过 code 匹配权限（跨系统 ID 不同但 code 稳定）
        List<Permission> allPerms = permissionRepository.findAll();
        Map<String, Long> codeToPermId = new HashMap<>();
        for (Permission p : allPerms) codeToPermId.put(p.getCode(), p.getId());
        
        List<RolePermission> entities = new ArrayList<>();
        for (Map<String, Object> map : list) {
            Long oldRoleId = parseLong(map.get("roleId"));
            Long newRoleId = roleIdMap.get(oldRoleId);
            String permCode = String.valueOf(map.get("permissionCode"));
            Long newPermId = codeToPermId.get(permCode);
            if (newRoleId != null && newPermId != null) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(newRoleId);
                rp.setPermissionId(newPermId);
                entities.add(rp);
            }
        }
        rolePermissionRepository.saveAll(entities);
        return entities.size();
    }

    private int importUserRoles(List<Map<String, Object>> list, Map<Long, Long> userIdMap, Map<Long, Long> roleIdMap) {
        if (list == null || list.isEmpty()) return 0;
        List<UserRole> entities = new ArrayList<>();
        for (Map<String, Object> map : list) {
            Long oldUserId = parseLong(map.get("userId"));
            Long oldRoleId = parseLong(map.get("roleId"));
            Long newUserId = userIdMap.get(oldUserId);
            Long newRoleId = roleIdMap.get(oldRoleId);
            if (newUserId != null && newRoleId != null) {
                UserRole ur = new UserRole();
                ur.setUserId(newUserId);
                ur.setRoleId(newRoleId);
                entities.add(ur);
            }
        }
        userRoleRepository.saveAll(entities);
        return entities.size();
    }

    private int importApiRoles(List<Map<String, Object>> list, Map<Long, Long> apiIdMap, Map<Long, Long> roleIdMap) {
        if (list == null || list.isEmpty()) return 0;
        List<ApiRole> entities = new ArrayList<>();
        for (Map<String, Object> map : list) {
            Long oldApiId = parseLong(map.get("apiId"));
            Long oldRoleId = parseLong(map.get("roleId"));
            Long newApiId = apiIdMap.get(oldApiId);
            Long newRoleId = roleIdMap.get(oldRoleId);
            if (newApiId != null && newRoleId != null) {
                ApiRole ar = new ApiRole();
                ar.setApiId(newApiId);
                ar.setRoleId(newRoleId);
                entities.add(ar);
            }
        }
        apiRoleRepository.saveAll(entities);
        return entities.size();
    }

    private int importScenarioSteps(List<Map<String, Object>> list, Map<Long, Long> scenarioIdMap) {
        if (list == null || list.isEmpty()) return 0;
        List<ScenarioStep> entities = new ArrayList<>();
        for (Map<String, Object> map : list) {
            Long oldScenarioId = parseLong(map.get("scenarioId"));
            Long newScenarioId = scenarioIdMap.get(oldScenarioId);
            if (newScenarioId != null) {
                ScenarioStep step = mapToEntity(map, ScenarioStep.class);
                step.setId(null);
                step.setScenarioId(newScenarioId);
                entities.add(step);
            }
        }
        scenarioStepRepository.saveAll(entities);
        return entities.size();
    }

    private int importTestCaseSteps(List<Map<String, Object>> list, Map<Long, Long> testCaseIdMap) {
        if (list == null || list.isEmpty()) return 0;
        List<TestCaseStep> entities = new ArrayList<>();
        for (Map<String, Object> map : list) {
            Long oldCaseId = parseLong(map.get("testCaseId"));
            Long newCaseId = testCaseIdMap.get(oldCaseId);
            if (newCaseId != null) {
                TestCaseStep step = mapToEntity(map, TestCaseStep.class);
                step.setId(null);
                step.setTestCaseId(newCaseId);
                entities.add(step);
            }
        }
        testCaseStepRepository.saveAll(entities);
        return entities.size();
    }

    private int importTestSuiteCases(List<Map<String, Object>> list, Map<Long, Long> testSuiteIdMap, Map<Long, Long> testCaseIdMap) {
        if (list == null || list.isEmpty()) return 0;
        List<TestSuiteCase> entities = new ArrayList<>();
        for (Map<String, Object> map : list) {
            Long oldSuiteId = parseLong(map.get("testSuiteId"));
            Long oldCaseId = parseLong(map.get("testCaseId"));
            Long newSuiteId = testSuiteIdMap.get(oldSuiteId);
            Long newCaseId = testCaseIdMap.get(oldCaseId);
            if (newSuiteId != null && newCaseId != null) {
                TestSuiteCase tsc = new TestSuiteCase();
                tsc.setTestSuiteId(newSuiteId);
                tsc.setTestCaseId(newCaseId);
                if (map.get("caseOrder") != null) {
                    tsc.setCaseOrder(parseInt(map.get("caseOrder")));
                }
                entities.add(tsc);
            }
        }
        testSuiteCaseRepository.saveAll(entities);
        return entities.size();
    }

    // ==================== 工具方法 ====================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(List<?> entities) {
        if (entities == null) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object entity : entities) {
            Map<String, Object> map = objectMapper.convertValue(entity, Map.class);
            result.add(map);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> T mapToEntity(Map<String, Object> map, Class<T> clazz) {
        return objectMapper.convertValue(map, clazz);
    }

    /**
     * 安全解析 Long 类型 ID，兼容 Number / String / null
     */
    private Long parseLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) {
            String s = ((String) val).trim();
            return s.isEmpty() ? null : new java.math.BigInteger(s).longValue();
        }
        return null;
    }

    /**
     * 安全解析 Integer 类型，兼容 Number / String / null
     */
    private Integer parseInt(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            String s = ((String) val).trim();
            return s.isEmpty() ? null : new java.math.BigDecimal(s).intValue();
        }
        return null;
    }

    /**
     * 导入结果
     */
    @lombok.Data
    public static class ImportResult {
        private boolean success;
        private String message;
        private long costMs;
        private Map<String, Integer> counts = new LinkedHashMap<>();
        
        public void addCounts(String key, int count) {
            counts.put(key, count);
        }
    }
}
