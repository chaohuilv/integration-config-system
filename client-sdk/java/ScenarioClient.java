package com.integration.sdk;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * 场景编排调用 SDK 客户端
 *
 * <p>复用 IntegrationClient 的登录状态和请求基础设施，
 * 专门提供场景编排的执行与管理功能。
 *
 * <p>使用示例：
 * <pre>{@code
 * IntegrationClient baseClient = new IntegrationClient("http://localhost:8080");
 * baseClient.login("admin", "123456");
 *
 * ScenarioClient scenario = new ScenarioClient(baseClient);
 *
 * // 执行场景（按编码调用）
 * ScenarioResult result = scenario.execute("order-sync",
 *     Map.of("orderId", "20260425001"));
 *
 * // 查询执行结果
 * Map<String, Object> detail = scenario.getExecution(result.getExecutionId());
 *
 * // 查询步骤日志
 * List<Map<String, Object>> steps = scenario.getExecutionSteps(result.getExecutionId());
 *
 * // 查询场景列表
 * Map<String, Object> list = scenario.listScenarios("订单组", null, "订单", 0, 20);
 *
 * // 查询场景详情
 * Map<String, Object> detail = scenario.getDetail(1L);
 * }</pre>
 */
public class ScenarioClient {

    private final IntegrationClient client;

    /**
     * 构造场景客户端
     *
     * @param client 已登录的 IntegrationClient 实例
     */
    public ScenarioClient(IntegrationClient client) {
        this.client = client;
    }

    // ==================== 场景执行 ====================

    /**
     * 执行场景（按编码调用）
     *
     * @param scenarioCode 场景编码（必填）
     * @param params       输入参数（可选，传 null）
     * @return 执行结果
     * @throws IntegrationException 未登录或执行失败时抛出
     */
    public ScenarioResult execute(String scenarioCode, Map<String, Object> params) {
        return execute(scenarioCode, params, false, "API");
    }

    /**
     * 执行场景（完整参数）
     *
     * @param scenarioCode  场景编码（必填）
     * @param params        输入参数（可选，传 null）
     * @param asyncExec     是否异步执行
     * @param triggerSource 触发来源：MANUAL / SCHEDULE / API
     * @return 执行结果
     * @throws IntegrationException 未登录或执行失败时抛出
     */
    public ScenarioResult execute(String scenarioCode, Map<String, Object> params,
                                   boolean asyncExec, String triggerSource) {
        if (!client.isLoggedIn()) {
            throw new IntegrationException("未登录，请先调用 client.login(userCode, password)");
        }
        // 构建请求体
        StringBuilder body = new StringBuilder("{");
        body.append("\"scenarioCode\":\"").append(escapeJson(scenarioCode)).append("\",");
        body.append("\"params\":");
        if (params != null && !params.isEmpty()) {
            body.append(mapToJson(params));
        } else {
            body.append("{}");
        }
        body.append(",\"async\":").append(asyncExec);
        body.append(",\"triggerSource\":\"").append(escapeJson(triggerSource)).append("\"");
        body.append("}");

        String response = client.rawRequest("POST", "/api/scenario/execute", null, body.toString(), null);
        return parseScenarioResult(response);
    }

    /**
     * 通过场景ID执行场景（兼容旧调用方式，不推荐）
     *
     * @param scenarioId    场景ID
     * @param params        输入参数（可选，传 null）
     * @param asyncExec     是否异步执行
     * @param triggerSource 触发来源
     * @return 执行结果
     * @throws IntegrationException 未登录或执行失败时抛出
     */
    public ScenarioResult executeById(Long scenarioId, Map<String, Object> params,
                                       boolean asyncExec, String triggerSource) {
        if (!client.isLoggedIn()) {
            throw new IntegrationException("未登录，请先调用 client.login(userCode, password)");
        }
        StringBuilder body = new StringBuilder("{");
        body.append("\"scenarioId\":").append(scenarioId).append(",");
        body.append("\"params\":");
        if (params != null && !params.isEmpty()) {
            body.append(mapToJson(params));
        } else {
            body.append("{}");
        }
        body.append(",\"async\":").append(asyncExec);
        body.append(",\"triggerSource\":\"").append(escapeJson(triggerSource)).append("\"");
        body.append("}");

        String response = client.rawRequest("POST", "/api/scenario/execute", null, body.toString(), null);
        return parseScenarioResult(response);
    }

    // ==================== 场景管理 ====================

    /**
     * 分页查询场景列表（调用 GET /api/scenario/list）
     *
     * @param groupName 分组名称（可选，传 null）
     * @param status    状态筛选（可选，传 null），ACTIVE / INACTIVE
     * @param keyword   关键词搜索（可选，传 null）
     * @param page      页码，从 0 开始
     * @param size      每页条数
     * @return 分页结果 JSON 字符串
     */
    public String listScenarios(String groupName, String status, String keyword,
                                 int page, int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("page", page);
        params.put("size", size);
        if (groupName != null) params.put("groupName", groupName);
        if (status != null) params.put("status", status);
        if (keyword != null) params.put("keyword", keyword);

        return client.rawRequest("GET", "/api/scenario/list", params, null, null);
    }

    /**
     * 查询场景详情（调用 GET /api/scenario/{id}）
     *
     * @param scenarioId 场景ID
     * @return 场景详情 JSON 字符串
     */
    public String getDetail(Long scenarioId) {
        return client.rawRequest("GET", "/api/scenario/" + scenarioId, null, null, null);
    }

    /**
     * 查询所有分组名称（调用 GET /api/scenario/groups）
     *
     * @return 分组名称列表 JSON 字符串
     */
    public String getGroupNames() {
        return client.rawRequest("GET", "/api/scenario/groups", null, null, null);
    }

    /**
     * 查询所有启用的场景（调用 GET /api/scenario/active）
     *
     * @return 启用的场景列表 JSON 字符串
     */
    public String getActiveScenarios() {
        return client.rawRequest("GET", "/api/scenario/active", null, null, null);
    }

    // ==================== 步骤管理 ====================

    /**
     * 查询场景步骤列表（调用 GET /api/scenario/{scenarioId}/steps）
     *
     * @param scenarioId 场景ID
     * @return 步骤列表 JSON 字符串
     */
    public String getSteps(Long scenarioId) {
        return client.rawRequest("GET", "/api/scenario/" + scenarioId + "/steps", null, null, null);
    }

    // ==================== 执行记录 ====================

    /**
     * 分页查询执行记录（调用 GET /api/scenario/executions）
     *
     * @param scenarioId   场景ID（可选，传 null）
     * @param scenarioCode 场景编码（可选，传 null）
     * @param status       状态筛选（可选，传 null），RUNNING / SUCCESS / FAILED / PARTIAL
     * @param page         页码
     * @param size         每页条数
     * @return 执行记录分页结果 JSON 字符串
     */
    public String listExecutions(Long scenarioId, String scenarioCode,
                                  String status, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("page", page);
        params.put("size", size);
        if (scenarioId != null) params.put("scenarioId", scenarioId);
        if (scenarioCode != null) params.put("scenarioCode", scenarioCode);
        if (status != null) params.put("status", status);

        return client.rawRequest("GET", "/api/scenario/executions", params, null, null);
    }

    /**
     * 查询执行记录详情（调用 GET /api/scenario/executions/{executionId}）
     *
     * @param executionId 执行记录ID
     * @return 执行记录详情 JSON 字符串
     */
    public String getExecution(Long executionId) {
        return client.rawRequest("GET", "/api/scenario/executions/" + executionId, null, null, null);
    }

    /**
     * 查询执行步骤日志（调用 GET /api/scenario/executions/{executionId}/steps）
     *
     * @param executionId 执行记录ID
     * @return 步骤执行日志列表 JSON 字符串
     */
    public String getExecutionSteps(Long executionId) {
        return client.rawRequest("GET", "/api/scenario/executions/" + executionId + "/steps", null, null, null);
    }

    // ==================== 内部方法 ====================

    private ScenarioResult parseScenarioResult(String response) {
        // 从 ResultVO 中提取 data
        String dataStr = extractDataField(response);
        if (dataStr == null) {
            dataStr = response;
        }

        ScenarioResult result = new ScenarioResult();
        result.setSuccess(extractJsonBoolean(dataStr, "success"));
        result.setExecutionId(extractJsonLong(dataStr, "executionId"));
        result.setScenarioCode(extractJsonString(dataStr, "scenarioCode"));
        result.setScenarioName(extractJsonString(dataStr, "scenarioName"));
        result.setStatus(extractJsonString(dataStr, "status"));
        result.setCostTimeMs(extractJsonLong(dataStr, "costTimeMs"));
        result.setErrorMessage(extractJsonString(dataStr, "errorMessage"));
        result.setRawResponse(dataStr);
        return result;
    }

    /**
     * 提取 ResultVO 中的 data 字段内容
     */
    private String extractDataField(String json) {
        if (json == null || json.isEmpty()) return null;
        String marker = "\"data\":";
        int idx = json.indexOf(marker);
        if (idx < 0) return null;
        int start = idx + marker.length();
        // 跳过空白
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;

        char c = json.charAt(start);
        if (c == '{') {
            // 找匹配的 }
            int depth = 0;
            int end = start;
            while (end < json.length()) {
                if (json.charAt(end) == '{') depth++;
                else if (json.charAt(end) == '}') {
                    depth--;
                    if (depth == 0) break;
                }
                end++;
            }
            return json.substring(start, end + 1);
        } else if (c == '"') {
            int end = json.indexOf('"', start + 1);
            if (end < 0) return null;
            return json.substring(start + 1, end);
        }
        return null;
    }

    private String extractJsonString(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx);
        if (colon < 0) return null;
        int start = json.indexOf("\"", colon);
        if (start < 0) return null;
        int end = json.indexOf("\"", start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    private Long extractJsonLong(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx);
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == start) return null;
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean extractJsonBoolean(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx);
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start + 4 <= json.length() && json.substring(start, start + 4).equals("true")) return true;
        if (start + 5 <= json.length() && json.substring(start, start + 5).equals("false")) return false;
        return null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number) {
                sb.append(v);
            } else if (v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append("\"").append(escapeJson(v.toString())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // ==================== ScenarioResult 内部类 ====================

    /**
     * 场景执行结果
     */
    public static class ScenarioResult {
        private Boolean success;
        private Long executionId;
        private String scenarioCode;
        private String scenarioName;
        private String status;
        private Long costTimeMs;
        private String errorMessage;
        private String rawResponse;

        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }

        public Long getExecutionId() { return executionId; }
        public void setExecutionId(Long executionId) { this.executionId = executionId; }

        public String getScenarioCode() { return scenarioCode; }
        public void setScenarioCode(String scenarioCode) { this.scenarioCode = scenarioCode; }

        public String getScenarioName() { return scenarioName; }
        public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public Long getCostTimeMs() { return costTimeMs; }
        public void setCostTimeMs(Long costTimeMs) { this.costTimeMs = costTimeMs; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public String getRawResponse() { return rawResponse; }
        public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }

        @Override
        public String toString() {
            return "ScenarioResult{" +
                    "success=" + success +
                    ", executionId=" + executionId +
                    ", scenarioCode='" + scenarioCode + '\'' +
                    ", scenarioName='" + scenarioName + '\'' +
                    ", status='" + status + '\'' +
                    ", costTimeMs=" + costTimeMs +
                    ", errorMessage='" + errorMessage + '\'' +
                    '}';
        }
    }
}
