using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Text;
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
    /// // GET 请求
    /// string result = client.Invoke("user-list");
    /// 
    /// // GET 请求（带 URL 参数）
    /// string result = client.Invoke("user-get", new Dictionary&lt;string, object&gt; { { "id", 1001 } });
    /// 
    /// // POST 请求
    /// string result = client.Invoke("user-create",
    ///     new Dictionary&lt;string, object&gt; { { "deptId", 10 } },
    ///     "{\"name\":\"张三\",\"email\":\"zhangsan@example.com\"}");
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
    /// </code>
    /// </summary>
    public class IntegrationClient : IDisposable
    {
        private readonly string _baseUrl;
        private readonly Dictionary<string, string> _defaultHeaders;
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
            string url = BuildUrl(apiCode, parameters);

            try
            {
                HttpWebRequest request = (HttpWebRequest)WebRequest.Create(url);
                request.Method = "POST";
                request.ContentType = "application/json; charset=UTF-8";
                request.Timeout = _connectTimeout;
                request.ReadWriteTimeout = _readTimeout;
                request.AutomaticDecompression = DecompressionMethods.None;
                request.ServicePoint.Expect100Continue = false;

                // 合并请求头
                var mergedHeaders = new Dictionary<string, string>(_defaultHeaders);
                if (headers != null)
                {
                    foreach (var h in headers) mergedHeaders[h.Key] = h.Value;
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

                    if ((int)((HttpWebResponse)response).StatusCode >= 200
                        && (int)((HttpWebResponse)response).StatusCode < 300)
                    {
                        return responseText;
                    }
                    else
                    {
                        throw new IntegrationException(
                            $"HTTP {(int)((HttpWebResponse)response).StatusCode}: {responseText}");
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

        private string BuildUrl(string apiCode, Dictionary<string, object> parameters)
        {
            var url = $"{_baseUrl}/api/invoke/{apiCode}";
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

        public void Dispose()
        {
            // .NET HttpWebRequest 无需显式释放资源
        }
    }
}
