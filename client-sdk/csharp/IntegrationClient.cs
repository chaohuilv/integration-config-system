using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading.Tasks;

namespace IntegrationSDK
{
    /// <summary>
    /// 集成配置系统 SDK 客户端
    /// 
    /// 使用示例：
    /// <code>
    /// var client = new IntegrationClient("http://localhost:8080");
    /// 
    /// // 登录认证
    /// var auth = client.Login("admin", "123456");
    /// Console.WriteLine($"欢迎: {auth.DisplayName}");
    /// 
    /// // GET 请求（带 URL 参数）
    /// string result = client.Invoke("user-get", new Dictionary&lt;string, object&gt; { { "id", 1001 } });
    /// 
    /// // POST 请求
    /// string result = client.Invoke("user-create",
    ///     new Dictionary&lt;string, object&gt; { { "deptId", 10 } },
    ///     "{\"username\":\"zhangsan\",\"email\":\"zhangsan@example.com\"}");
    /// 
    /// // 带自定义请求头
    /// var headers = new Dictionary&lt;string, string&gt;
    /// {
    ///     { "X-Request-Id", "req-12345" }
    /// };
    /// string result = client.Invoke("user-create",
    ///     new Dictionary&lt;string, object&gt;(),
    ///     "{\"name\":\"张三\"}",
    ///     headers);
    /// 
    /// // 退出登录
    /// client.Logout();
    /// </code>
    /// </summary>
    public class IntegrationClient : IDisposable
    {
        private readonly string _baseUrl;
        private readonly Dictionary<string, string> _defaultHeaders;
        private string _accessToken;
        private int _connectTimeout = 30000;
        private int _readTimeout = 30000;

        /// <summary>
        /// 构造客户端
        /// </summary>
        /// <param name="baseUrl">后端服务地址，如：http://localhost:8080</param>
        public IntegrationClient(string baseUrl)
        {
            _baseUrl = baseUrl.TrimEnd('/');
            _defaultHeaders = new Dictionary<string, string>
            {
                { "Accept", "application/json" }
            };
        }

        /// <summary>
        /// 添加默认请求头，所有请求都会携带
        /// </summary>
        public void AddDefaultHeader(string key, string value)
        {
            _defaultHeaders[key] = value;
        }

        /// <summary>
        /// 设置超时时间（毫秒），默认 30000
        /// </summary>
        public void SetTimeout(int connectTimeoutMs = 30000, int readTimeoutMs = 30000)
        {
            _connectTimeout = connectTimeoutMs;
            _readTimeout = readTimeoutMs;
        }

        /// <summary>
        /// 是否已登录（有有效 Token）
        /// </summary>
        public bool IsLoggedIn => !string.IsNullOrEmpty(_accessToken);

        /// <summary>
        /// 登录认证（调用 /api/auth/login）
        /// </summary>
        /// <param name="userCode">用户编码</param>
        /// <param name="password">密码</param>
        /// <returns>认证结果，包含用户信息和 access_token</returns>
        /// <exception cref="IntegrationException">登录失败时抛出</exception>
        public AuthResult Login(string userCode, string password)
        {
            string body = JsonSerializer.Serialize(new { userCode, password });
            try
            {
                string response = RawRequest("POST", "/api/auth/login", null, body, null);
                var node = JsonNode.Parse(response);
                var root = node as JsonObject;

                string token = root?["access_token"]?.GetValue<string>();
                if (string.IsNullOrEmpty(token))
                {
                    string msg = root?["message"]?.GetValue<string>();
                    throw new IntegrationException(msg ?? "登录失败：未获取到 token");
                }

                _accessToken = token;

                long? userId = root?["id"]?.GetValue<long?>();
                string sdkUserCode = root?["userCode"]?.GetValue<string>();
                string username = root?["username"]?.GetValue<string>();
                string displayName = root?["displayName"]?.GetValue<string>();

                return new AuthResult(token, userId, sdkUserCode, username, displayName);
            }
            catch (IntegrationException)
            {
                throw;
            }
            catch (Exception ex)
            {
                throw new IntegrationException("登录失败: " + ex.Message, ex);
            }
        }

        /// <summary>
        /// 检查是否已登录
        /// </summary>
        public bool CheckLogin()
        {
            if (!IsLoggedIn) return false;
            try
            {
                RawRequest("GET", "/api/auth/current", null, null, null);
                return true;
            }
            catch
            {
                return false;
            }
        }

        /// <summary>
        /// 退出登录（调用 /api/auth/logout）
        /// </summary>
        public void Logout()
        {
            try
            {
                RawRequest("POST", "/api/auth/logout", null, null, null);
            }
            catch
            {
                // 忽略退出失败的错误
            }
            finally
            {
                _accessToken = null;
            }
        }

        /// <summary>
        /// 获取当前登录用户信息（调用 /api/auth/current）
        /// </summary>
        /// <returns>用户信息字典</returns>
        /// <exception cref="IntegrationException">未登录或请求失败时抛出</exception>
        public Dictionary<string, object> GetCurrentUser()
        {
            if (!IsLoggedIn)
                throw new IntegrationException("未登录，请先调用 Login()");

            try
            {
                string response = RawRequest("GET", "/api/auth/current", null, null, null);
                return ParseJsonToDict(response);
            }
            catch (IntegrationException)
            {
                throw;
            }
            catch (Exception ex)
            {
                throw new IntegrationException("获取当前用户失败: " + ex.Message, ex);
            }
        }

        /// <summary>
        /// GET 请求，无参数
        /// </summary>
        public string Invoke(string apiCode)
        {
            return Invoke(apiCode, null, null, null);
        }

        /// <summary>
        /// GET 请求，带 URL 参数
        /// </summary>
        public string Invoke(string apiCode, Dictionary<string, object> parameters)
        {
            return Invoke(apiCode, parameters, null, null);
        }

        /// <summary>
        /// POST/PUT 请求，带 URL 参数和请求体
        /// </summary>
        public string Invoke(string apiCode,
                             Dictionary<string, object> parameters,
                             string body)
        {
            return Invoke(apiCode, parameters, body, null);
        }

        /// <summary>
        /// 完整调用，支持自定义请求头（覆盖配置的同名请求头）
        /// </summary>
        /// <param name="apiCode">接口编码</param>
        /// <param name="parameters">URL 查询参数（可选）</param>
        /// <param name="body">JSON 请求体字符串（可选）</param>
        /// <param name="headers">自定义请求头（可选）</param>
        /// <returns>JSON 响应字符串</returns>
        /// <exception cref="IntegrationException">调用失败时抛出</exception>
        public string Invoke(string apiCode,
                             Dictionary<string, object> parameters,
                             string body,
                             Dictionary<string, string> headers)
        {
            RequireLogin();
            return RawRequest("POST", $"/api/invoke/{apiCode}", parameters, body, headers);
        }

        /// <summary>
        /// 异步调用
        /// </summary>
        public async Task<string> InvokeAsync(string apiCode,
                                                Dictionary<string, object> parameters = null,
                                                string body = null,
                                                Dictionary<string, string> headers = null)
        {
            return await Task.Run(() => Invoke(apiCode, parameters, body, headers));
        }

        /// <summary>
        /// 批量调用（顺序执行）
        /// </summary>
        /// <param name="items">批量调用项，格式：(apiCode, parameters, body, headers)</param>
        /// <returns>响应字符串列表</returns>
        public List<string> BatchInvoke(List<(string apiCode,
                                               Dictionary<string, object> parameters,
                                               string body,
                                               Dictionary<string, string> headers)> items)
        {
            var results = new List<string>();
            foreach (var item in items)
            {
                results.Add(Invoke(item.apiCode, item.parameters, item.body, item.headers));
            }
            return results;
        }

        // ==================== 内部方法 ====================

        internal string RawRequest(string method,
                                   string path,
                                   Dictionary<string, object> parameters,
                                   string body,
                                   Dictionary<string, string> headers)
        {
            string url = BuildFullUrl(path, parameters);

            try
            {
                HttpWebRequest request = (HttpWebRequest)WebRequest.Create(url);
                request.Method = method;
                request.ContentType = "application/json; charset=UTF-8";
                request.Timeout = _connectTimeout;
                request.ReadWriteTimeout = _readTimeout;
                request.AutomaticDecompression = DecompressionMethods.None;
                request.ServicePoint.Expect100Continue = false;

                // 合并请求头
                var mergedHeaders = new Dictionary<string, string>(_defaultHeaders);
                // 认证 Token（自动添加）
                if (!string.IsNullOrEmpty(_accessToken))
                {
                    mergedHeaders["Authorization"] = "Bearer " + _accessToken;
                }
                // 用户自定义请求头（覆盖默认/认证头）
                if (headers != null)
                {
                    foreach (var h in headers)
                        mergedHeaders[h.Key] = h.Value;
                }
                foreach (var h in mergedHeaders)
                {
                    request.Headers[h.Key] = h.Value;
                }

                // 发送请求体
                if (!string.IsNullOrEmpty(body))
                {
                    byte[] bodyBytes = Encoding.UTF8.GetBytes(body);
                    request.ContentLength = bodyBytes.Length;
                    using (var reqStream = request.GetRequestStream())
                    {
                        reqStream.Write(bodyBytes, 0, bodyBytes.Length);
                    }
                }
                else
                {
                    request.ContentLength = 0;
                }

                // 读取响应
                using (WebResponse response = request.GetResponse())
                using (Stream responseStream = response.GetResponseStream())
                {
                    string responseText = responseStream == null
                        ? ""
                        : new StreamReader(responseStream, Encoding.UTF8).ReadToEnd();

                    int statusCode = (int)((HttpWebResponse)response).StatusCode;

                    if (statusCode >= 200 && statusCode < 300)
                    {
                        // 业务层错误检查
                        if (responseText.Contains("\"code\":") && !responseText.Contains("\"code\":200"))
                        {
                            var errNode = JsonNode.Parse(responseText);
                            string bizMsg = errNode?["message"]?.GetValue<string>();
                            throw new IntegrationException("业务错误: " + (bizMsg ?? responseText));
                        }
                        return responseText;
                    }
                    else if (statusCode == 401)
                    {
                        _accessToken = null;
                        throw new IntegrationException("认证失败（401）：Token 无效或已过期，请重新登录");
                    }
                    else
                    {
                        var errNode = JsonNode.Parse(responseText);
                        string errMsg = errNode?["message"]?.GetValue<string>();
                        throw new IntegrationException($"HTTP {statusCode}: " + (errMsg ?? responseText));
                    }
                }
            }
            catch (IntegrationException)
            {
                throw;
            }
            catch (WebException ex)
            {
                string errorResponse = "";
                if (ex.Response != null)
                {
                    using (var errStream = ex.Response.GetResponseStream())
                    {
                        if (errStream != null)
                            errorResponse = new StreamReader(errStream, Encoding.UTF8).ReadToEnd();
                    }
                }
                throw new IntegrationException($"SDK 调用失败: {ex.Message} | 响应: {errorResponse}", ex);
            }
            catch (Exception ex)
            {
                throw new IntegrationException($"SDK 调用失败: {ex.Message}", ex);
            }
        }

        private string BuildFullUrl(string path, Dictionary<string, object> parameters)
        {
            var url = $"{_baseUrl}{path}";
            if (parameters == null || parameters.Count == 0) return url;

            var query = new StringBuilder();
            foreach (var p in parameters)
            {
                if (query.Length > 0) query.Append('&');
                query.Append(Uri.EscapeDataString(p.Key))
                     .Append('=')
                     .Append(Uri.EscapeDataString(p.Value?.ToString() ?? ""));
            }
            return url + "?" + query;
        }

        private void RequireLogin()
        {
            if (!IsLoggedIn)
                throw new IntegrationException("未登录，请先调用 client.Login(userCode, password)");
        }

        private Dictionary<string, object> ParseJsonToDict(string json)
        {
            var result = new Dictionary<string, object>();
            if (string.IsNullOrEmpty(json)) return result;

            try
            {
                var node = JsonNode.Parse(json);
                var obj = node as JsonObject;
                if (obj == null) return result;

                // 提取 data 字段
                var dataNode = obj["data"] as JsonObject;
                if (dataNode != null)
                {
                    foreach (var kv in dataNode)
                    {
                        result[kv.Key] = kv.Value?.GetValue<object>() ?? null;
                    }
                }
                else
                {
                    foreach (var kv in obj)
                    {
                        result[kv.Key] = kv.Value?.GetValue<object>() ?? null;
                    }
                }
            }
            catch { }

            return result;
        }

        public void Dispose()
        {
            // .NET HttpWebRequest 无需显式释放资源
        }
    }

    /// <summary>
    /// 登录认证结果
    /// </summary>
    public class AuthResult
    {
        public string AccessToken { get; }
        public long? UserId { get; }
        public string UserCode { get; }
        public string Username { get; }
        public string DisplayName { get; }

        public AuthResult(string accessToken, long? userId, string userCode,
                          string username, string displayName)
        {
            AccessToken = accessToken;
            UserId = userId;
            UserCode = userCode;
            Username = username;
            DisplayName = displayName;
        }
    }
}
