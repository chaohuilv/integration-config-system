package com.integration.config.controller;

import com.integration.config.annotation.RequirePermission;
import com.integration.config.enums.Status;
import com.integration.config.repository.config.ApiConfigRepository;
import com.integration.config.repository.config.EnvironmentRepository;
import com.integration.config.repository.config.UserRepository;
import com.integration.config.repository.config.RoleRepository;
import com.integration.config.repository.log.AuditLogRepository;
import com.integration.config.repository.log.InvokeLogRepository;
import com.integration.config.vo.ResultVO;
import com.sun.management.OperatingSystemMXBean;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 实时大盘 Controller
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {

    private final InvokeLogRepository invokeLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final ApiConfigRepository apiConfigRepository;
    private final EnvironmentRepository environmentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RedisConnectionFactory redisConnectionFactory;

    /**
     * 总览数据（卡片）
     */
    @GetMapping("/overview")
    @RequirePermission("dashboard:view")
    public ResultVO<Map<String, Object>> getOverview() {
        Map<String, Object> data = new LinkedHashMap<>();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // 接口总数
        long apiTotal = apiConfigRepository.count();
        long apiActive = apiConfigRepository.findByStatusOrderByCreatedAtDesc(Status.ACTIVE).size();
        data.put("apiTotal", apiTotal);
        data.put("apiActive", apiActive);
        data.put("apiInactive", apiTotal - apiActive);

        // 用户/角色
        data.put("userCount", userRepository.count());
        data.put("roleCount", roleRepository.count());
        data.put("envCount", environmentRepository.count());

        // 调用统计
        Long invokeTotal = invokeLogRepository.countAllSuccess() + invokeLogRepository.countAllFail();
        Long invokeSuccess = invokeLogRepository.countAllSuccess();
        Long invokeFail = invokeLogRepository.countAllFail();
        Long todayInvoke = invokeLogRepository.countTodaySuccess(todayStart) + invokeLogRepository.countTodayFail(todayStart);
        Long todaySuccess = invokeLogRepository.countTodaySuccess(todayStart);
        Long todayFail = invokeLogRepository.countTodayFail(todayStart);
        Double avgCost = invokeLogRepository.avgCostTimeToday(todayStart);

        data.put("invokeTotal", invokeTotal);
        data.put("invokeSuccess", invokeSuccess);
        data.put("invokeFail", invokeFail);
        data.put("todayInvoke", todayInvoke);
        data.put("todaySuccess", todaySuccess);
        data.put("todayFail", todayFail);
        data.put("todayAvgCost", avgCost != null ? Math.round(avgCost) : 0);
        data.put("todaySuccessRate", todayInvoke > 0 ? Math.round(todaySuccess * 10000.0 / todayInvoke) / 100.0 : 100.0);

        // 审计日志
        Long todayAudit = auditLogRepository.countToday(todayStart);
        data.put("auditToday", todayAudit);
        data.put("auditTotal", auditLogRepository.count());

        return ResultVO.success(data);
    }

    /**
     * 调用趋势（24小时）
     */
    @GetMapping("/invoke-trend")
    @RequirePermission("dashboard:view")
    public ResultVO<List<Map<String, Object>>> getInvokeTrend(@RequestParam(defaultValue = "24") Integer hours) {
        LocalDateTime start = LocalDateTime.now().minusHours(hours);
        List<Object[]> rows = invokeLogRepository.countHourlyTrend(start);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", row[0]);
            item.put("total", row[1]);
            item.put("success", row[2]);
            item.put("fail", row[3]);
            result.add(item);
        }
        return ResultVO.success(result);
    }

    /**
     * 接口调用排行
     */
    @GetMapping("/top-apis")
    @RequirePermission("dashboard:view")
    public ResultVO<List<Map<String, Object>>> getTopApis(@RequestParam(defaultValue = "10") Integer limit) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        List<Object[]> rows = invokeLogRepository.topApisByCalls(start);
        List<Map<String, Object>> result = new ArrayList<>();
        int count = 0;
        for (Object[] row : rows) {
            if (count++ >= limit) break;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("apiCode", row[0]);
            item.put("callCount", row[1]);
            item.put("successCount", row[2]);
            item.put("failCount", row[3]);
            item.put("avgCost", row[4]);
            result.add(item);
        }
        return ResultVO.success(result);
    }

    /**
     * 审计活动分布
     */
    @GetMapping("/audit-stats")
    @RequirePermission("dashboard:view")
    public ResultVO<Map<String, Object>> getAuditStats() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        Map<String, Object> data = new LinkedHashMap<>();

        // 按模块
        List<Object[]> moduleRows = auditLogRepository.countByModule(start);
        List<Map<String, Object>> modules = new ArrayList<>();
        for (Object[] row : moduleRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("module", row[0]);
            item.put("count", row[1]);
            modules.add(item);
        }
        data.put("byModule", modules);

        // 按类型
        List<Object[]> typeRows = auditLogRepository.countByOperateType(start);
        List<Map<String, Object>> types = new ArrayList<>();
        for (Object[] row : typeRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", row[0]);
            item.put("count", row[1]);
            types.add(item);
        }
        data.put("byType", types);

        // 每日趋势（7天）
        LocalDateTime weekStart = LocalDate.now().minusDays(6).atStartOfDay();
        List<Object[]> dailyRows = auditLogRepository.countDailyTrend(weekStart);
        List<Map<String, Object>> daily = new ArrayList<>();
        for (Object[] row : dailyRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", row[0]);
            item.put("count", row[1]);
            daily.add(item);
        }
        data.put("dailyTrend", daily);

        return ResultVO.success(data);
    }

    /**
     * 最近活动流
     */
    @GetMapping("/recent-activity")
    @RequirePermission("dashboard:view")
    public ResultVO<List<Map<String, Object>>> getRecentActivity() {
        List<com.integration.config.entity.log.AuditSysLog> logs = auditLogRepository.findTop20ByOrderByOperateTimeDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (com.integration.config.entity.log.AuditSysLog log : logs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", log.getId());
            item.put("operateType", log.getOperateType());
            item.put("module", log.getModule());
            item.put("description", log.getDescription());
            item.put("userName", log.getUserName());
            item.put("clientIp", log.getClientIp());
            item.put("result", log.getResult());
            item.put("costTime", log.getCostTime());
            item.put("operateTime", log.getOperateTime());
            result.add(item);
        }
        return ResultVO.success(result);
    }

    /**
     * 系统健康状态
     */
    @GetMapping("/health")
    @RequirePermission("dashboard:view")
    public ResultVO<Map<String, Object>> getHealth() {
        Map<String, Object> data = new LinkedHashMap<>();

        // Redis
        try {
            String ping = redisConnectionFactory.getConnection().ping();
            data.put("redis", true);
        } catch (Exception e) {
            data.put("redis", false);
            data.put("redisError", e.getMessage());
        }

        data.put("timestamp", System.currentTimeMillis());
        return ResultVO.success(data);
    }

    /**
     * 系统硬件资源（CPU / 内存 / JVM）
     */
    @GetMapping("/system-resources")
    @RequirePermission("dashboard:view")
    public ResultVO<Map<String, Object>> getSystemResources() {
        Map<String, Object> data = new LinkedHashMap<>();

        // ---- 操作系统 ----
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = osBean.getCpuLoad() * 100;
        double processCpuLoad = osBean.getProcessCpuLoad() * 100;
        long totalPhysicalMemory = osBean.getTotalPhysicalMemorySize();
        long freePhysicalMemory = osBean.getFreePhysicalMemorySize();
        int processors = osBean.getAvailableProcessors();

        data.put("cpu", new LinkedHashMap<String, Object>() {{
            put("systemLoad", Math.round(cpuLoad * 10) / 10.0);
            put("processLoad", Math.round(processCpuLoad * 10) / 10.0);
            put("processors", processors);
        }});

        data.put("memory", new LinkedHashMap<String, Object>() {{
            long usedPhysical = totalPhysicalMemory - freePhysicalMemory;
            put("total", formatBytes(totalPhysicalMemory));
            put("totalBytes", totalPhysicalMemory);
            put("used", formatBytes(usedPhysical));
            put("usedBytes", usedPhysical);
            put("free", formatBytes(freePhysicalMemory));
            put("freeBytes", freePhysicalMemory);
            put("usagePercent", Math.round(usedPhysical * 10000.0 / totalPhysicalMemory) / 100.0);
        }});

        data.put("os", new LinkedHashMap<String, Object>() {{
            put("name", osBean.getName());
            put("version", osBean.getVersion());
            put("arch", osBean.getArch());
        }});

        // ---- JVM 内存 ----
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

        data.put("jvm", new LinkedHashMap<String, Object>() {{
            put("heap", new LinkedHashMap<String, Object>() {{
                put("max", formatBytes(heapUsage.getMax()));
                put("maxBytes", heapUsage.getMax());
                put("used", formatBytes(heapUsage.getUsed()));
                put("usedBytes", heapUsage.getUsed());
                put("committed", formatBytes(heapUsage.getCommitted()));
                put("usagePercent", Math.round(heapUsage.getUsed() * 10000.0 / heapUsage.getMax()) / 100.0);
            }});
            put("nonHeap", new LinkedHashMap<String, Object>() {{
                put("used", formatBytes(nonHeapUsage.getUsed()));
                put("usedBytes", nonHeapUsage.getUsed());
                put("committed", formatBytes(nonHeapUsage.getCommitted()));
            }});
        }});

        // ---- 运行时 ----
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        Runtime runtime = Runtime.getRuntime();
        long uptimeMs = runtimeBean.getUptime();

        data.put("runtime", new LinkedHashMap<String, Object>() {{
            put("javaVersion", System.getProperty("java.version"));
            put("vmName", System.getProperty("java.vm.name"));
            put("uptime", formatUptime(uptimeMs));
            put("uptimeMs", uptimeMs);
            put("threadCount", Thread.activeCount());
            put("maxMemory", formatBytes(runtime.maxMemory()));
            put("totalMemory", formatBytes(runtime.totalMemory()));
            put("freeMemory", formatBytes(runtime.freeMemory()));
        }});

        data.put("timestamp", System.currentTimeMillis());
        return ResultVO.success(data);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return Math.round(bytes * 10.0 / 1024) / 10.0 + " KB";
        if (bytes < 1024 * 1024 * 1024) return Math.round(bytes * 10.0 / (1024 * 1024)) / 10.0 + " MB";
        return Math.round(bytes * 10.0 / (1024 * 1024 * 1024)) / 10.0 + " GB";
    }

    private String formatUptime(long ms) {
        long days = ms / 86400000;
        long hours = (ms % 86400000) / 3600000;
        long minutes = (ms % 3600000) / 60000;
        if (days > 0) return days + "天" + hours + "小时" + minutes + "分";
        if (hours > 0) return hours + "小时" + minutes + "分";
        return minutes + "分钟";
    }
}
