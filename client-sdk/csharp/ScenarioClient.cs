using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace IntegrationSDK
{
    /// <summary>
    /// 场景编排调用 SDK 客户端
    /// 
    /// 复用 IntegrationClient 的登录状态和请求基础设施，
    /// 专门提供场景编排的执行与管理功能。
    /// 
    /// 使用示例：
    /// <code>
    /// var baseClient = new IntegrationClient("http://localhost:8080");
    /// baseClient.Login("admin", "123456");
    /// 
    /// var scenario = new ScenarioClient(baseClient);
    /// 
    /// // 执行场景（按编码调用）
    /// var result = scenario.Execute("order-sync",
    ///     new Dictionary&lt;string, object&gt; { { "orderId", "20260425001" } });
    /// Console.WriteLine($"执行状态: {result.Status}");
    /// 
    /// // 查询执行结果
    /// var detail = scenario.GetExecution(result.ExecutionId);
    /// 
    /// // 查询步骤日志
    /// var steps = scenario.GetExecutionSteps(result.ExecutionId);
    /// 
    /// // 查询场景列表
    /// var list = scenario.ListScenarios(keyword: "订单");
    /// 
    /// // 查询场景详情
    /// var detail = scenario.GetDetail(1);
    /// </code>
    /// </summary>
    public class ScenarioClient
    {
        private readonly IntegrationClient _client;

        /// <summary>
        /// 构造场景客户端
        /// </summary>
        /// <param name="client">已登录的 IntegrationClient 实例</param>
        public ScenarioClient(IntegrationClient client)
        {
            _client = client ?? throw new ArgumentNullException(nameof(client));
        }

        // ==================== 场景执行 ====================

        /// <summary>
        /// 执行场景（按编码调用）
        /// </summary>
        /// <param name="scenarioCode">场景编码（必填）</param>
        /// <param name="params">输入参数（可选）</param>
        /// <returns>执行结果</returns>
        /// <exception cref="IntegrationException">未登录或执行失败时抛出</exception>
        public ScenarioResult Execute(string scenarioCode, Dictionary<string, object> parameters = null)
        {
            return Execute(scenarioCode, parameters, false, "API");
        }

        /// <summary>
        /// 执行场景（完整参数）
        /// </summary>
        /// <param name="scenarioCode">场景编码（必填）</param>
        /// <param name="parameters">输入参数（可选）</param>
        /// <param name="asyncExec">是否异步执行</param>
        /// <param name="triggerSource">触发来源：MANUAL / SCHEDULE / API</param>
        /// <returns>执行结果</returns>
        /// <exception cref="IntegrationException">未登录或执行失败时抛出</exception>
        public ScenarioResult Execute(string scenarioCode, Dictionary<string, object> parameters,
                                       bool asyncExec, string triggerSource)
        {
            if (!_client.IsLoggedIn)
                throw new IntegrationException("未登录，请先调用 client.Login(userCode, password)");

            var bodyObj = new Dictionary<string, object>
            {
                { "scenarioCode", scenarioCode },
                { "params", parameters ?? new Dictionary<string, object>() },
                { "async", asyncExec },
                { "triggerSource", triggerSource ?? "API" }
            };
            string body = JsonSerializer.Serialize(bodyObj);

            string response = _client.RawRequest("POST", "/api/scenario/execute", null, body, null);
            return ParseScenarioResult(response);
        }

        /// <summary>
        /// 通过场景ID执行场景（兼容旧调用方式，不推荐）
        /// </summary>
        /// <param name="scenarioId">场景ID</param>
        /// <param name="parameters">输入参数（可选）</param>
        /// <param name="asyncExec">是否异步执行</param>
        /// <param name="triggerSource">触发来源</param>
        /// <returns>执行结果</returns>
        /// <exception cref="IntegrationException">未登录或执行失败时抛出</exception>
        public ScenarioResult ExecuteById(long scenarioId, Dictionary<string, object> parameters = null,
                                           bool asyncExec = false, string triggerSource = "API")
        {
            if (!_client.IsLoggedIn)
                throw new IntegrationException("未登录，请先调用 client.Login(userCode, password)");

            var bodyObj = new Dictionary<string, object>
            {
                { "scenarioId", scenarioId },
                { "params", parameters ?? new Dictionary<string, object>() },
                { "async", asyncExec },
                { "triggerSource", triggerSource ?? "API" }
            };
            string body = JsonSerializer.Serialize(bodyObj);

            string response = _client.RawRequest("POST", "/api/scenario/execute", null, body, null);
            return ParseScenarioResult(response);
        }

        // ==================== 场景管理 ====================

        /// <summary>
        /// 分页查询场景列表（调用 GET /api/scenario/list）
        /// </summary>
        /// <param name="groupName">分组名称（可选）</param>
        /// <param name="status">状态筛选（可选），ACTIVE / INACTIVE</param>
        /// <param name="keyword">关键词搜索（可选）</param>
        /// <param name="page">页码，从 0 开始</param>
        /// <param name="size">每页条数</param>
        /// <returns>分页结果 JSON 字符串</returns>
        public string ListScenarios(string groupName = null, string status = null,
                                     string keyword = null, int page = 0, int size = 20)
        {
            var parameters = new Dictionary<string, object>
            {
                { "page", page },
                { "size", size }
            };
            if (groupName != null) parameters["groupName"] = groupName;
            if (status != null) parameters["status"] = status;
            if (keyword != null) parameters["keyword"] = keyword;

            return _client.RawRequest("GET", "/api/scenario/list", parameters, null, null);
        }

        /// <summary>
        /// 查询场景详情（调用 GET /api/scenario/{id}）
        /// </summary>
        /// <param name="scenarioId">场景ID</param>
        /// <returns>场景详情 JSON 字符串</returns>
        public string GetDetail(long scenarioId)
        {
            return _client.RawRequest("GET", $"/api/scenario/{scenarioId}", null, null, null);
        }

        /// <summary>
        /// 查询所有分组名称（调用 GET /api/scenario/groups）
        /// </summary>
        /// <returns>分组名称列表 JSON 字符串</returns>
        public string GetGroupNames()
        {
            return _client.RawRequest("GET", "/api/scenario/groups", null, null, null);
        }

        /// <summary>
        /// 查询所有启用的场景（调用 GET /api/scenario/active）
        /// </summary>
        /// <returns>启用的场景列表 JSON 字符串</returns>
        public string GetActiveScenarios()
        {
            return _client.RawRequest("GET", "/api/scenario/active", null, null, null);
        }

        // ==================== 步骤管理 ====================

        /// <summary>
        /// 查询场景步骤列表（调用 GET /api/scenario/{scenarioId}/steps）
        /// </summary>
        /// <param name="scenarioId">场景ID</param>
        /// <returns>步骤列表 JSON 字符串</returns>
        public string GetSteps(long scenarioId)
        {
            return _client.RawRequest("GET", $"/api/scenario/{scenarioId}/steps", null, null, null);
        }

        // ==================== 执行记录 ====================

        /// <summary>
        /// 分页查询执行记录（调用 GET /api/scenario/executions）
        /// </summary>
        /// <param name="scenarioId">场景ID（可选）</param>
        /// <param name="scenarioCode">场景编码（可选）</param>
        /// <param name="status">状态筛选（可选），RUNNING / SUCCESS / FAILED / PARTIAL</param>
        /// <param name="page">页码</param>
        /// <param name="size">每页条数</param>
        /// <returns>执行记录分页结果 JSON 字符串</returns>
        public string ListExecutions(long? scenarioId = null, string scenarioCode = null,
                                      string status = null, int page = 0, int size = 20)
        {
            var parameters = new Dictionary<string, object>
            {
                { "page", page },
                { "size", size }
            };
            if (scenarioId.HasValue) parameters["scenarioId"] = scenarioId.Value;
            if (scenarioCode != null) parameters["scenarioCode"] = scenarioCode;
            if (status != null) parameters["status"] = status;

            return _client.RawRequest("GET", "/api/scenario/executions", parameters, null, null);
        }

        /// <summary>
        /// 查询执行记录详情（调用 GET /api/scenario/executions/{executionId}）
        /// </summary>
        /// <param name="executionId">执行记录ID</param>
        /// <returns>执行记录详情 JSON 字符串</returns>
        public string GetExecution(long executionId)
        {
            return _client.RawRequest("GET", $"/api/scenario/executions/{executionId}", null, null, null);
        }

        /// <summary>
        /// 查询执行步骤日志（调用 GET /api/scenario/executions/{executionId}/steps）
        /// </summary>
        /// <param name="executionId">执行记录ID</param>
        /// <returns>步骤执行日志列表 JSON 字符串</returns>
        public string GetExecutionSteps(long executionId)
        {
            return _client.RawRequest("GET", $"/api/scenario/executions/{executionId}/steps", null, null, null);
        }

        // ==================== 内部方法 ====================

        private ScenarioResult ParseScenarioResult(string response)
        {
            var result = new ScenarioResult();

            try
            {
                var node = JsonNode.Parse(response);
                var root = node as JsonObject;

                // 提取 data 字段（标准 ResultVO 包装）
                var dataNode = root?["data"] as JsonObject;
                if (dataNode == null) dataNode = root;

                result.Success = dataNode?["success"]?.GetValue<bool?>();
                result.ExecutionId = dataNode?["executionId"]?.GetValue<long?>();
                result.ScenarioCode = dataNode?["scenarioCode"]?.GetValue<string>();
                result.ScenarioName = dataNode?["scenarioName"]?.GetValue<string>();
                result.Status = dataNode?["status"]?.GetValue<string>();
                result.CostTimeMs = dataNode?["costTimeMs"]?.GetValue<long?>();
                result.ErrorMessage = dataNode?["errorMessage"]?.GetValue<string>();
                result.RawResponse = dataNode?.ToJsonString();
            }
            catch
            {
                result.RawResponse = response;
            }

            return result;
        }
    }

    /// <summary>
    /// 场景执行结果
    /// </summary>
    public class ScenarioResult
    {
        /// <summary>是否成功</summary>
        public bool? Success { get; set; }

        /// <summary>执行记录ID</summary>
        public long? ExecutionId { get; set; }

        /// <summary>场景编码</summary>
        public string ScenarioCode { get; set; }

        /// <summary>场景名称</summary>
        public string ScenarioName { get; set; }

        /// <summary>执行状态：RUNNING / SUCCESS / FAILED / PARTIAL</summary>
        public string Status { get; set; }

        /// <summary>耗时（毫秒）</summary>
        public long? CostTimeMs { get; set; }

        /// <summary>错误信息</summary>
        public string ErrorMessage { get; set; }

        /// <summary>原始响应 JSON</summary>
        public string RawResponse { get; set; }

        public override string ToString()
        {
            return $"ScenarioResult{{Success={Success}, ExecutionId={ExecutionId}, " +
                   $"ScenarioCode='{ScenarioCode}', Status='{Status}', CostTimeMs={CostTimeMs}}}";
        }
    }
}
