package com.integration.config.controller;

import com.integration.config.dto.InvokeRequestDTO;
import com.integration.config.dto.InvokeResponseDTO;
import com.integration.config.dto.PageResult;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.entity.log.InvokeLog;
import com.integration.config.repository.config.ApiConfigRepository;
import com.integration.config.repository.log.InvokeLogRepository;
import com.integration.config.service.HttpInvokeService;
import com.integration.config.enums.AppConstants;
import com.integration.config.service.RoleService;
import com.integration.config.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 接口调用 Controller
 */
@RestController
@RequestMapping("/api/invoke")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class InvokeController {

    private final HttpInvokeService httpInvokeService;
    private final InvokeLogRepository invokeLogRepository;
    private final RoleService roleService;
    private final ApiConfigRepository apiConfigRepository;

    /**
     * 调用接口
     */
    @PostMapping
    public Result<InvokeResponseDTO> invoke(@RequestBody InvokeRequestDTO request, HttpServletRequest httpRequest) {
        // 权限检查
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (!checkApiAccess(userId, request.getApiCode())) {
            return Result.of(405, "无权限访问该接口: " + request.getApiCode(), null);
        }

        InvokeResponseDTO response = httpInvokeService.invoke(request);
        return Result.of(
                response.getSuccess() ? 200 : 500,
                response.getMessage(),
                response
        );
    }

    /**
     * 调用接口（简化参数，使用 queryString）
     */
    @GetMapping("/{apiCode}")
    public Result<InvokeResponseDTO> invokeGet(
            @PathVariable String apiCode,
            @RequestParam(required = false) String params,
            HttpServletRequest httpRequest) {
        // 权限检查
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (!checkApiAccess(userId, apiCode)) {
            return Result.of(405, "无权限访问该接口: " + apiCode, null);
        }

        InvokeRequestDTO request = InvokeRequestDTO.builder()
                .apiCode(apiCode)
                .debug(true) // GET 请求默认调试模式
                .build();
        InvokeResponseDTO response = httpInvokeService.invoke(request);
        return Result.of(
                response.getSuccess() ? 200 : 500,
                response.getMessage(),
                response
        );
    }

    /**
     * 检查用户是否有权限访问接口
     */
    private boolean checkApiAccess(Long userId, String apiCode) {
        // 获取接口配置
        ApiConfig apiConfig = apiConfigRepository.findByCode(apiCode).orElse(null);
        if (apiConfig == null) {
            log.warn("[InvokeController] 接口不存在: {}", apiCode);
            return false;
        }

        // 检查接口状态
        if (!AppConstants.USER_STATUS_ACTIVE.equals(apiConfig.getStatus().name())) {
            log.warn("[InvokeController] 接口已禁用: {}", apiCode);
            return false;
        }

        // 权限检查
        boolean hasAccess = roleService.hasApiAccess(userId, apiConfig.getId());
        if (!hasAccess) {
            log.warn("[InvokeController] 用户 {} 无权限访问接口 {}", userId, apiCode);
        }
        return hasAccess;
    }

    /**
     * 查询调用日志（支持接口编码、请求URL、请求体联合模糊查询）
     */
    @GetMapping("/logs")
    public Result<PageResult<InvokeLog>> getLogs(
            @RequestParam(required = false) String apiCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String requestUrl,
            @RequestParam(required = false) String requestBody,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Page<InvokeLog> pageResult = invokeLogRepository.findByConditions(
                apiCode, startTime, endTime, success, requestUrl, requestBody, PageRequest.of(page - 1, size));
        
        // 获取统计数据
        Long successCount = invokeLogRepository.countAllSuccess();
        Long failCount = invokeLogRepository.countAllFail();
        Long total = successCount + failCount;
        
        PageResult<InvokeLog> result = PageResult.ofWithStats(
                pageResult.getContent(),
                total,
                page,
                size,
                successCount,
                failCount
        );
        return Result.success(result);
    }

    /**
     * 查询单条日志详情
     */
    @GetMapping("/logs/detail/{id}")
    public Result<InvokeLog> getLogDetail(@PathVariable Long id) {
        return invokeLogRepository.findById(id)
                .map(Result::success)
                .orElse(Result.of(404, "日志不存在", null));
    }

    /**
     * 批量删除日志
     */
    @DeleteMapping("/logs")
    public Result<Void> deleteLogs(@RequestBody List<Long> ids) {
        for (Long id : ids) {
            invokeLogRepository.deleteById(id);
        }
        return Result.success(null);
    }

    /**
     * 获取接口最近调用记录
     */
    @GetMapping("/logs/{apiCode}/recent")
    public Result<List<InvokeLog>> getRecentLogs(@PathVariable String apiCode) {
        List<InvokeLog> logs = invokeLogRepository.findTop10ByApiCodeOrderByInvokeTimeDesc(apiCode);
        return Result.success(logs);
    }
}
