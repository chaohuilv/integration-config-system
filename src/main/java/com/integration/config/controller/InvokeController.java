package com.integration.config.controller;

import com.integration.config.annotation.AuditLog;
import com.integration.config.annotation.RequirePermission;
import com.integration.config.enums.ErrorCode;
import com.integration.config.dto.InvokeRequestDTO;
import com.integration.config.dto.InvokeResponseDTO;
import com.integration.config.dto.PageResult;
import com.integration.config.entity.config.ApiConfig;
import com.integration.config.entity.log.InvokeLog;
import com.integration.config.exception.BusinessException;
import com.integration.config.repository.config.ApiConfigRepository;
import com.integration.config.repository.log.InvokeLogRepository;
import com.integration.config.service.HttpInvokeService;
import com.integration.config.enums.AppConstants;
import com.integration.config.service.RoleService;
import com.integration.config.vo.ResultVO;
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
    @RequirePermission("api:invoke")
    @AuditLog(operateType = "OTHER", module = "INVOKE", description = "'调用接口: ' + #request.apiCode", targetType = "API", targetId = "#request.apiCode", recordParams = true)
    public ResultVO<InvokeResponseDTO> invoke(@RequestBody InvokeRequestDTO request, HttpServletRequest httpRequest) {
        // 权限检查
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (!checkApiAccess(userId, request.getApiCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问该接口: " + request.getApiCode());
        }

        InvokeResponseDTO response = httpInvokeService.invoke(request);
        if (!response.getSuccess()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Invoke failed: " + response.getMessage());
        }
        return ResultVO.success(response);
    }

    /**
     * 调用接口（简化参数，使用 queryString）
     */
    @GetMapping("/{apiCode}")
    @RequirePermission("api:invoke")
    @AuditLog(operateType = "OTHER", module = "INVOKE", description = "'GET调用接口: ' + #apiCode", targetType = "API", targetId = "#apiCode")
    public ResultVO<InvokeResponseDTO> invokeGet(
            @PathVariable String apiCode,
            @RequestParam(required = false) String params,
            HttpServletRequest httpRequest) {
        // 权限检查
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (!checkApiAccess(userId, apiCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问该接口: " + apiCode);
        }

        InvokeRequestDTO request = InvokeRequestDTO.builder()
                .apiCode(apiCode)
                .debug(true) // GET 请求默认调试模式
                .build();
        InvokeResponseDTO response = httpInvokeService.invoke(request);
        if (!response.getSuccess()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Invoke failed: " + response.getMessage());
        }
        return ResultVO.success(response);
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
    @RequirePermission("log:view")
    @AuditLog(operateType = "QUERY", module = "INVOKE_LOG", description = "'查询调用日志列表'", recordResult = false)
    public ResultVO<PageResult<InvokeLog>> getLogs(
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
        return ResultVO.success(result);
    }

    /**
     * 查询单条日志详情
     */
    @GetMapping("/logs/detail/{id}")
    @RequirePermission("invoke-log:detail")
    @AuditLog(operateType = "QUERY", module = "INVOKE_LOG", description = "'查询调用日志详情ID: ' + #id", targetId = "#id")
    public ResultVO<InvokeLog> getLogDetail(@PathVariable Long id) {
        return invokeLogRepository.findById(id)
                .map(ResultVO::success)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "日志不存在"));
    }

    /**
     * 批量删除日志
     */
    @DeleteMapping("/logs")
    @RequirePermission("invoke-log:delete")
    @AuditLog(operateType = "DELETE", module = "INVOKE_LOG", description = "'批量删除调用日志'", recordParams = true)
    public ResultVO<Void> deleteLogs(@RequestBody List<Long> ids) {
        for (Long id : ids) {
            invokeLogRepository.deleteById(id);
        }
        return ResultVO.success(null);
    }

    /**
     * 获取接口最近调用记录
     */
    @GetMapping("/logs/{apiCode}/recent")
    @RequirePermission("log:view")
    @AuditLog(operateType = "QUERY", module = "INVOKE_LOG", description = "'查询接口最近调用: ' + #apiCode", targetType = "API", targetId = "#apiCode")
    public ResultVO<List<InvokeLog>> getRecentLogs(@PathVariable String apiCode) {
        List<InvokeLog> logs = invokeLogRepository.findTop10ByApiCodeOrderByInvokeTimeDesc(apiCode);
        return ResultVO.success(logs);
    }
}
