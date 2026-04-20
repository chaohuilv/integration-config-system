package com.integration.config.controller;

import com.integration.config.vo.ResultVO;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 版本信息 Controller
 * 用于前端检测页面更新
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class VersionController {

    // 服务启动时间作为版本号
    private static final String START_TIME = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

    @GetMapping("/version")
    public ResultVO<Map<String, Object>> getVersion() {
        Map<String, Object> data = new HashMap<>();
        data.put("version", START_TIME);
        data.put("timestamp", System.currentTimeMillis());
        return ResultVO.success(data);
    }
}
