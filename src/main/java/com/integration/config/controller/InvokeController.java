package com.integration.config.controller;

import com.integration.config.dto.InvokeRequestDTO;
import com.integration.config.dto.InvokeResponseDTO;
import com.integration.config.dto.PageResult;
import com.integration.config.entity.log.InvokeLog;
import com.integration.config.repository.log.InvokeLogRepository;
import com.integration.config.service.HttpInvokeService;
import com.integration.config.util.Result;
import lombok.RequiredArgsConstructor;
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
public class InvokeController {

    private final HttpInvokeService httpInvokeService;
    private final InvokeLogRepository invokeLogRepository;

    /**
     * 调用接口
     */
    @PostMapping
    public Result<InvokeResponseDTO> invoke(@RequestBody InvokeRequestDTO request) {
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
            @RequestParam(required = false) String params) {
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
     * 查询调用日志
     */
    @GetMapping("/logs")
    public Result<PageResult<InvokeLog>> getLogs(
            @RequestParam(required = false) String apiCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Page<InvokeLog> pageResult = invokeLogRepository.findByConditions(
                apiCode, startTime, endTime, success, PageRequest.of(page - 1, size));
        
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
     * 获取接口最近调用记录
     */
    @GetMapping("/logs/{apiCode}/recent")
    public Result<List<InvokeLog>> getRecentLogs(@PathVariable String apiCode) {
        List<InvokeLog> logs = invokeLogRepository.findTop10ByApiCodeOrderByInvokeTimeDesc(apiCode);
        return Result.success(logs);
    }
}
