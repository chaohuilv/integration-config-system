package com.integration.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock 响应模板引擎
 *
 * <p>支持动态变量替换：
 * <ul>
 *   <li><code>{{randomInt}}</code> - 随机整数</li>
 *   <li><code>{{randomInt:min:max}}</code> - 指定范围随机整数</li>
 *   <li><code>{{randomFloat}}</code> - 随机浮点数</li>
 *   <li><code>{{randomFloat:min:max:decimals}}</code> - 指定范围随机浮点数</li>
 *   <li><code>{{uuid}}</code> - UUID</li>
 *   <li><code>{{uuid:nodash}}</code> - 无横线 UUID</li>
 *   <li><code>{{timestamp}}</code> - Unix 时间戳（秒）</li>
 *   <li><code>{{timestampMs}}</code> - Unix 时间戳（毫秒）</li>
 *   <li><code>{{date}}</code> - 日期 yyyy-MM-dd</li>
 *   <li><code>{{datetime}}</code> - 日期时间 yyyy-MM-dd HH:mm:ss</li>
 *   <li><code>{{datetime:format}}</code> - 自定义格式</li>
 *   <li><code>{{randomString}}</code> - 随机字符串（8位）</li>
 *   <li><code>{{randomString:length}}</code> - 指定长度随机字符串</li>
 *   <li><code>{{randomEmail}}</code> - 随机邮箱</li>
 *   <li><code>{{randomPhone}}</code> - 随机手机号</li>
 *   <li><code>{{randomName}}</code> - 随机中文姓名</li>
 *   <li><code>{{randomCity}}</code> - 随机城市</li>
 *   <li><code>$request.path.xxx</code> - 路径参数</li>
 *   <li><code>$request.query.xxx</code> - 查询参数</li>
 *   <li><code>$request.header.xxx</code> - 请求头</li>
 *   <li><code>$request.body.xxx</code> - 请求体字段（JSON Path）</li>
 * </ul>
 */
@Slf4j
@Component
public class MockTemplateEngine {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectMapper objectMapper;

    // 中文姓名库
    private static final String[] SURNAMES = {"张", "李", "王", "刘", "陈", "杨", "黄", "赵", "周", "吴", "徐", "孙", "马", "朱", "胡", "郭", "何", "林", "高", "罗"};
    private static final String[] NAMES = {"伟", "芳", "娜", "秀英", "敏", "静", "丽", "强", "磊", "军", "洋", "勇", "艳", "杰", "娟", "涛", "明", "超", "秀兰", "霞"};

    // 城市库
    private static final String[] CITIES = {"北京", "上海", "广州", "深圳", "杭州", "南京", "武汉", "成都", "西安", "重庆", "苏州", "天津", "长沙", "郑州", "青岛"};

    public MockTemplateEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 渲染模板
     *
     * @param template 模板内容
     * @param context  上下文变量
     * @return 渲染后的内容
     */
    public String render(String template, MockContext context) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        // 第一轮：处理内置模板变量
        String result = renderBuiltInVariables(template);

        // 第二轮：处理请求上下文变量
        result = renderRequestVariables(result, context);

        return result;
    }

    /**
     * 渲染内置模板变量
     */
    private String renderBuiltInVariables(String template) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            String replacement = evaluateExpression(expression);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 渲染请求上下文变量
     */
    private String renderRequestVariables(String template, MockContext context) {
        if (context == null) {
            return template;
        }

        String result = template;

        // 替换 $request.path.xxx
        if (context.getPathVariables() != null) {
            for (Map.Entry<String, String> entry : context.getPathVariables().entrySet()) {
                result = result.replace("$request.path." + entry.getKey(), entry.getValue());
            }
        }

        // 替换 $request.query.xxx
        if (context.getQueryParams() != null) {
            for (Map.Entry<String, String[]> entry : context.getQueryParams().entrySet()) {
                String value = entry.getValue() != null && entry.getValue().length > 0 ? entry.getValue()[0] : "";
                result = result.replace("$request.query." + entry.getKey(), value);
            }
        }

        // 替换 $request.header.xxx
        if (context.getHeaders() != null) {
            for (Map.Entry<String, String> entry : context.getHeaders().entrySet()) {
                result = result.replace("$request.header." + entry.getKey(), entry.getValue());
            }
        }

        // 替换 $request.body.xxx（JSON Path）
        if (context.getBody() != null && !context.getBody().isEmpty()) {
            result = renderBodyVariables(result, context.getBody());
        }

        return result;
    }

    /**
     * 渲染请求体变量（JSON Path）
     */
    private String renderBodyVariables(String template, String body) {
        try {
            JsonNode rootNode = objectMapper.readTree(body);
            Pattern bodyPattern = Pattern.compile("\\$request\\.body\\.([a-zA-Z0-9_.\\[\\]]+)");
            Matcher matcher = bodyPattern.matcher(template);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String jsonPath = matcher.group(1);
                String value = extractJsonPathValue(rootNode, jsonPath);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : ""));
            }
            matcher.appendTail(sb);

            return sb.toString();
        } catch (Exception e) {
            log.debug("解析请求体 JSON 失败: {}", e.getMessage());
            return template;
        }
    }

    /**
     * 从 JSON 节点提取值
     */
    private String extractJsonPathValue(JsonNode node, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = node;
        for (String part : parts) {
            // 处理数组索引，如 items[0]
            if (part.contains("[")) {
                String fieldName = part.substring(0, part.indexOf("["));
                int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));
                current = current.path(fieldName).get(index);
            } else {
                current = current.path(part);
            }
            if (current.isMissingNode()) {
                return null;
            }
        }
        return current.isValueNode() ? current.asText() : current.toString();
    }

    /**
     * 执行表达式
     */
    private String evaluateExpression(String expression) {
        // 随机整数 {{randomInt}} 或 {{randomInt:min:max}}
        if (expression.startsWith("randomInt")) {
            return evaluateRandomInt(expression);
        }

        // 随机浮点数 {{randomFloat}} 或 {{randomFloat:min:max:decimals}}
        if (expression.startsWith("randomFloat")) {
            return evaluateRandomFloat(expression);
        }

        // UUID {{uuid}} 或 {{uuid:nodash}}
        if (expression.equals("uuid")) {
            return UUID.randomUUID().toString();
        }
        if (expression.equals("uuid:nodash")) {
            return UUID.randomUUID().toString().replace("-", "");
        }

        // 时间戳 {{timestamp}} {{timestampMs}}
        if (expression.equals("timestamp")) {
            return String.valueOf(System.currentTimeMillis() / 1000);
        }
        if (expression.equals("timestampMs")) {
            return String.valueOf(System.currentTimeMillis());
        }

        // 日期时间 {{date}} {{datetime}} {{datetime:format}}
        if (expression.equals("date")) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        if (expression.equals("datetime")) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        if (expression.startsWith("datetime:")) {
            String format = expression.substring("datetime:".length());
            try {
                return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
            } catch (Exception e) {
                return LocalDateTime.now().toString();
            }
        }

        // 随机字符串 {{randomString}} 或 {{randomString:length}}
        if (expression.startsWith("randomString")) {
            return evaluateRandomString(expression);
        }

        // 随机邮箱 {{randomEmail}}
        if (expression.equals("randomEmail")) {
            String[] domains = {"qq.com", "163.com", "gmail.com", "outlook.com", "foxmail.com"};
            return "user" + (RANDOM.nextInt(90000) + 10000) + "@" + domains[RANDOM.nextInt(domains.length)];
        }

        // 随机手机号 {{randomPhone}}
        if (expression.equals("randomPhone")) {
            String[] prefixes = {"138", "139", "150", "151", "186", "187", "188", "189"};
            StringBuilder sb = new StringBuilder(prefixes[RANDOM.nextInt(prefixes.length)]);
            for (int i = 0; i < 8; i++) {
                sb.append(RANDOM.nextInt(10));
            }
            return sb.toString();
        }

        // 随机姓名 {{randomName}}
        if (expression.equals("randomName")) {
            return SURNAMES[RANDOM.nextInt(SURNAMES.length)] + NAMES[RANDOM.nextInt(NAMES.length)];
        }

        // 随机城市 {{randomCity}}
        if (expression.equals("randomCity")) {
            return CITIES[RANDOM.nextInt(CITIES.length)];
        }

        // 未知表达式，保持原样
        return "{{" + expression + "}}";
    }

    private String evaluateRandomInt(String expression) {
        if (expression.equals("randomInt")) {
            return String.valueOf(RANDOM.nextInt(10000));
        }
        // {{randomInt:min:max}}
        String[] parts = expression.split(":");
        if (parts.length == 3) {
            try {
                int min = Integer.parseInt(parts[1]);
                int max = Integer.parseInt(parts[2]);
                return String.valueOf(min + RANDOM.nextInt(max - min + 1));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return "0";
    }

    private String evaluateRandomFloat(String expression) {
        if (expression.equals("randomFloat")) {
            return String.format("%.2f", RANDOM.nextDouble() * 1000);
        }
        // {{randomFloat:min:max:decimals}}
        String[] parts = expression.split(":");
        if (parts.length >= 3) {
            try {
                double min = Double.parseDouble(parts[1]);
                double max = Double.parseDouble(parts[2]);
                int decimals = parts.length > 3 ? Integer.parseInt(parts[3]) : 2;
                double value = min + RANDOM.nextDouble() * (max - min);
                return String.format("%." + decimals + "f", value);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return "0.0";
    }

    private String evaluateRandomString(String expression) {
        int length = 8;
        if (expression.startsWith("randomString:")) {
            try {
                length = Integer.parseInt(expression.substring("randomString:".length()));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Mock 请求上下文
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MockContext {
        private Map<String, String> pathVariables;
        private Map<String, String[]> queryParams;
        private Map<String, String> headers;
        private String body;
    }
}
