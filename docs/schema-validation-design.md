# Schema 校验功能详细设计文档

> **文档版本**：v1.0
> **编写日期**：2026-05-10
> **功能模块**：Schema 校验
> **技术栈**：Spring Boot 3.2.3 + Java 17 + networknt json-schema-validator 1.0.87

---

## 1. 背景与目标

### 1.1 为什么需要 Schema 校验

在统一接口配置系统中，业务系统在调用下游第三方 API 之前，往往需要先校验传入的参数是否符合接口规范。传统做法是业务代码中硬编码校验逻辑，但存在以下问题：

- **重复编码**：每个接口都要写校验代码，枯燥且易遗漏
- **规则分散**：校验规则散落在各个业务模块，难以统一管控
- **下游压力**：无效请求仍会打到下游，浪费带宽且可能被限流
- **响应不可控**：下游返回的数据结构是否符合预期，业务侧无法提前感知

### 1.2 功能目标

在**上游**（HTTP 调用发起之前）对请求参数进行 Schema 校验，提前发现数据格式错误，避免无效请求打到下游。同时对**下游响应**进行 Schema 校验，确保返回数据结构符合预期。

**核心价值**：
- 在源头拦截脏数据，减少下游无效调用
- 统一校验规则，无需每个接口硬编码
- 对响应结构做约束，提前发现下游接口异常

---

## 2. 设计与架构

### 2.1 校验引擎选型

| 方案 | 优点 | 缺点 |
|------|------|------|
| 手写校验逻辑 | 无依赖 | 工作量大，规则难复用 |
| Jakarta Validation (JSR-380) | Spring 原生 | 适合 DTO，不适合动态 JSON |
| **networknt json-schema-validator** | 支持 draft-07、功能完善、缓存机制 | 需引入依赖 |
| everit-org/json-schema-validator | 支持 draft-04/draft-06 | 更新较慢 |

**选择理由**：`networknt/json-schema-validator` 是目前最活跃的 JSON Schema 校验库，支持 draft-07 标准，提供了 Schema 缓存机制，且依赖项简单（仅需 Jackson，已在项目中）。

### 2.2 校验时机

```
请求到达
   ↓
┌─────────────────────────────────────────────────┐
│ 1. 获取接口配置 (ApiConfigService)              │
│ 2. 【请求 Schema 校验】 ← 在此拦截无效请求        │
│ 3. 检查 Redis 缓存                              │
│ 4. 动态 Token 获取（可选）                       │
│ 5. 应用环境变量替换                              │
│ 6. 构建完整请求 URL                              │
│ 7. 【HTTP 调用】                                │
│    ↓                                           │
│    ├─ 成功 ─→ 【响应 Schema 校验】 ← 警告，不阻断  │
│    └─ 失败 ─→ 重试策略（5xx / 429 / 连接异常）    │
│ 8. 记录日志                                     │
└─────────────────────────────────────────────────┘
```

**关键设计决策**：
- 请求校验在**缓存检查之前**执行：校验失败的请求不写缓存，直接返回 400
- 响应校验仅对**调用成功**（HTTP 2xx）的响应进行，5xx 错误不参与校验
- 响应校验失败**不阻断**返回，仅在响应中附加警告信息

### 2.3 校验失败处理策略

| 校验类型 | HTTP 状态码 | 行为 | 是否记录日志 |
|---------|-----------|------|------------|
| 请求参数校验失败 | **400 Bad Request** | 立即返回，不请求下游 | ✅ 记录（标记为校验失败） |
| 响应结果校验失败 | **200 OK**（数据正常返回） | 附加 `schemaValidationError` 字段 | ✅ 记录警告 |

### 2.4 Schema 编译缓存

同一个接口的 Schema 在运行期间不会变化，因此采用内存缓存策略：

```
schemaCache: ConcurrentHashMap<String cacheKey, JsonSchema>
cacheKey = "schema:" + schemaStr.hashCode()
```

**优点**：
- 避免每次调用都重新解析和编译 JSON Schema
- 同一接口多次调用只编译一次
- 缓存量小（按需缓存），无需淘汰策略

---

## 3. 数据模型

### 3.1 ApiConfig 新增字段

```java
// Schema 校验
@Column(name = "ENABLE_REQUEST_SCHEMA")
private Boolean enableRequestSchema;   // 是否启用请求参数校验

@Column(name = "REQUEST_SCHEMA", columnDefinition = "TEXT")
private String requestSchema;            // 请求参数 JSON Schema（draft-07）

@Column(name = "ENABLE_RESPONSE_SCHEMA")
private Boolean enableResponseSchema;    // 是否启用响应结果校验

@Column(name = "RESPONSE_SCHEMA", columnDefinition = "TEXT")
private String responseSchema;           // 响应结果 JSON Schema（draft-07）
```

**说明**：
- 使用 `TEXT` 类型存储 Schema（可能较长）
- 三个 Boolean 字段默认为 `null`（即"禁用"状态），避免数据库默认值冲突

### 3.2 InvokeResponseDTO 新增字段

```java
// Schema 校验错误信息（请求校验失败时填充详细错误）
private String schemaValidationError;
```

### 3.3 ErrorCode 新增枚举

```java
/** Schema 校验失败 */
SCHEMA_VALIDATION_FAILED(400, "SCHEMA_VALIDATION_FAILED", "Schema validation failed"),
```

---

## 4. 核心服务

### 4.1 SchemaValidationService

**路径**：`com.integration.config.service.SchemaValidationService`

**主要方法**：

| 方法 | 入参 | 返回 | 说明 |
|------|------|------|------|
| `validateRequestBody` | schemaStr, body, traceId | String（错误信息）或 null | 校验请求体 |
| `validateRequestParams` | schemaStr, params (Map), traceId | String 或 null | 校验 Query Params |
| `validateResponse` | schemaStr, response, traceId | String 或 null | 校验响应结果 |
| `validateSchemaDefinition` | schemaStr | String 或 null | 校验 Schema 本身格式（编辑器用） |
| `clearCache` | — | void | 手动清空 Schema 缓存 |

**校验流程**（以 `validateRequestBody` 为例）：

```
1. 检查 schemaStr 是否为空 → 空则直接返回 null（不禁用则跳过）
2. 检查 body 是否为空 → 空则直接返回 null（允许空 body）
3. 将 body 转为 JsonNode（支持 String / Map / List / 其他对象）
4. getOrCompileSchema(schemaStr) → 从缓存获取或编译
5. schema.validate(data) → 返回 Set<ValidationMessage>
6. 若 messages 为空 → 返回 null（校验通过）
7. 若有消息 → 格式化为人类可读的错误报告并返回
```

**错误消息格式**：

```
请求体 Schema 校验失败（共 2 项错误）：
  1. 字段 [name]：缺少必填字段
  2. 字段 [age]：type error: expected type integer, found type string
```

### 4.2 HttpInvokeService 改造

**注入依赖**：
```java
private final SchemaValidationService schemaValidationService;
```

**请求校验**（步骤 1.1）：
```java
String validationError = validateRequestSchema(config, request, traceId);
if (validationError != null) {
    return buildValidationErrorResponse(validationError, traceId, startTime, logEntry);
}
```

**响应校验**（步骤 6.1）：
```java
if (response.getSuccess() && Boolean.TRUE.equals(config.getEnableResponseSchema())) {
    String respValidationError = schemaValidationService.validateResponse(
            config.getResponseSchema(), response.getData(), traceId);
    if (respValidationError != null) {
        response.setSchemaValidationError(respValidationError);
        log.warn("[{}] 响应 Schema 校验失败（接口调用成功）: {}", traceId, ...);
    }
}
```

### 4.3 InvokeController 改造

```java
InvokeResponseDTO response = httpInvokeService.invoke(request);
if (!response.getSuccess()) {
    // Schema 校验失败 → 400，错误信息包含详细校验报告
    if (response.getStatusCode() == 400
            && response.getSchemaValidationError() != null) {
        throw new BusinessException(ErrorCode.SCHEMA_VALIDATION_FAILED,
                response.getSchemaValidationError());
    }
    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
            "Invoke failed: " + response.getMessage());
}
return ResultVO.success(response);
```

---

## 5. JSON Schema draft-07 语法参考

### 5.1 基础类型

| type 值 | 说明 | 示例 |
|--------|------|------|
| `string` | 字符串 | `{"type": "string"}` |
| `number` | 浮点数 | `{"type": "number"}` |
| `integer` | 整数 | `{"type": "integer"}` |
| `boolean` | 布尔值 | `{"type": "boolean"}` |
| `array` | 数组 | `{"type": "array"}` |
| `object` | 对象 | `{"type": "object"}` |
| `null` | 空值 | `{"type": "null"}` |

### 5.2 常用约束

#### 字符串约束

```json
{
  "type": "string",
  "minLength": 1,
  "maxLength": 100,
  "pattern": "^[a-zA-Z]+$",
  "format": "email"
}
```

支持的 `format` 值：`email`、`uri`、`date-time`、`date`、`time`、`uuid`

#### 数值约束

```json
{
  "type": "integer",
  "minimum": 0,
  "maximum": 150,
  "exclusiveMaximum": true,
  "multipleOf": 2
}
```

#### 数组约束

```json
{
  "type": "array",
  "minItems": 1,
  "maxItems": 10,
  "uniqueItems": true,
  "items": {
    "type": "string"
  }
}
```

#### 对象约束

```json
{
  "type": "object",
  "properties": {
    "name": { "type": "string" },
    "age": { "type": "integer", "minimum": 0 }
  },
  "required": ["name"],
  "additionalProperties": false
}
```

#### 枚举约束

```json
{
  "type": "string",
  "enum": ["pending", "approved", "rejected"]
}
```

#### 组合约束

```json
{
  "type": "object",
  "oneOf": [
    { "properties": { "country": { "const": "US" } }, "required": ["country"] },
    { "properties": { "country": { "const": "CN" } }, "required": ["country"] }
  ]
}
```

### 5.3 完整示例

#### 请求 Schema 示例：创建用户

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "创建用户请求",
  "properties": {
    "username": {
      "type": "string",
      "minLength": 3,
      "maxLength": 20,
      "pattern": "^[a-zA-Z0-9_]+$",
      "description": "用户名，3-20位字母数字下划线"
    },
    "email": {
      "type": "string",
      "format": "email",
      "description": "邮箱地址"
    },
    "age": {
      "type": "integer",
      "minimum": 0,
      "maximum": 150
    },
    "tags": {
      "type": "array",
      "items": { "type": "string" },
      "minItems": 1,
      "maxItems": 5
    },
    "status": {
      "type": "string",
      "enum": ["active", "inactive", "pending"]
    }
  },
  "required": ["username", "email"],
  "additionalProperties": false
}
```

#### 响应 Schema 示例：分页数据

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "分页响应",
  "properties": {
    "code": {
      "type": "integer",
      "description": "业务状态码，0表示成功"
    },
    "message": {
      "type": "string",
      "description": "提示信息"
    },
    "data": {
      "type": "object",
      "properties": {
        "total": { "type": "integer", "minimum": 0 },
        "page": { "type": "integer", "minimum": 1 },
        "size": { "type": "integer", "minimum": 1 },
        "records": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": { "type": "integer" },
              "name": { "type": "string" }
            }
          }
        }
      },
      "required": ["total", "records"]
    }
  },
  "required": ["code", "data"]
}
```

---

## 6. 前端设计

### 6.1 页面入口

**接口编辑页**：`api_form.html`
新增「🔍 Schema 校验」配置区块，位于「⚡ 频率限制」和「其他」之间。

### 6.2 UI 布局

```
🔍 Schema 校验
┌─ 启用请求参数校验 ─┐  ┌─ 启用响应结果校验 ─┐
│ ⛔ 禁用 / ✅ 启用  │  │ ⛔ 禁用 / ✅ 启用  │
└──────────────────┘  └──────────────────┘

▼ 启用请求参数校验后展开：
请求参数 Schema（JSON Schema draft-07）
┌──────────────────────────────────────────┐
│ {                                         │
│   "type": "object",                       │
│   "properties": {...},                    │
│   "required": ["name"]                    │
│ }                                         │
└──────────────────────────────────────────┘ [🔍 校验定义] [示例]

提示：支持 JSON Schema draft-07 标准...
```

### 6.3 实时校验按钮

用户粘贴 Schema 后，点击「🔍 校验定义」按钮，前端先用 `JSON.parse()` 做基础语法检查：
- 语法正确 → 显示绿色「✅ Schema 定义格式正确」（3秒后自动消失）
- 语法错误 → 显示红色错误信息

### 6.4 切换交互

- `enableRequestSchema` 切换为「启用」→ 展开请求 Schema 编辑区
- `enableResponseSchema` 切换为「启用」→ 展开响应 Schema 编辑区
- 切换为「禁用」→ 收起对应编辑区

### 6.5 编辑模式数据加载

编辑已配置的接口时：
1. 自动回填 `enableRequestSchema` 和 `enableResponseSchema` 值
2. 触发 `change` 事件，展开对应的 Schema 编辑区
3. 回填 `requestSchema` 和 `responseSchema` 的 JSON 内容

---

## 7. API 响应格式

### 7.1 请求校验失败（HTTP 400）

```json
{
  "code": 400,
  "message": "请求体 Schema 校验失败（共 2 项错误）:\n  1. 字段 [name]：缺少必填字段\n  2. 字段 [age]：type error: expected type integer, found type string",
  "data": null
}
```

### 7.2 响应校验失败（HTTP 200）

响应数据正常返回，`schemaValidationError` 字段包含警告：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "statusCode": 200,
    "data": { ... },
    "message": "调用成功",
    "costTime": 235,
    "traceId": "...",
    "fromCache": false,
    "requestUrl": "https://...",
    "schemaValidationError": "响应结果 Schema 校验失败（共 1 项错误）:\n  1. 字段 [data/userId]：type error: expected type integer, found type string"
  }
}
```

### 7.3 调用日志记录

请求校验失败的日志：

```
success: false
responseStatus: 400
errorMessage: Schema校验失败: 请求体 Schema 校验失败（共 2 项错误）...
```

响应校验失败的日志（调用仍成功）：
- `success: true`
- `errorMessage: null`
- 但日志中会打印 WARN 级别的校验警告

---

## 8. 权限与安全

### 8.1 权限控制

Schema 校验功能复用接口编辑的权限（`api:save` / `api:create`），无需新增权限码。

### 8.2 Schema 注入风险

**潜在风险**：恶意用户通过在 Schema 中定义 `$ref` 引用外部 URL 可能导致 SSRF。

**防护措施**：
- 不支持 `$ref` 远程引用（networknt 默认不支持 HTTP URL 引用）
- Schema 存储在数据库中，由管理员配置，受 RBAC 权限保护
- Schema 仅服务端使用，不返回给调用方

### 8.3 Schema 校验异常处理

- Schema 格式错误（无法解析）：在编辑器实时校验时提示，不阻止保存
- 校验执行时异常（编译错误等）：记录 ERROR 日志，返回 `schemaValidationError` 描述异常信息，不阻断正常返回

---

## 9. 性能考量

### 9.1 Schema 编译开销

| 场景 | 开销 |
|------|------|
| Schema 首次编译 | ~1-5ms（取决于 Schema 复杂度） |
| Schema 缓存命中 | < 0.1ms |
| 实际 HTTP 调用 | 数十ms ~ 数s（网络延迟主导） |

**结论**：Schema 编译开销相对于 HTTP 调用本身可忽略不计。

### 9.2 缓存策略

- 缓存 Key：`"schema:" + schemaStr.hashCode()`
- 同一接口多次调用 Schema 只编译一次
- Schema 更新后（接口重新保存）自动使用新 Schema，旧缓存自然失效
- 缓存大小上限：理论上一个接口最多一个 Schema，通常不超过几百个

---

## 10. 测试建议

### 10.1 单元测试（SchemaValidationService）

| 测试用例 | 输入 | 期望结果 |
|---------|------|---------|
| 有效 JSON + 符合 Schema | `{"name": "Tom", "age": 25}` + 正确 Schema | `null` |
| 缺少必填字段 | `{"age": 25}` + required ["name"] | 返回 name 缺失错误 |
| 字段类型错误 | `{"name": "Tom", "age": "25"}`（string）| 返回类型错误 |
| 空 Schema | `null` 或 `""` | 返回 `null`（跳过校验）|
| 非法 JSON Schema | `{"type": "INVALID"}` | 返回 Schema 解析错误 |
| 嵌套对象校验 | 包含嵌套 properties 的对象 | 正确校验深层次字段 |

### 10.2 集成测试（端到端）

| 测试场景 | 操作 | 期望 |
|---------|------|------|
| 请求校验拦截 | body 不符合 Schema 的请求 | HTTP 400，不请求下游 |
| 响应校验警告 | 成功响应但不符合 Schema | HTTP 200，data 中含警告 |
| 缓存前拦截 | 相同接口连续两次错误请求 | 两次均返回 400 |
| Schema 切换 | 修改接口 Schema 后调用 | 使用新版 Schema 校验 |

---

## 11. 未来扩展方向

1. **Schema 在线生成**：基于历史请求/响应数据，自动推断并生成 Schema（减少手工编写成本）
2. **Schema 版本管理**：不同版本的接口对应不同的 Schema，纳入现有版本控制体系
3. **Schema 注册中心**：统一管理可复用的 Schema 片段（`$defs`），支持跨接口引用
4. **异步告警**：当响应 Schema 校验失败次数超过阈值时，触发告警通知
5. **OpenAPI 导入**：从 OpenAPI 3.0 规范自动提取 requestSchema / responseSchema

---

## 12. 变更记录

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|---------|------|
| 2026-05-10 | v1.0 | 初始实现：请求校验 + 响应校验 | QClaw |
