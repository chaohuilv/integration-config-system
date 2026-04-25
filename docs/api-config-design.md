# 接口配置设计文档

> 本文档描述接口配置、环境配置、在线调试、Curl 导入和接口文档功能的设计与实现。

---

## 一、功能概述

### 1.1 接口配置管理

统一管理各业务系统的 API 接口配置，支持完整的 CRUD、版本控制、认证管理、频率限制、响应缓存和动态 Token。

| 特性 | 说明 |
|------|------|
| 基础配置 | 名称/编码/URL/方法/Content-Type/超时/重试 |
| 加密存储 | headers、requestBody、authInfo 使用 AES-256-GCM 加密 |
| 认证方式 | NONE / BASIC / BEARER / API_KEY / DYNAMIC（动态 Token） |
| 版本控制 | 多版本管理（v1→v2→v3），latestVersion 标记推荐版本 |
| 频率限制 | Redis 固定窗口计数器，按接口+用户维度限流 |
| 响应缓存 | Redis 缓存，按 apiCode+params 维度缓存 |
| 动态 Token | 自动调用 Token 接口获取并缓存，支持 header/url/body 三种注入位置 |
| Token 自动缓存 | 调用成功后自动提取响应中的 Token 写入缓存表 |

### 1.2 环境配置

多环境管理（DEV/TEST/PRE/PROD），接口调用时自动替换 URL 域名，实现同一配置在不同环境间切换。

| 特性 | 说明 |
|------|------|
| 多环境管理 | 按系统名称分组，每组可有多个环境 |
| 互斥激活 | 同一系统同时只能激活一个环境 |
| URL 域名替换 | 调用时自动用活跃环境的 baseUrl 替换接口 URL 的域名部分 |
| 缓存清理 | 切换环境时自动清理 Token 缓存和场景缓存 |

### 1.3 在线调试

通过接口编码在线调用接口，支持动态参数替换，实时查看请求和响应。

| 特性 | 说明 |
|------|------|
| POST 调用 | `/api/invoke`，传入 apiCode + params |
| GET 调用 | `/api/invoke/{apiCode}`，默认调试模式 |
| 频率限制 | 调用前检查 Redis 限流，超限返回 HTTP 429 |
| 权限控制 | 用户必须有 `api:invoke` 权限 + 角色级 API 访问权限 |
| 调试模式 | `debug=true` 时不记录调用日志 |

### 1.4 Curl 导入

解析标准 curl 命令，自动提取 URL、Method、Headers、Body、认证信息，一键生成接口配置。

| 特性 | 说明 |
|------|------|
| URL 提取 | 支持 `curl 'https://...'` 和 `curl https://...` 两种格式 |
| 方法推断 | `-X POST` 显式指定；`-d`/`--data` 隐式推断为 POST；默认 GET |
| 请求头解析 | `-H 'Key: Value'` / `--header='Key: Value'` |
| 请求体解析 | `-d 'body'` / `--data` / `--data-raw` |
| 认证检测 | 自动识别 Authorization 头（Bearer/Basic/API Key） |
| 参数分离 | URL 中的 query string 自动拆分为 requestParams |
| 编码生成 | 从 URL 路径自动生成 code 和 name |
| 去重处理 | 编码冲突时自动追加时间戳后缀 |

### 1.5 接口文档

根据接口配置自动生成文档，支持在线浏览和 Word 导出。

| 特性 | 说明 |
|------|------|
| 在线文档 | 按分组聚合展示，含请求参数、响应结构、认证信息 |
| Word 导出 | 全部接口或按分组导出 .docx 文件 |
| 模板替换 | 基于 .docx 模板的占位符替换，零 POI 依赖 |

---

## 二、数据模型

### 2.1 实体关系

```
API_CONFIG (接口配置，config 库)
    └── INVOKE_LOG (调用日志，log 库，一对多)
    └── TOKEN_CACHE (Token 缓存，token 库，一对多)

ENVIRONMENT (环境配置，config 库)
    └── 通过 systemName 与 API_CONFIG.groupName 关联
```

### 2.2 API_CONFIG 接口配置表（config 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键，雪花 ID |
| NAME | VARCHAR(100) | 接口名称，用于展示 |
| CODE | VARCHAR(50) | 接口编码，唯一，调用时的标识 |
| DESCRIPTION | VARCHAR(500) | 接口描述 |
| METHOD | VARCHAR(10) | HTTP 方法：GET/POST/PUT/DELETE/PATCH |
| URL | VARCHAR(500) | 目标 URL，支持 `{{param}}` 占位符 |
| CONTENT_TYPE | VARCHAR(50) | 请求内容类型：JSON/FORM/XML/TEXT |
| HEADERS | TEXT | 请求头配置（AES-256-GCM 加密存储） |
| REQUEST_PARAMS | TEXT | URL 查询参数（JSON 格式） |
| REQUEST_BODY | TEXT | 请求体模板（AES-256-GCM 加密存储） |
| AUTH_TYPE | VARCHAR(20) | 认证类型：NONE/BASIC/BEARER/API_KEY/DYNAMIC |
| AUTH_INFO | VARCHAR(500) | 认证信息（AES-256-GCM 加密存储） |
| TIMEOUT | INT | 超时时间（毫秒），默认 30000 |
| RETRY_COUNT | INT | 失败重试次数，默认 0 |
| ENABLE_CACHE | BOOLEAN | 是否启用 Redis 响应缓存 |
| CACHE_TIME | INT | 缓存有效期（秒） |
| STATUS | VARCHAR(20) | 状态：ACTIVE/INACTIVE |
| GROUP_NAME | VARCHAR(100) | 分组名称（关联环境配置的 systemName） |
| **版本控制** | | |
| VERSION | VARCHAR(20) | 版本号，如 v1/v2/v3，默认 v1 |
| BASE_CODE | VARCHAR(50) | 基础编码，同接口多版本的共同标识 |
| LATEST_VERSION | BOOLEAN | 是否为最新推荐版本 |
| DEPRECATED | BOOLEAN | 是否已废弃 |
| **频率限制** | | |
| ENABLE_RATE_LIMIT | BOOLEAN | 是否启用频率限制 |
| RATE_LIMIT_WINDOW | INT | 限流时间窗口（秒） |
| RATE_LIMIT_MAX | INT | 窗口内最大请求数 |
| **动态 Token** | | |
| ENABLE_DYNAMIC_TOKEN | BOOLEAN | 是否启用动态 Token |
| TOKEN_API_CODE | VARCHAR(50) | 获取 Token 的接口编码 |
| TOKEN_EXTRACT_PATH | VARCHAR(200) | Token 提取路径（JSONPath） |
| TOKEN_POSITION | VARCHAR(20) | Token 传递位置：header/url/body |
| TOKEN_PARAM_NAME | VARCHAR(100) | Token 参数名 |
| TOKEN_PREFIX | VARCHAR(50) | Token 前缀（如 Bearer） |
| TOKEN_CACHE_TIME | INT | Token 缓存时间（秒） |
| **Token 自动缓存** | | |
| ENABLE_TOKEN_CACHE | BOOLEAN | 是否启用 Token 自动缓存 |
| TOKEN_CACHE_SECONDS | INT | Token 自动缓存时长（秒），默认 3600 |
| **审计字段** | | |
| CREATED_AT / UPDATED_AT | DATETIME | 创建/更新时间 |
| CREATED_BY_ID / UPDATED_BY_ID | BIGINT | 创建人/更新人 ID |
| CREATED_BY_NAME / UPDATED_BY_NAME | VARCHAR(50) | 创建人/更新人名称 |

**唯一约束**：`(BASE_CODE, VERSION)` — 同一基础编码下版本号唯一。

### 2.3 T_ENVIRONMENT 环境配置表（config 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键，自增 |
| SYSTEM_NAME | VARCHAR(100) | 系统名称（对应接口的 groupName） |
| ENV_NAME | VARCHAR(20) | 环境名称：DEV/TEST/PRE/PROD |
| BASE_URL | VARCHAR(500) | 基础 URL（如 `https://api-dev.example.com`） |
| DESCRIPTION | VARCHAR(500) | 描述 |
| STATUS | VARCHAR(20) | ACTIVE/INACTIVE，同系统只能一个 ACTIVE |
| URL_REPLACE | BOOLEAN | 是否启用域名替换，默认 true |
| CREATED_AT / UPDATED_AT | DATETIME | 创建/更新时间 |

### 2.4 INVOKE_LOG 调用日志表（log 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键，雪花 ID |
| API_CODE | VARCHAR(50) | 接口编码 |
| REQUEST_URL | TEXT | 实际请求 URL |
| REQUEST_PARAMS | TEXT | 请求参数（JSON） |
| REQUEST_HEADERS | TEXT | 请求头（JSON） |
| REQUEST_BODY | TEXT | 请求体 |
| RESPONSE_STATUS | INT | HTTP 响应状态码 |
| RESPONSE_DATA | TEXT | 响应数据（最多 10000 字符） |
| SUCCESS | BOOLEAN | 是否成功 |
| ERROR_MESSAGE | TEXT | 错误信息 |
| COST_TIME | BIGINT | 耗时（毫秒） |
| CLIENT_IP | VARCHAR(50) | 客户端 IP |
| TRACE_ID | VARCHAR(50) | 链路追踪 ID |
| INVOKE_TIME | DATETIME | 调用时间 |
| RETRY_ATTEMPT | INT | 重试序号（0=首次，1=第1次重试） |

---

## 三、接口配置管理

### 3.1 创建接口

**请求**：`POST /api/config`

```json
{
  "name": "获取用户信息",
  "code": "user-info",
  "method": "GET",
  "url": "https://api.example.com/users/{{userId}}",
  "contentType": "JSON",
  "headers": "{\"Accept\":\"application/json\"}",
  "requestParams": "{\"fields\":\"basic\"}",
  "authType": "BEARER",
  "authInfo": "eyJhbGciOiJIUzI1NiJ9...",
  "timeout": 30000,
  "retryCount": 0,
  "groupName": "UserService"
}
```

**自动处理**：
- `version` 默认设为 `v1`
- `baseCode` 默认等于 `code`
- `latestVersion` 默认为 `true`
- `deprecated` 默认为 `false`
- `status` 默认为 `ACTIVE`

### 3.2 更新接口

**请求**：`PUT /api/config/{id}`

> 编辑时禁止修改 `version` 和 `baseCode`，服务端会强制保留原值。

### 3.3 版本控制

#### 版本号规则

```
v1 → v2 → v3 → ... → v10 → v11
```

编码格式：`{baseCode}:{version}`（如 `user-api:v2`）

#### 创建新版本

**请求**：`POST /api/config/{sourceId}/version`

```json
{
  "name": null,           // null 保留源接口名称，自动替换版本号
  "url": "https://...",   // 非空则覆盖
  "description": null      // null 保留源接口描述
}
```

**自动处理**：
1. 从源接口完整复制所有配置字段
2. 生成新版本号（`nextVersion`：v2→v3）
3. 新编码 = `baseCode + ":" + newVersion`
4. 源版本的 `latestVersion` 设为 `false`
5. 新版本 `latestVersion = true`、`deprecated = false`
6. 名称智能替换：`"用户接口 v2"` → `"用户接口 v3"`；不含版本号则追加

#### 删除版本

删除最新版本时，自动将上一个版本设为 `latestVersion = true`。如果该版本已废弃（`deprecated = true`），则自动恢复为 `false`。

#### 设为推荐版本

`POST /api/config/{id}/set-latest` — 将指定版本设为推荐版本，同组其他版本的 `latestVersion` 自动取消。

#### 废弃/恢复

`POST /api/config/{id}/deprecate` — 切换 `deprecated` 状态。废弃接口仍可调用，SDK 调用时返回警告。

### 3.4 认证方式

| authType | authInfo 格式 | 注入方式 |
|----------|--------------|----------|
| `NONE` | — | 不添加认证头 |
| `BASIC` | `base64(username:password)` 或纯值 | `Authorization: Basic {authInfo}` |
| `BEARER` | Token 值（不带 `Bearer ` 前缀） | `Authorization: Bearer {authInfo}` |
| `API_KEY` | `HeaderName:Value` 或纯 Key 值 | `HeaderName: Value` 或 `X-API-Key: {authInfo}` |
| `DYNAMIC` | 空（由动态 Token 字段控制） | 根据 `tokenPosition` 注入到 header/url/body |

> **注意**：`authInfo` 存储时会自动去除已有的 `Bearer `/`Basic ` 前缀，注入时重新拼接，保证格式统一。

### 3.5 动态 Token

启用 `enableDynamicToken` 后，调用接口前自动获取 Token 并注入。

**执行流程**：

```
调用接口 → 检查 enableDynamicToken
    │ YES
    ├─ 1. 从 TokenCacheManager 读取缓存 Token
    │     ├─ 命中 → 直接使用
    │     └─ 未命中 → 调用 tokenApiCode 对应的接口
    │           ├─ 成功 → 用 tokenExtractPath 从响应提取 Token
    │           │         写入缓存（tokenCacheTime 秒）
    │           └─ 失败 → 抛出异常
    │
    ├─ 2. 按 tokenPosition 注入 Token
    │     ├─ header → headers.set(tokenParamName, tokenPrefix + token)
    │     ├─ url    → 作为 URL 参数附加
    │     └─ body   → 注入到请求体 JSON
    │
    └─ 3. 继续正常调用流程
```

**Token 自动缓存**（`enableTokenCache`）：

与动态 Token 不同，这是**当前接口自身**的 Token 缓存机制。调用成功后，自动从响应中提取 Token 写入缓存表，供后续调用或其他场景步骤引用。

- 默认提取路径：`$.data.accessToken`
- 默认缓存时长：3600 秒

### 3.6 频率限制

基于 Redis 固定窗口计数器实现。

**配置**：
- `enableRateLimit`：是否启用
- `rateLimitWindow`：时间窗口（秒）
- `rateLimitMax`：窗口内最大请求数

**限流 Key**：`integration:ratelimit:{apiCode}:{userId}`

**超限响应**：HTTP 429，消息 `"调用频率超限: 每秒最多 N 次（窗口 M 秒）"`

### 3.7 响应缓存

基于 Redis 的接口级响应缓存。

**缓存 Key**：`{apiCode}:{paramsJson}`

**流程**：
1. `enableCache = true` → 检查 Redis 缓存
2. 命中 → 直接返回缓存数据，`fromCache = true`
3. 未命中 → 正常调用 → 成功后写入缓存（TTL = `cacheTime`）

### 3.8 失败重试

`invokeWithRetry()` 仅对以下情况重试：
- HTTP 5xx 服务端错误
- HTTP 429 限流
- 连接异常（超时/拒绝）

**不重试**：4xx 客户端错误（除 429 外）

**重试策略**：
- 递增等待：1s → 2s → 3s
- 最大重试次数：由 `retryCount` 配置（默认 0，最大 5）
- 重试耗尽后，响应消息追加 `"(已重试 N 次)"`

---

## 四、环境配置

### 4.1 设计原理

通过环境配置，实现**同一接口配置在不同环境中使用不同的域名**：

```
接口配置 URL: https://api.example.com/users/list
                ↓ 环境替换
DEV 环境:  https://api-dev.example.com/users/list
TEST 环境: https://api-test.example.com/users/list
PROD 环境: https://api.example.com/users/list
```

**关联方式**：环境配置的 `systemName` 与接口配置的 `groupName` 对应。

### 4.2 互斥激活

同一 `systemName` 下只能有一个 `ACTIVE` 环境。激活新环境时：
1. 同系统的其他环境自动设为 `INACTIVE`
2. 自动清理 Token 缓存表（`TOKEN_CACHE`）
3. 自动清理场景缓存表（`SCENARIO_CACHE`）

> 缓存清理的原因：不同环境的 Token 和数据不通用，切换环境后旧缓存会导致请求错误。

### 4.3 URL 域名替换

当 `integration.environmentEnabled = true` 且环境配置的 `urlReplace = true` 时，接口调用前自动替换 URL 域名。

**替换规则**：

```
原始 URL:      https://api.example.com:8080/users/list?page=1
环境 baseUrl:  https://api-dev.example.com

替换后:        https://api-dev.example.com/users/list?page=1
```

**保留**：path、query string、fragment  
**替换**：scheme://host:port

**实现方式**：`applyEnvironmentUrl()` 克隆 ApiConfig 对象（不修改原对象），替换 URL 后传递给后续调用流程。

### 4.4 全局开关

```yaml
integration:
  environment-enabled: true   # 是否启用环境配置功能
```

关闭后，接口调用不进行环境替换，直接使用配置的原始 URL。

---

## 五、在线调试

### 5.1 调用方式

**POST 方式**（推荐，支持动态参数）：

```bash
POST /api/invoke
{
  "apiCode": "user-info",
  "params": {
    "userId": "12345"
  },
  "headers": {
    "X-Custom-Header": "value"
  },
  "debug": true
}
```

**GET 方式**（简化调试）：

```bash
GET /api/invoke/{apiCode}
```

### 5.2 调用流程

```
InvokeController.invoke()
    │
    ├─ 1. 权限检查
    │     ├─ api:invoke 权限
    │     └─ 角色级 API 访问权限（roleService.hasApiAccess）
    │
    ├─ 2. 频率限制检查
    │     └─ RateLimitService.tryAcquire() → 超限抛 429
    │
    └─ 3. HttpInvokeService.invoke()
          ├─ 获取接口配置
          ├─ 检查 Redis 缓存
          ├─ 获取动态 Token
          ├─ 环境配置 URL 替换
          ├─ 构建请求（URL/Headers/Body）
          ├─ 带重试的 HTTP 调用
          ├─ 解析响应
          ├─ 写入缓存
          ├─ Token 自动缓存
          └─ 记录调用日志（非 debug 模式）
```

### 5.3 InvokeRequestDTO

| 字段 | 类型 | 说明 |
|------|------|------|
| apiCode | String | 接口编码（必填） |
| params | Map<String, Object> | 动态参数，替换 URL 中的 `{{param}}` |
| headers | Map<String, String> | 动态请求头（覆盖配置头） |
| body | String | 请求体（跳过模板时直接使用） |
| skipTemplate | Boolean | 是否跳过模板替换，直接使用 body |
| debug | Boolean | 调试模式，不记录日志 |

### 5.4 InvokeResponseDTO

| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | 是否成功 |
| statusCode | Integer | HTTP 状态码 |
| data | Object | 响应数据（JSON 解析后的对象） |
| message | String | 消息 |
| costTime | Long | 耗时（毫秒） |
| invokeTime | LocalDateTime | 调用时间 |
| traceId | String | 链路追踪 ID |
| fromCache | Boolean | 是否来自缓存 |
| requestUrl | String | 实际请求 URL |

### 5.5 调用日志查询

| 接口 | 说明 |
|------|------|
| `GET /api/invoke/logs` | 分页查询，支持 apiCode/status/时间范围/URL/Body 模糊搜索 |
| `GET /api/invoke/logs/detail/{id}` | 单条日志详情 |
| `GET /api/invoke/logs/{apiCode}/recent` | 最近 10 条调用 |
| `DELETE /api/invoke/logs` | 批量删除 |

---

## 六、Curl 导入

### 6.1 解析流程

```
CurlParser.parse(curlCommand)
    │
    ├─ 1. 预处理
    │     ├─ 去掉开头的 "curl "
    │     ├─ 合并续行符（\ → 空格）
    │     └─ 压缩多余空格
    │
    ├─ 2. 提取 URL
    │     ├─ 优先匹配引号包裹：'https://...' 或 "https://..."
    │     └─ 降级匹配裸 URL：https://...
    │
    ├─ 3. 提取方法
    │     ├─ -X POST / --request=POST（显式指定）
    │     ├─ 有 -d/--data 隐式推断为 POST
    │     └─ 默认 GET
    │
    ├─ 4. 提取请求头
    │     ├─ -H 'Key: Value' / --header='Key: Value'
    │     └─ 清理系统头：Host/Content-Length/Connection/Sec-Fetch-*
    │
    ├─ 5. 提取请求体
    │     └─ -d 'body' / --data / --data-raw
    │
    ├─ 6. 认证检测
    │     ├─ Authorization: Bearer xxx → BEARER
    │     ├─ Authorization: Basic xxx  → BASIC
    │     ├─ X-API-Key: xxx           → API_KEY
    │     └─ 无认证头                  → NONE
    │
    ├─ 7. 参数分离
    │     ├─ URL 含 ? → query string 拆为 requestParams JSON
    │     └─ URL 本身去掉 query string
    │
    └─ 8. 自动生成
          ├─ code：从 URL 路径生成（如 /api/users/list → api-users-list）
          ├─ name：从 URL 路径最后一段生成（如 List）
          └─ groupName：从 URL 路径第一段生成（如 Api）
```

### 6.2 支持的 curl 格式

```bash
# 基本 GET
curl 'https://api.example.com/users?page=1&size=10'

# POST + JSON Body
curl -X POST 'https://api.example.com/users' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer eyJhbGci...' \
  -d '{"name":"张三","age":25}'

# 带 API Key
curl 'https://api.example.com/data' \
  -H 'X-API-Key: abc123' \
  -H 'Accept: application/json'

# POST + Form
curl -X POST 'https://api.example.com/login' \
  -d 'username=admin&password=123456'
```

### 6.3 导入接口

**请求**：`POST /api/config/import/curl`

```json
{
  "curl": "curl -X POST 'https://api.example.com/users' -H 'Content-Type: application/json' -H 'Authorization: Bearer eyJhbGci...' -d '{\"name\":\"张三\"}'"
}
```

**响应**：返回导入成功后的接口编码。

**处理逻辑**：
1. 调用 `CurlParser.parse()` 解析
2. 编码冲突时自动追加时间戳后缀
3. 自动保存到数据库
4. 返回完整的 `ApiConfigDTO`

---

## 七、接口文档

### 7.1 在线文档

**按分组获取**：`GET /api/doc/list`

返回 `List<ApiDocGroup>`，每个分组包含：

```json
{
  "groupName": "UserService",
  "items": [
    {
      "id": 123,
      "code": "user-info",
      "name": "获取用户信息",
      "method": "GET",
      "url": "https://api.example.com/users/{{userId}}",
      "version": "v1",
      "latestVersion": true,
      "deprecated": false,
      "description": "根据用户ID获取用户基本信息"
    }
  ]
}
```

**单个接口详情**：`GET /api/doc/{id}`

返回完整的接口文档，含：

```json
{
  "id": 123,
  "code": "user-info",
  "name": "获取用户信息",
  "method": "GET",
  "url": "https://api.example.com/users/{{userId}}",
  "contentType": "JSON",
  "authType": "BEARER",
  "timeout": 30000,
  "retryCount": 0,
  "queryParams": [
    { "name": "userId", "in": "query", "required": true, "type": "string" }
  ],
  "bodyParams": [],
  "headers": [
    { "name": "Accept", "in": "header", "required": true, "type": "string", "example": "application/json" }
  ]
}
```

### 7.2 Word 导出

**导出全部**：`GET /api/doc/export` → 下载 `接口文档_2026-04-25.docx`

**按分组导出**：`GET /api/doc/export/group?groupName=UserService` → 下载 `接口文档_UserService_2026-04-25.docx`

**导出原理**：

```
1. 加载 .docx 模板（ZIP 格式）
2. 解压读取 word/document.xml
3. 定位含 {{...}} 占位符的段落作为接口单元模板
4. 对每个接口：
   ├─ 克隆模板段落
   ├─ 替换占位符为实际值
   │   ├─ 简单值：直接替换
   │   └─ 含换行值：拆为多个 <w:t> + <w:br/>
   └─ 拼接到文档
5. 生成分组标题段落（深蓝色粗体 14pt）
6. 替换统计区占位符
7. 重打包为 .docx 输出
```

**模板占位符**：

| 占位符 | 值 |
|--------|-----|
| `{{name}}` | 接口名称 |
| `{{code}}` | 接口编码 |
| `{{url}}` | 请求 URL |
| `{{method}}` | HTTP 方法 |
| `{{contentType}}` | 内容类型 |
| `{{authType}}` | 认证方式 |
| `{{timeout}}` | 超时时间 |
| `{{headers}}` | 请求头 |
| `{{requestParams}}` | 请求参数 |
| `{{requestBody}}` | 请求体 |
| `{{description}}` | 描述 |
| `{{version}}` | 版本号 |
| `{{latestVersion}}` | 是否最新版本（是/否） |
| `{{deprecated}}` | 是否废弃（是/否） |
| `{{totalCount}}` | 接口总数 |
| `{{groupList}}` | 分组名称列表 |
| `{{exportTime}}` | 导出时间 |

---

## 八、API 接口汇总

### 8.1 接口配置

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/config` | api:add | 创建接口 |
| PUT | `/api/config/{id}` | api:edit | 更新接口 |
| DELETE | `/api/config/{id}` | api:delete | 删除接口 |
| GET | `/api/config/{id}` | api:detail | 获取接口详情（含统计+版本信息） |
| GET | `/api/config/code/{code}` | api:detail | 根据编码查询 |
| GET | `/api/config/page` | api:view | 分页查询（支持版本筛选） |
| GET | `/api/config/active` | api:view | 获取所有启用接口 |
| POST | `/api/config/{id}/toggle` | api:edit | 启用/停用切换 |
| GET | `/api/config/simple-list` | api:view | 简化列表（下拉选择用） |
| POST | `/api/config/{id}/version` | api:version | 创建新版本 |
| GET | `/api/config/{id}/versions` | api:detail | 获取所有版本 |
| POST | `/api/config/{id}/set-latest` | api:version | 设为推荐版本 |
| POST | `/api/config/{id}/deprecate` | api:deprecate | 切换废弃状态 |
| POST | `/api/config/import/curl` | api:add | Curl 命令导入 |

### 8.2 环境配置

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/environment` | env:add | 创建环境 |
| PUT | `/api/environment/{id}` | env:edit | 更新环境 |
| DELETE | `/api/environment/{id}` | env:delete | 删除环境 |
| GET | `/api/environment/{id}` | env:detail | 获取环境详情 |
| GET | `/api/environment/list` | env:view | 分页查询环境列表 |
| GET | `/api/environment/systems` | env:view | 获取所有系统名称 |
| GET | `/api/environment/by-system/{systemName}` | env:view | 获取指定系统的所有环境 |

### 8.3 接口调用

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/invoke` | api:invoke | POST 调用接口 |
| GET | `/api/invoke/{apiCode}` | api:invoke | GET 调用接口 |
| GET | `/api/invoke/logs` | log:view | 查询调用日志 |
| GET | `/api/invoke/logs/detail/{id}` | invoke-log:detail | 日志详情 |
| GET | `/api/invoke/logs/{apiCode}/recent` | log:view | 最近调用记录 |
| DELETE | `/api/invoke/logs` | invoke-log:delete | 批量删除日志 |

### 8.4 接口文档

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/doc/list` | api:view | 按分组获取文档列表 |
| GET | `/api/doc/{id}` | api:detail | 单个接口文档详情 |
| GET | `/api/doc/export` | api:export | 导出全部接口文档（Word） |
| GET | `/api/doc/export/group` | api:export | 按分组导出（Word） |

---

## 九、前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 接口列表 | `pages/api_list.html` | 卡片概览 + 表格 + 搜索 + 版本筛选 + 分页 |
| 接口详情 | `pages/api_detail.html` | 完整配置展示 + 版本切换 + 调用统计 |
| 接口编辑 | `pages/api_form.html` | 创建/编辑表单 + 认证配置 + 动态Token + 频率限制 |
| 在线调试 | `pages/api_debug.html` | 参数输入 + 执行调用 + 实时响应展示 |
| Curl 导入 | `pages/api_import.html` | curl 命令输入 + 解析预览 + 一键创建 |
| 接口文档 | `pages/api_doc.html` | 分组导航 + 接口文档展示 + Word 导出 |
| 调用日志 | `pages/api_logs.html` | 日志列表 + 搜索过滤 + 详情查看 |
| SDK 下载 | `pages/api_sdk.html` | 多语言 SDK 下载 |
| 环境列表 | `pages/environment_list.html` | 环境卡片 + 系统分组 + 状态切换 |
| 环境编辑 | `pages/environment_form.html` | 创建/编辑环境 + baseUrl 配置 |

---

## 十、加密与安全

### 10.1 AES-256-GCM 加密

以下字段使用 `EncryptedFieldConverter` 自动加密存储、自动解密读取：

| 字段 | 加密原因 |
|------|----------|
| `headers` | 可能包含 Authorization 等敏感头 |
| `requestBody` | 可能包含密码、Token 等敏感模板数据 |
| `authInfo` | 直接存储认证凭证（Token/密钥/密码） |

**实现方式**：JPA `@Convert(converter = EncryptedFieldConverter.class)`，读写自动加解密，业务代码无感知。

### 10.2 客户端 IP 获取

按优先级依次检查代理头：
1. `X-Real-IP`（Nginx）
2. `X-Forwarded-For`（多级代理取第一个）
3. `Ali-Cdn-Real-IP`（阿里云 SLB）
4. `X-Custom-Real-IP`（腾讯云 CLB）
5. `request.getRemoteAddr()`（直连）

---

## 十一、核心代码

### 11.1 HttpInvokeService 关键方法

```
invoke()                    — 调用入口：获取配置→缓存检查→动态Token→环境替换→构建请求→调用→日志
doInvoke()                  — 实际 HTTP 调用：构建URL/Headers/Body → RestTemplate.exchange
invokeWithRetry()           — 带重试封装：5xx/429/连接异常重试，4xx不重试
buildUrl()                  — 构建 URL：参数替换{{param}} + Token注入 + GET参数附加
buildHeaders()              — 构建请求头：Content-Type + 配置头 + 动态头 + 认证 + Token注入
buildBody()                 — 构建请求体：模板替换{{param}} + Token注入 + params降级为body
applyAuth()                 — 应用认证：Bearer/Basic/API_KEY，自动去前缀再拼接
applyEnvironmentUrl()       — 环境替换：按groupName查找活跃环境，替换URL域名
obtainDynamicToken()        — 获取动态Token：缓存→调用Token接口→提取→缓存
extractAndCacheToken()      — Token自动缓存：调用成功后从响应提取Token写入缓存表
parseResponse()             — 解析响应：JSON解析或原始字符串（HTML/非JSON）
replaceUrlDomain()          — URL域名替换：保留path/query/fragment
```

### 11.2 CurlParser 关键方法

```
parse()                     — 入口：预处理→提取URL/Method/Headers/Body→认证检测→参数分离→自动生成
preprocess()                — 去curl前缀、合并续行、压缩空格
extractUrl()                — 提取URL（优先引号包裹，降级裸URL）
extractMethod()             — 提取方法（-X显式，-d隐式推断POST）
extractHeaders()            — 提取请求头（-H / --header）
extractBody()               — 提取请求体（-d / --data / --data-raw）
detectAuth()                — 认证检测（Bearer/Basic/API_KEY）
generateCode()              — 从URL路径生成编码
generateName()              — 从URL路径生成名称
parseQueryStringToJson()    — QueryString → JSON
```

### 11.3 环境相关方法

```
EnvironmentService.create()          — 创建环境，互斥激活+缓存清理
EnvironmentService.update()          — 更新环境，互斥激活+旧系统停用
EnvironmentService.getActiveEnvironment() — 查找系统当前活跃环境
HttpInvokeService.applyEnvironmentUrl()   — 调用时替换URL域名
```

---

## 十二、常见问题

**Q：环境切换后，之前的 Token 缓存还能用吗？**
A：不能。环境切换时自动清理 Token 缓存表和场景缓存表，因为不同环境的 Token 不通用。下次调用会重新获取。

**Q：URL 域名替换支持相对路径吗？**
A：支持。如果接口配置的 URL 以 `/` 开头（如 `/api/users`），会直接与环境的 baseUrl 拼接。如果是绝对 URL，只替换 scheme://host:port 部分，保留路径和参数。

**Q：Curl 导入时认证头会怎样处理？**
A：`Authorization` 头会被自动检测并归类为 BEARER/BASIC/API_KEY，从 headers 中移除后存入 `authType`/`authInfo` 字段。其他非系统头保留在 headers 中。

**Q：动态 Token 和 Token 自动缓存有什么区别？**
A：动态 Token（`enableDynamicToken`）是在调用**当前接口前**，先调用另一个接口（`tokenApiCode`）获取 Token 注入到请求中。Token 自动缓存（`enableTokenCache`）是当前接口调用成功后，从**当前接口的响应**中提取 Token 缓存起来，供其他场景引用。前者是"先获取再调用"，后者是"调用后缓存"。

**Q：接口的 `groupName` 和环境的 `systemName` 是什么关系？**
A：它们是同一个概念的不同命名。接口的 `groupName` 标识它属于哪个业务系统，环境的 `systemName` 也标识业务系统。两者相等时，环境配置才会对该接口生效。

**Q：Curl 导入生成的编码冲突怎么办？**
A：如果自动生成的编码已存在，会追加时间戳后缀（如 `api-users-423`）。建议导入后手动修改为更有意义的编码。

**Q：Word 导出的模板在哪里？**
A：模板文件为 `接口文档模板.docx`，放置在 `src/main/resources/static/` 目录下。模板中使用 `{{占位符}}` 标记需要替换的位置。
