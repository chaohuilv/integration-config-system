"""
集成配置系统 SDK 客户端

使用示例：
    from integration_sdk import IntegrationClient

    client = IntegrationClient("http://localhost:8080")

    # GET 请求
    result = client.invoke("user-list")

    # GET 请求（带 URL 参数）
    result = client.invoke("user-get", params={"id": 1001})

    # POST 请求
    result = client.invoke(
        "user-create",
        params={"deptId": 10},
        body={"username": "zhangsan", "email": "zhangsan@example.com"}
    )

    # 带自定义请求头
    headers = {"X-Request-Id": "req-12345"}
    result = client.invoke(
        "user-create",
        params={},
        body={"name": "张三"},
        headers=headers
    )
"""

import json
import time
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional, Tuple, Union


class IntegrationException(Exception):
    """SDK 调用异常"""
    pass


class IntegrationClient:
    """
    集成配置系统 SDK 客户端

    Args:
        base_url: 后端服务地址，如：http://localhost:8080
        timeout: 请求超时时间（秒），默认 30
        default_headers: 默认请求头，所有请求都会携带
    """

    def __init__(
        self,
        base_url: str,
        timeout: int = 30,
        default_headers: Optional[Dict[str, str]] = None
    ):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.default_headers: Dict[str, str] = default_headers or {}
        if "Content-Type" not in self.default_headers:
            self.default_headers["Content-Type"] = "application/json; charset=UTF-8"
        if "Accept" not in self.default_headers:
            self.default_headers["Accept"] = "application/json"

    def add_default_header(self, key: str, value: str) -> None:
        """
        添加默认请求头，所有请求都会携带

        Args:
            key: 请求头名称
            value: 请求头值
        """
        self.default_headers[key] = value

    def invoke(
        self,
        api_code: str,
        params: Optional[Dict[str, Any]] = None,
        body: Optional[Union[Dict, str]] = None,
        headers: Optional[Dict[str, str]] = None
    ) -> str:
        """
        调用接口

        Args:
            api_code: 接口编码
            params: URL 查询参数（可选），例：{"id": 1001}
            body: 请求体（可选），可以是 dict（自动转 JSON）或 str
            headers: 自定义请求头（可选），会覆盖配置的同名请求头

        Returns:
            响应体字符串（通常为 JSON）

        Raises:
            IntegrationException: 调用失败时抛出
        """
        url = f"{self.base_url}/api/invoke/{api_code}"

        # 构建请求体
        body_str = ""
        if body is not None:
            if isinstance(body, dict):
                body_str = json.dumps(body, ensure_ascii=False)
            else:
                body_str = str(body)

        # 合并请求头
        merged_headers = {**self.default_headers}
        if headers:
            merged_headers.update(headers)

        try:
            req = urllib.request.Request(
                url=url,
                data=body_str.encode("utf-8") if body_str else None,
                headers=merged_headers,
                method="POST"
            )

            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                response_body = resp.read().decode("utf-8")
                return response_body

        except urllib.error.HTTPError as e:
            error_body = e.read().decode("utf-8") if e.fp else ""
            raise IntegrationException(
                f"HTTP {e.code}: {error_body}"
            ) from e
        except urllib.error.URLError as e:
            raise IntegrationException(
                f"网络请求失败: {e.reason}"
            ) from e
        except Exception as e:
            raise IntegrationException(
                f"SDK 调用失败: {e}"
            ) from e

    def batch_invoke(
        self,
        items: List[Tuple[str, Optional[Dict], Optional[Any], Optional[Dict]]]
    ) -> List[str]:
        """
        批量调用（顺序执行）

        Args:
            items: 调用项列表，每项格式：
                (api_code, params, body, headers)
                其中 params/body/headers 均可为 None

        Returns:
            响应字符串列表
        """
        results: List[str] = []
        for item in items:
            api_code = item[0]
            params = item[1] if len(item) > 1 else None
            body = item[2] if len(item) > 2 else None
            headers = item[3] if len(item) > 3 else None
            results.append(self.invoke(api_code, params, body, headers))
        return results
