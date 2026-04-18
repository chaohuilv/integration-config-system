"""
集成配置系统 SDK 客户端

使用示例：
    from integration_sdk import IntegrationClient

    client = IntegrationClient("http://localhost:8080")

    # 登录认证
    auth = client.login("admin", "123456")
    print(f"欢迎: {auth['display_name']}")

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

    # 退出登录
    client.logout()
"""

import json
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional, Tuple, Union


class IntegrationException(Exception):
    """SDK 调用异常"""
    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


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
        self._access_token: Optional[str] = None
        if "Content-Type" not in self.default_headers:
            self.default_headers["Content-Type"] = "application/json; charset=UTF-8"
        if "Accept" not in self.default_headers:
            self.default_headers["Accept"] = "application/json"

    @property
    def is_logged_in(self) -> bool:
        """检查是否已登录（有有效 Token）"""
        return bool(self._access_token)

    def login(self, user_code: str, password: str) -> Dict[str, Any]:
        """
        登录认证（调用 /api/auth/login）

        Args:
            user_code: 用户编码
            password: 密码

        Returns:
            认证结果字典，包含：
            - access_token: 访问令牌
            - id: 用户ID
            - user_code: 用户编码
            - username: 用户名
            - display_name: 显示名称

        Raises:
            IntegrationException: 登录失败时抛出
        """
        body = json.dumps({"userCode": user_code, "password": password}, ensure_ascii=False)
        try:
            response = self._raw_request("POST", "/api/auth/login", None, body, None)
            data = json.loads(response)

            token = data.get("access_token")
            if not token:
                msg = data.get("message", "登录失败：未获取到 token")
                raise IntegrationException(msg)

            self._access_token = token
            return {
                "access_token": token,
                "id": data.get("id"),
                "user_code": data.get("userCode"),
                "username": data.get("username"),
                "display_name": data.get("displayName"),
            }
        except IntegrationException:
            raise
        except Exception as e:
            raise IntegrationException(f"登录失败: {e}")

    def check_login(self) -> bool:
        """
        检查 Token 是否有效（调用 /api/auth/current）

        Returns:
            True 表示 Token 有效，False 表示无效或已过期
        """
        if not self.is_logged_in:
            return False
        try:
            self._raw_request("GET", "/api/auth/current", None, None, None)
            return True
        except Exception:
            return False

    def logout(self) -> None:
        """
        退出登录（调用 /api/auth/logout）
        """
        try:
            self._raw_request("POST", "/api/auth/logout", None, None, None)
        except Exception:
            pass
        finally:
            self._access_token = None

    def get_current_user(self) -> Dict[str, Any]:
        """
        获取当前登录用户信息（调用 /api/auth/current）

        Returns:
            用户信息字典

        Raises:
            IntegrationException: 未登录或请求失败时抛出
        """
        if not self.is_logged_in:
            raise IntegrationException("未登录，请先调用 client.login(user_code, password)")
        try:
            response = self._raw_request("GET", "/api/auth/current", None, None, None)
            data = json.loads(response)
            # 优先取 data 字段
            return data.get("data", data)
        except IntegrationException:
            raise
        except Exception as e:
            raise IntegrationException(f"获取当前用户失败: {e}")

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
            IntegrationException: 未登录或调用失败时抛出
        """
        if not self.is_logged_in:
            raise IntegrationException("未登录，请先调用 client.login(user_code, password)")
        return self._raw_request("POST", f"/api/invoke/{api_code}", params, body, headers)

    def invoke_async(
        self,
        api_code: str,
        params: Optional[Dict[str, Any]] = None,
        body: Optional[Union[Dict, str]] = None,
        headers: Optional[Dict[str, str]] = None
    ):
        """
        异步调用（使用 concurrent.futures）

        Returns:
            Future 对象，可用 .result() 获取结果
        """
        from concurrent.futures import ThreadPoolExecutor
        with ThreadPoolExecutor(max_workers=1) as executor:
            return executor.submit(self.invoke, api_code, params, body, headers)

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

    # ==================== 内部方法 ====================

    def _raw_request(
        self,
        method: str,
        path: str,
        params: Optional[Dict[str, Any]],
        body: Optional[Union[Dict, str]],
        headers: Optional[Dict[str, str]]
    ) -> str:
        """核心请求方法，统一处理认证、自动重试、响应解析"""

        # 1. 构建完整 URL（含查询参数）
        url = self._build_full_url(path, params)

        # 2. 构建请求体字符串
        body_str = ""
        if body is not None:
            if isinstance(body, dict):
                body_str = json.dumps(body, ensure_ascii=False)
            else:
                body_str = str(body)

        # 3. 合并请求头
        merged_headers = {**self.default_headers}

        # 认证 Token（自动添加）
        if self._access_token:
            merged_headers["Authorization"] = f"Bearer {self._access_token}"

        # 用户自定义请求头（覆盖默认）
        if headers:
            merged_headers.update(headers)

        # 4. 发送请求
        try:
            req = urllib.request.Request(
                url=url,
                data=body_str.encode("utf-8") if body_str else None,
                headers=merged_headers,
                method=method
            )

            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                response_body = resp.read().decode("utf-8")
                # 业务层错误检查
                try:
                    data = json.loads(response_body)
                    if "code" in data and data["code"] != 200:
                        raise IntegrationException(f"业务错误: {data.get('message', response_body)}")
                except IntegrationException:
                    raise
                except Exception:
                    pass  # 不是合法 JSON，继续返回
                return response_body

        except urllib.error.HTTPError as e:
            error_body = e.read().decode("utf-8") if e.fp else ""
            if e.code == 401:
                self._access_token = None
                raise IntegrationException("认证失败（401）：Token 无效或已过期，请重新登录")
            try:
                err_data = json.loads(error_body)
                err_msg = err_data.get("message", error_body)
            except Exception:
                err_msg = error_body
            raise IntegrationException(f"HTTP {e.code}: {err_msg}") from e

        except urllib.error.URLError as e:
            raise IntegrationException(f"网络请求失败: {e.reason}") from e

        except Exception as e:
            raise IntegrationException(f"SDK 调用失败: {e}") from e

    def _build_full_url(self, path: str, params: Optional[Dict[str, Any]]) -> str:
        """构建带查询参数的完整 URL"""
        url = f"{self.base_url}{path}"
        if not params:
            return url
        query_parts = []
        for k, v in params.items():
            key_encoded = urllib.request.quote(str(k), safe="")
            val_encoded = urllib.request.quote(str(v) if v is not None else "", safe="")
            query_parts.append(f"{key_encoded}={val_encoded}")
        if query_parts:
            url += "?" + "&".join(query_parts)
        return url
