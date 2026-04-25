"""
场景编排调用 SDK 客户端

使用示例：
    from integration_sdk import IntegrationClient
    from scenario_sdk import ScenarioClient

    # 先登录
    base_client = IntegrationClient("http://localhost:8080")
    base_client.login("admin", "123456")

    # 创建场景客户端（共享登录状态）
    scenario = ScenarioClient(base_client)

    # 执行场景（按编码调用）
    result = scenario.execute("order-sync", params={"orderId": "20260425001"})

    # 查询执行结果
    detail = scenario.get_execution(result["executionId"])

    # 查询步骤日志
    steps = scenario.get_execution_steps(result["executionId"])

    # 查询场景列表
    scenarios = scenario.list_scenarios(keyword="订单")

    # 查询场景详情
    detail = scenario.get_detail(1)
"""

import json
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional

from integration_sdk import IntegrationClient, IntegrationException


class ScenarioClient:
    """
    场景编排 SDK 客户端

    复用 IntegrationClient 的登录状态和请求基础设施，
    专门提供场景编排的执行与管理功能。

    Args:
        client: 已登录的 IntegrationClient 实例
    """

    def __init__(self, client: IntegrationClient):
        self._client = client

    # ==================== 场景执行 ====================

    def execute(
        self,
        scenario_code: str,
        params: Optional[Dict[str, Any]] = None,
        async_exec: bool = False,
        trigger_source: str = "API"
    ) -> Dict[str, Any]:
        """
        执行场景（调用 POST /api/scenario/execute）

        Args:
            scenario_code: 场景编码（必填）
            params: 输入参数（可选），例：{"orderId": "20260425001"}
            async_exec: 是否异步执行，默认 False
            trigger_source: 触发来源，默认 "API"，可选 MANUAL / SCHEDULE / API

        Returns:
            执行结果字典，包含：
            - success: 是否成功
            - executionId: 执行记录ID
            - scenarioCode: 场景编码
            - scenarioName: 场景名称
            - status: 执行状态（RUNNING / SUCCESS / FAILED / PARTIAL）
            - startTime: 开始时间
            - endTime: 结束时间
            - costTimeMs: 耗时（毫秒）
            - errorMessage: 错误信息
            - context: 执行上下文（包含所有步骤输出）
            - steps: 步骤执行结果列表

        Raises:
            IntegrationException: 未登录或执行失败时抛出
        """
        if not self._client.is_logged_in:
            raise IntegrationException("未登录，请先调用 client.login(user_code, password)")

        body = {
            "scenarioCode": scenario_code,
            "params": params or {},
            "async": async_exec,
            "triggerSource": trigger_source
        }
        response = self._client._raw_request("POST", "/api/scenario/execute", None, body, None)
        return self._parse_result(response)

    def execute_by_id(
        self,
        scenario_id: int,
        params: Optional[Dict[str, Any]] = None,
        async_exec: bool = False,
        trigger_source: str = "API"
    ) -> Dict[str, Any]:
        """
        通过场景ID执行场景（兼容旧调用方式，不推荐）

        Args:
            scenario_id: 场景ID
            params: 输入参数（可选）
            async_exec: 是否异步执行
            trigger_source: 触发来源

        Returns:
            执行结果字典

        Raises:
            IntegrationException: 未登录或执行失败时抛出
        """
        if not self._client.is_logged_in:
            raise IntegrationException("未登录，请先调用 client.login(user_code, password)")

        body = {
            "scenarioId": scenario_id,
            "params": params or {},
            "async": async_exec,
            "triggerSource": trigger_source
        }
        response = self._client._raw_request("POST", "/api/scenario/execute", None, body, None)
        return self._parse_result(response)

    # ==================== 场景管理 ====================

    def list_scenarios(
        self,
        group_name: Optional[str] = None,
        status: Optional[str] = None,
        keyword: Optional[str] = None,
        page: int = 0,
        size: int = 20
    ) -> Dict[str, Any]:
        """
        分页查询场景列表（调用 GET /api/scenario/list）

        Args:
            group_name: 分组名称（可选）
            status: 状态筛选（可选），ACTIVE / INACTIVE
            keyword: 关键词搜索（可选）
            page: 页码，从 0 开始
            size: 每页条数

        Returns:
            分页结果字典，包含 content / totalElements / totalPages 等
        """
        params = {"page": page, "size": size}
        if group_name:
            params["groupName"] = group_name
        if status:
            params["status"] = status
        if keyword:
            params["keyword"] = keyword

        response = self._client._raw_request("GET", "/api/scenario/list", params, None, None)
        return self._parse_data(response)

    def get_detail(self, scenario_id: int) -> Dict[str, Any]:
        """
        查询场景详情（调用 GET /api/scenario/{id}）

        Args:
            scenario_id: 场景ID

        Returns:
            场景详情字典
        """
        response = self._client._raw_request("GET", f"/api/scenario/{scenario_id}", None, None, None)
        return self._parse_data(response)

    def get_group_names(self) -> List[str]:
        """
        查询所有分组名称（调用 GET /api/scenario/groups）

        Returns:
            分组名称列表
        """
        response = self._client._raw_request("GET", "/api/scenario/groups", None, None, None)
        return self._parse_data(response)

    def get_active_scenarios(self) -> List[Dict[str, Any]]:
        """
        查询所有启用的场景（调用 GET /api/scenario/active）

        Returns:
            启用的场景列表
        """
        response = self._client._raw_request("GET", "/api/scenario/active", None, None, None)
        return self._parse_data(response)

    # ==================== 步骤管理 ====================

    def get_steps(self, scenario_id: int) -> List[Dict[str, Any]]:
        """
        查询场景步骤列表（调用 GET /api/scenario/{scenarioId}/steps）

        Args:
            scenario_id: 场景ID

        Returns:
            步骤列表
        """
        response = self._client._raw_request("GET", f"/api/scenario/{scenario_id}/steps", None, None, None)
        return self._parse_data(response)

    # ==================== 执行记录 ====================

    def list_executions(
        self,
        scenario_id: Optional[int] = None,
        scenario_code: Optional[str] = None,
        status: Optional[str] = None,
        page: int = 0,
        size: int = 20
    ) -> Dict[str, Any]:
        """
        分页查询执行记录（调用 GET /api/scenario/executions）

        Args:
            scenario_id: 场景ID（可选）
            scenario_code: 场景编码（可选）
            status: 状态筛选（可选），RUNNING / SUCCESS / FAILED / PARTIAL
            page: 页码
            size: 每页条数

        Returns:
            执行记录分页结果
        """
        params = {"page": page, "size": size}
        if scenario_id is not None:
            params["scenarioId"] = scenario_id
        if scenario_code:
            params["scenarioCode"] = scenario_code
        if status:
            params["status"] = status

        response = self._client._raw_request("GET", "/api/scenario/executions", params, None, None)
        return self._parse_data(response)

    def get_execution(self, execution_id: int) -> Dict[str, Any]:
        """
        查询执行记录详情（调用 GET /api/scenario/executions/{executionId}）

        Args:
            execution_id: 执行记录ID

        Returns:
            执行记录详情
        """
        response = self._client._raw_request("GET", f"/api/scenario/executions/{execution_id}", None, None, None)
        return self._parse_data(response)

    def get_execution_steps(self, execution_id: int) -> List[Dict[str, Any]]:
        """
        查询执行步骤日志（调用 GET /api/scenario/executions/{executionId}/steps）

        Args:
            execution_id: 执行记录ID

        Returns:
            步骤执行日志列表
        """
        response = self._client._raw_request("GET", f"/api/scenario/executions/{execution_id}/steps", None, None, None)
        return self._parse_data(response)

    # ==================== 内部方法 ====================

    def _parse_result(self, response: str) -> Dict[str, Any]:
        """解析场景执行结果"""
        try:
            data = json.loads(response)
            # 标准结果包装：{"code": 200, "data": {...}}
            if "data" in data:
                return data["data"]
            return data
        except json.JSONDecodeError:
            raise IntegrationException(f"响应解析失败: {response[:200]}")

    def _parse_data(self, response: str) -> Any:
        """解析标准 ResultVO 响应，提取 data 字段"""
        try:
            data = json.loads(response)
            if "data" in data:
                return data["data"]
            return data
        except json.JSONDecodeError:
            raise IntegrationException(f"响应解析失败: {response[:200]}")