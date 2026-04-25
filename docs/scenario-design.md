# 场景编排设计文档

> 本文档描述场景编排功能的设计与实现。

---

## 一、功能概述

场景编排（Scenario Orchestration）将多个 API 接口按顺序组合成一个自动化流程，前序步骤的响应可提取后作为后序步骤的输入参数，支持条件判断、失败跳过、两种失败策略和步骤级缓存。

### 1.1 核心特性

| 特性 | 说明 |
|------|------|
| 多步骤编排 | 一个场景包含多个步骤，按 `stepOrder` 顺序执行 |
| 输入映射 | 支持 static/input/step/cache/token 五种来源 |
| 输出映射 | 通过 JsonPath 表达式从响应中提取字段，存入执行上下文 |
| 条件表达式 | 接口调用**之后**判断结果是否满足预期，不满足则标记失败 |
| 失败跳过 | `skipOnError=1` 时，前序步骤失败则自动跳过当前步骤 |
| 失败策略 | STOP（遇错终止）/ CONTINUE（遇错继续），控制全局行为 |
| 步骤缓存 | `enableCache` 开启后，缓存命中时跳过 API 调用，未命中则调用并写入缓存 |
| 执行日志 | 每一步记录请求参数、响应数据和耗时，缓存命中也会记录日志 |

### 1.2 典型应用场景

- **OAuth 认证流程**：获取授权码 → 换取 Access Token → 获取用户信息
- **订单创建流程**：校验库存 → 创建订单 → 扣减库存 → 发送通知
- **数据同步流程**：查询源系统 → 转换格式 → 写入目标系统 → 记录日志
- **Token 缓存复用**：首次获取 Token 后缓存，后续执行直接从缓存读取，避免重复调用

---

## 二、数据模型

### 2.1 实体关系

```
SCENARIO (场景，config 库)
    └── SCENARIO_STEP (步骤，config 库，多对一)

SCENARIO_EXECUTION (执行记录，log 库)
    └── SCENARIO_STEP_LOG (步骤日志，log 库，一对多)

SCENARIO_CACHE (场景缓存，token 库)
    └── 按 scenarioCode + stepCode + outputKey 存储
```

### 2.2 SCENARIO 场景表（config 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键，雪花 ID |
| CODE | VARCHAR(50) | 场景编码，唯一 |
| NAME | VARCHAR(100) | 场景名称 |
| DESCRIPTION | VARCHAR(500) | 描述 |
| GROUP_NAME | VARCHAR(50) | 分组名称（如"订单流程"） |
| FAILURE_STRATEGY | VARCHAR(20) | 失败策略：STOP / CONTINUE |
| TIMEOUT_SECONDS | INT | 超时时间（秒），默认 300 |
| STATUS | VARCHAR(20) | ACTIVE / INACTIVE |
| CREATED_AT | DATETIME | 创建时间 |
| UPDATED_AT | DATETIME | 更新时间 |

### 2.3 SCENARIO_STEP 步骤表（config 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键，雪花 ID |
| SCENARIO_ID | BIGINT | 所属场景 ID |
| STEP_CODE | VARCHAR(50) | 步骤编码（场景内唯一） |
| STEP_NAME | VARCHAR(100) | 步骤名称 |
| STEP_ORDER | INT | 执行顺序，从 1 开始 |
| API_CODE | VARCHAR(50) | 调用的接口编码 |
| INPUT_MAPPING | TEXT | 输入参数映射（JSON 字符串） |
| OUTPUT_MAPPING | TEXT | 输出字段映射（JSON 字符串） |
| CONDITION_EXPR | VARCHAR(200) | 条件表达式（可选） |
| SKIP_ON_ERROR | TINYINT | 前序失败时是否跳过：0=执行，1=跳过 |
| RETRY_COUNT | INT | 失败重试次数（保留字段） |
| ENABLE_CACHE | BOOLEAN | 是否启用缓存（默认 false） |
| CACHE_SECONDS | INT | 缓存时长（秒），配合 enableCache 使用 |
| CACHE_KEYS | VARCHAR(500) | 要缓存的输出字段（逗号分隔），空则缓存全部 |
| CREATED_AT | DATETIME | 创建时间 |

### 2.4 SCENARIO_EXECUTION 执行记录表（log 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键，雪花 ID |
| SCENARIO_ID | BIGINT | 场景 ID |
| SCENARIO_CODE | VARCHAR(50) | 场景编码 |
| SCENARIO_NAME | VARCHAR(100) | 场景名称 |
| STATUS | VARCHAR(20) | RUNNING / SUCCESS / FAILED / PARTIAL |
| START_TIME | DATETIME | 开始时间 |
| END_TIME | DATETIME | 结束时间 |
| COST_TIME_MS | BIGINT | 总耗时（毫秒） |
| TRIGGER_SOURCE | VARCHAR(50) | 触发来源：MANUAL / SCHEDULE / API |
| TRIGGER_USER | VARCHAR(50) | 触发用户 |
| ERROR_MESSAGE | TEXT | 全局错误信息（异常捕获时填充） |
| CONTEXT | TEXT | 执行上下文 JSON（input + steps + metadata） |
| TRACE_ID | VARCHAR(50) | 链路追踪 ID |

### 2.5 SCENARIO_STEP_LOG 步骤日志表（log 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键，雪花 ID |
| EXECUTION_ID | BIGINT | 执行记录 ID |
| STEP_ID | BIGINT | 步骤 ID |
| STEP_CODE | VARCHAR(50) | 步骤编码 |
| STEP_NAME | VARCHAR(100) | 步骤名称 |
| STEP_ORDER | INT | 步骤顺序 |
| STATUS | VARCHAR(20) | SUCCESS / FAILED / SKIPPED / RUNNING |
| REQUEST_PARAMS | TEXT | 请求参数（JSON）；缓存命中时为 `(cache hit)` |
| RESPONSE_DATA | TEXT | 响应数据（JSON，最多 5000 字符） |
| ERROR_MESSAGE | TEXT | 错误信息 |
| START_TIME | DATETIME | 开始时间 |
| END_TIME | DATETIME | 结束时间 |
| COST_TIME_MS | BIGINT | 耗时（毫秒） |

### 2.6 SCENARIO_CACHE 场景缓存表（token 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键，雪花 ID |
| CACHE_KEY | VARCHAR(200) | 缓存 Key，格式 `{scenarioCode}:{stepCode}:{outputKey}`，唯一索引 |
| SCENARIO_ID | BIGINT | 所属场景 ID |
| SCENARIO_CODE | VARCHAR(50) | 场景编码 |
| STEP_CODE | VARCHAR(50) | 步骤编码 |
| OUTPUT_KEY | VARCHAR(100) | 输出字段名 |
| CACHE_VALUE | TEXT | 缓存值（统一 JSON 序列化，字符串值也会带引号存储） |
| EXPIRE_TIME | DATETIME | 过期时间 |
| CREATED_AT | DATETIME | 创建时间 |

---

## 三、参数映射

### 3.1 输入映射（INPUT_MAPPING）

JSON 格式，支持 `params`（请求参数）和 `headers`（请求头）两个区块：

```json
{
  "params": {
    "userId":    { "type": "input",  "value": "userId" },
    "token":     { "type": "step",   "step": "login", "path": "$.token" },
    "productId": { "type": "static", "value": "P001" },
    "accessToken": { "type": "cache", "step": "login", "key": "access_token" },
    "apiKey":    { "type": "token",  "tokenApiCode": "get-api-key" }
  },
  "headers": {
    "Authorization": { "type": "step", "step": "login", "path": "$.token", "prefix": "Bearer " },
    "X-Api-Key":     { "type": "cache", "step": "login", "key": "api_key" }
  }
}
```

**type 五种来源：**

| type | 说明 | 示例 |
|------|------|------|
| `static` | 固定值，原样传递 | `{ "type": "static", "value": "P001" }` |
| `input` | 外部传入参数 | `{ "type": "input", "value": "userId" }` |
| `step` | 前序步骤的输出字段（当前执行内） | `{ "type": "step", "step": "login", "path": "$.token" }` |
| `cache` | 场景缓存表读取（跨执行持久化，过期自动失效） | `{ "type": "cache", "step": "login", "key": "token" }` |
| `token` | Token 缓存表读取（接口配置的动态 Token） | `{ "type": "token", "tokenApiCode": "get-api-key" }` |

**JsonPath 提取语法（path 字段）：**
- `$.data.token` — 提取嵌套字段
- `$.items[0].productId` — 提取数组第一个元素的字段
- 可配合 `prefix` 字段添加前缀（如 `"Bearer "`）

### 3.2 输出映射（OUTPUT_MAPPING）

定义从接口响应中提取哪些字段存入执行上下文，供后续步骤引用：

```json
{
  "outputs": {
    "token":      "$.data.accessToken",
    "tokenType":  "$.data.tokenType",
    "expiresIn":  "$.data.expiresIn"
  }
}
```

- **键名**：存入上下文的变量名（后续步骤通过 `step` 类型引用）
- **值**：JsonPath 表达式，从响应 JSON 中提取

> 如果未配置 outputMapping，则不提取任何字段，该步骤输出为空 Map。

### 3.3 条件表达式（CONDITION_EXPR）

条件表达式在**接口调用完成之后**评估，用于判断当前步骤的结果是否满足预期。

**语法：**

```
{{step:步骤编码.字段路径}} == '期望值'
{{step:步骤编码.字段路径}} != '期望值'
{{input:参数名}} == '期望值'
```

**示例：**

```
{{step:login.token}} != null
{{step:query.code}} == '200'
{{step:check-stock.available}} == true
{{input:needNotify}} == 'true'
```

**设计原则：**
- 条件表达式是"断言"，不是"开关"——它判断结果是否符合预期，而不是决定是否执行接口
- 不满足条件的步骤会被标记为 `FAILED`，影响全局状态和失败策略

---

### 3.4 步骤缓存（enableCache）

步骤级缓存机制，解决 Token 等有时效性的数据在多次场景执行间共享的问题。

**设计原则：** 缓存读取和写入统一由 `enableCache` 控制——开启后自动读写一体，不做单独的读/写分离开关。

#### 缓存配置

| 字段 | 类型 | 说明 |
|------|------|------|
| `enableCache` | Boolean | 是否启用缓存，默认 `false` |
| `cacheSeconds` | Integer | 缓存时长（秒），配合 enableCache 使用 |
| `cacheKeys` | String | 要缓存的输出字段（逗号分隔），空则缓存全部输出字段 |

#### 缓存执行逻辑

```
enableCache = true && cacheSeconds > 0
    │
    ├─ 读取缓存（逐 key 通过 ScenarioCacheService.get() 读取）
    │   ├─ 确定要读取的 key 列表：
    │   │   ├─ 有 cacheKeys → 按指定字段
    │   │   └─ 无 cacheKeys → 从 outputMapping.outputs 提取所有 key
    │   │
    │   ├─ 全部 key 都命中 → 跳过 API 调用，直接使用缓存数据
    │   │   ├─ 缓存数据写入 context.steps（与正常输出格式一致）
    │   │   ├─ 记录步骤日志（requestParams = "(cache hit)"）
    │   │   └─ 返回 SUCCESS
    │   │
    │   └─ 部分或全部未命中 → 正常调用 API
    │
    └─ API 调用成功后写入缓存
        ├─ 有 cacheKeys → 只缓存指定字段
        └─ 无 cacheKeys → 缓存全部输出字段
```

#### 缓存读取与输出格式一致性

缓存读取后恢复的数据结构与 `extractOutput()` 的输出格式**完全一致**：

| 数据来源 | 格式 | 说明 |
|----------|------|------|
| 正常 API 调用 | `extractOutput()` → `{key: value}` | JsonPath 提取后存入 context.steps |
| 缓存命中 | `ScenarioCacheService.get()` → `{key: value}` | 反序列化后存入 context.steps |

后续步骤通过 `{{step:xxx.yyy}}` 引用时，统一从 `context.steps` 取值，**不区分数据来源**，确保格式一致。

#### 缓存值序列化规则

`ScenarioCacheService.put()` 统一使用 `JsonUtil.toJson(value)` 序列化：

| 值类型 | 存储格式 | 反序列化结果 |
|--------|----------|-------------|
| String（如 JWT） | `"eyJ0eXAiOiJKV1Qi..."` （带引号的 JSON 字符串） | String |
| Integer/Long | `123` | Integer |
| Map/Object | `{"name":"张三","age":25}` | LinkedHashMap |
| Boolean | `true` | Boolean |

> **注意**：所有缓存值统一 JSON 序列化，确保 `JsonUtil.fromJson()` 反序列化时类型正确。旧版直接存储裸字符串（如 JWT 不带引号）会导致 `JsonParseException`。

#### 缓存 Key 格式

```
{scenarioCode}:{stepCode}:{outputKey}
```

示例：`oauth_flow:login:access_token`

#### 输入映射中引用缓存（cache 类型）

```json
{
  "params": {
    "access_token": { "type": "cache", "step": "login", "key": "access_token" }
  },
  "headers": {
    "Authorization": { "type": "cache", "step": "login", "key": "access_token", "prefix": "Bearer " }
  }
}
```

> **区别**：`cache` 类型是在**输入映射**中显式引用缓存值（手动指定 key）；`enableCache` 是在**步骤配置**中自动读取/写入缓存（整体命中则跳过 API 调用）。两者可以配合使用，也可以独立使用。

#### 典型应用场景

1. **OAuth Token 缓存**：登录步骤配置 `enableCache=true` + `cacheSeconds=3600` + `cacheKeys=access_token`，缓存命中时跳过登录接口，直接使用缓存的 Token
2. **跨执行复用**：同一天内多次执行同一场景，直接使用上次获取的 Token，避免重复登录
3. **自动续期**：Token 过期后缓存未命中，自动调用接口获取新 Token 并写入缓存

---

## 四、执行流程

### 4.1 整体流程

```
加载场景配置 → 加载步骤列表 → 创建执行记录 → 初始化上下文
                                                          │
                                                    ┌─────┴─────┐
                                                    │ 按顺序执行步骤│
                                                    └─────┬─────┘
                                                          │
                                              更新执行记录状态
                                                          │
                                                  返回执行结果
```

### 4.2 单步执行顺序（executeStep）

```
┌──────────────────────────────────────┐
│ ① skipOnError 检查                    │
│    前序有失败 && skipOnError=1 ?       │
│      YES → SKIPPED，结束（返回）        │
│      NO  → 继续                        │
└────────────────┬─────────────────────┘
                 │ NO
                 ▼
┌──────────────────────────────────────┐
│ ② 构建输入参数（resolveParams/Headers） │
│    static / input / step / cache /     │
│    token 五种来源替换                    │
└────────────────┬─────────────────────┘
                 ▼
┌──────────────────────────────────────┐
│ ③ 缓存读取检查（enableCache）          │
│    enableCache=true && cacheSeconds>0? │
│      NO  → 跳过缓存，直接调用 API       │
│      YES → 读取缓存                    │
│        ├─ 命中 → 跳过 API，记录日志      │
│        │        (requestParams=         │
│        │         "(cache hit)")         │
│        │        返回 SUCCESS             │
│        └─ 未命中 → 继续调用 API          │
└────────────────┬─────────────────────┘
                 │ 缓存未命中
                 ▼
┌──────────────────────────────────────┐
│ ④ 调用接口（HttpInvokeService.invoke） │
│    获取 InvokeResponseDTO              │
│    success=false → FAILED              │
└────────────────┬─────────────────────┘
                 ▼
┌──────────────────────────────────────┐
│ ⑤ 提取输出（extractOutput）            │
│    按 outputMapping 用 JsonPath         │
│    从 response.data 提取字段            │
└────────────────┬─────────────────────┘
                 ▼
┌──────────────────────────────────────┐
│ ⑥ 存入执行上下文                       │
│    context.steps[stepCode] = output    │
└────────────────┬─────────────────────┘
                 ▼
┌──────────────────────────────────────┐
│ ⑦ 写入场景缓存（enableCache 时）        │
│    enableCache=true && cacheSeconds>0  │
│    && output 非空 → 写入缓存            │
└────────────────┬─────────────────────┘
                 ▼
┌──────────────────────────────────────┐
│ ⑧ 记录步骤日志（ScenarioStepLog）      │
└────────────────┬─────────────────────┘
                 ▼
┌──────────────────────────────────────┐
│ ⑨ 评估条件表达式（evaluateCondition）  │
│    条件不满足 → FAILED                 │
│    条件满足   → SUCCESS                │
│    同时标记 _hasFailure=true（失败时）   │
└────────────────┬─────────────────────┘
                 ▼
┌──────────────────────────────────────┐
│ ⑩ 返回 StepResultDTO                  │
│    status / output / errorMessage     │
└──────────────────────────────────────┘
```

> **执行顺序关键点**：
> - 缓存读取在接口调用**之前**，缓存命中时完全跳过 API 调用
> - 条件表达式在接口调用**之后**评估，基于当前步骤的返回结果判断是否满足预期
> - 缓存写入在输出提取**之后**，确保写入的是经过 outputMapping 处理后的数据

### 4.3 失败处理

**步骤失败来源：**
1. `HttpInvokeService` 返回 `success=false`（接口调用失败）
2. 条件表达式评估不满足
3. 执行过程中抛出异常

**失败标记机制：**
- 使用 `context.metadata._hasFailure` 独立存储，不污染输出数据
- `skipOnError=1` 的步骤会检查此标记决定是否跳过

**全局失败策略：**
- `STOP`：遇到失败步骤立即终止剩余步骤
- `CONTINUE`：遇到失败步骤记录后继续执行后续步骤

**执行最终状态：**
- 全部成功 → `SUCCESS`
- 有失败且无成功 → `FAILED`
- 有失败但也有成功 → `PARTIAL`

---

## 五、API 接口

### 5.1 场景管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/scenarios` | scenario:view | 分页查询场景 |
| GET | `/api/scenarios/{id}` | scenario:view | 获取场景详情（含步骤） |
| GET | `/api/scenarios/groups` | scenario:view | 获取所有分组名称 |
| GET | `/api/scenarios/active` | scenario:view | 获取所有启用状态的场景 |
| POST | `/api/scenarios` | scenario:add | 创建场景 |
| PUT | `/api/scenarios/{id}` | scenario:edit | 更新场景 |
| POST | `/api/scenarios/{id}/toggle` | scenario:edit | 启用/停用切换 |
| DELETE | `/api/scenarios/{id}` | scenario:delete | 删除场景 |

### 5.2 步骤管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/scenarios/{scenarioId}/steps` | scenario:view | 获取场景所有步骤 |
| POST | `/api/scenarios/{scenarioId}/steps` | scenario:edit | 添加步骤 |
| PUT | `/api/scenarios/steps/{stepId}` | scenario:edit | 更新步骤 |
| DELETE | `/api/scenarios/steps/{stepId}` | scenario:edit | 删除步骤 |

### 5.3 执行与记录

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/scenarios/execute` | scenario:execute | 执行场景（按 code 或 id） |
| GET | `/api/scenarios/executions` | scenario:view | 分页查询执行记录 |
| GET | `/api/scenarios/executions/{id}` | scenario:view | 获取单条执行记录 |
| GET | `/api/scenarios/executions/{id}/steps` | scenario:view | 获取执行的所有步骤日志 |

### 5.4 执行请求示例

```bash
# 执行场景（按 scenarioCode）
POST /api/scenarios/execute
{
  "scenarioCode": "oauth-flow",
  "params": {
    "clientId": "my-app",
    "clientSecret": "secret-123"
  },
  "triggerSource": "MANUAL"
}

# 执行响应
{
  "success": true,
  "executionId": 723401728482852864,
  "scenarioCode": "oauth-flow",
  "scenarioName": "OAuth 认证流程",
  "status": "SUCCESS",
  "startTime": "2026-04-25T15:00:00",
  "endTime": "2026-04-25T15:00:02",
  "costTimeMs": 2015,
  "context": {
    "input":  { "clientId": "my-app", "clientSecret": "secret-123" },
    "steps": {
      "get-auth-code": { "authCode": "AUTH123" },
      "get-token":     { "token": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer" },
      "get-userinfo":  { "userId": 10001, "userName": "张三" }
    },
    "metadata": {}
  },
  "steps": [
    { "stepCode": "get-auth-code", "stepName": "获取授权码", "status": "SUCCESS", "costTimeMs": 150 },
    { "stepCode": "get-token",     "stepName": "换取 Token",   "status": "SUCCESS", "costTimeMs": 800 },
    { "stepCode": "get-userinfo",  "stepName": "获取用户信息", "status": "SUCCESS", "costTimeMs": 1065 }
  ]
}

# 缓存命中时的步骤日志
{
  "stepCode": "get-token",
  "stepName": "换取 Token",
  "status": "SUCCESS",
  "requestParams": "(cache hit)",
  "responseData": "{\"token\":\"eyJhbGciOiJIUzI1NiJ9...\",\"tokenType\":\"Bearer\"}",
  "costTimeMs": 2
}
```

---

## 六、前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 场景列表 | `pages/scenario_list.html` | 场景卡片概览 + 搜索 + 表格 + 分页 |
| 场景编辑 | `pages/scenario_edit.html` | 基本信息 + 步骤编排（添加/上移/下移/删除） |
| 场景执行 | `pages/scenario_exec.html` | 参数输入 + 执行触发 + 步骤时间线 + 历史记录 |

### 6.1 场景编辑页 — 步骤配置

每个步骤支持配置：

- **基本信息**：步骤编码、名称、执行顺序、调用的 API
- **输入映射**：params / headers 两个区块，每项选择来源类型（static / input / step / cache）
- **输出映射**：outputs 区块，每项填写变量名和 JsonPath 表达式
- **条件表达式**：文本框填写条件（支持 `{{step:xxx}}` 和 `{{input:xxx}}` 引用）
- **选项**：skipOnError 开关、retryCount
- **缓存配置**：enableCache 开关 + cacheSeconds 输入框 + cacheKeys 输入框

### 6.2 缓存配置 UI

- **enableCache 开关**：勾选后展开缓存配置区域，取消勾选则隐藏
- **cacheSeconds**：缓存时长输入框（秒）
- **cacheKeys**：要缓存的字段名输入框（逗号分隔），留空则缓存全部输出字段
- **提示文案**："开启后优先读取缓存，命中则跳过接口调用；未命中则调用并写入缓存"
- **向后兼容**：旧数据 `enableCache` 为 `null` 或 `undefined` 时，自动归一化为 `false`

> **JSON 序列化注意**：`inputMapping` 和 `outputMapping` 存储为 JSON 字符串。前端 textarea 展示时用 `JSON.stringify(obj, null, 2)` 格式化；保存时用 `JSON.parse()` 转对象后提交。后端 `ScenarioController.parseStepDTOs()` 会判断类型——已是对象则直接使用，是字符串则解析。

---

## 七、权限配置

### 7.1 权限码（permission-config.json）

| 权限码 | 名称 | sortOrder |
|--------|------|-----------|
| `scenario:view` | 查看场景列表 | 15 |
| `scenario:add` | 新增场景 | 16 |
| `scenario:edit` | 编辑场景 | 17 |
| `scenario:delete` | 删除场景 | 18 |
| `scenario:execute` | 执行场景 | 19 |

### 7.2 菜单项（menu-config.json）

| 编码 | 名称 | 图标 | 页面路径 | 分组 | sortOrder |
|------|------|------|----------|------|-----------|
| `scenario-list` | 场景列表 | 🔗 | `pages/scenario_list.html` | 集成管理 | 6 |
| `scenario-edit` | 场景编辑 | — | `pages/scenario_edit.html` | （隐藏菜单） | — |
| `scenario-exec` | 场景执行 | — | `pages/scenario_exec.html` | （隐藏菜单） | — |

`scenario-list` 分配给 DEVELOPER 角色，`scenario-edit` 和 `scenario-exec` 对应编辑和执行按钮的权限校验。

---

## 八、核心代码

### 8.1 ScenarioExecutionService 关键方法

```
execute()                    — 场景入口，加载配置→创建记录→执行步骤→返回结果
executeStep()                — 单步执行：skipOnError→构建请求→缓存检查→调用接口→提取输出→写入缓存→评估条件
buildInvokeRequest()         — 构建 InvokeRequestDTO，解析 inputMapping
resolveParams()              — 解析 params 区块（static/input/step/cache/token 五种来源）
resolveHeaders()             — 解析 headers 区块（支持 prefix 前缀）
extractOutput()              — 按 outputMapping 用 JsonPath 提取响应字段
evaluateCondition()          — 解析条件表达式，替换 {{step:xxx.yyy}} / {{input:xxx}} 变量后评估
readStepCache()              — 读取步骤缓存（逐 key 读取，自动过滤过期）
resolveCacheKeys()           — 解析需要缓存的字段列表（cacheKeys 优先，否则从 outputMapping 提取）
cacheStepOutput()            — 将步骤输出写入场景缓存表
buildCacheHitResult()        — 构建缓存命中时的步骤结果
ExecutionContext             — 内部类：input（外部参数）、steps（各步骤输出）、metadata（失败标记）
```

### 8.2 条件表达式评估细节

```
evaluateCondition() 评估步骤：
  1. 用正则 \{\{(step|input):([^}]+)\}\} 匹配变量引用
  2. step 类型：解析 "步骤编码.字段路径"，用 JsonPath 从 context.steps[stepCode] 提取值
  3. input 类型：直接从 context.input[path] 取值
  4. 替换后用 == 或 != 比较（自动去掉字符串两端的引号）
  5. 支持 true/false 布尔比较
```

### 8.3 缓存读取流程

```
readStepCache(scenarioCode, step):
  1. 调用 resolveCacheKeys(step) 确定要读取的 key 列表
     ├─ 有 cacheKeys → 按逗号分隔取指定字段
     └─ 无 cacheKeys → 从 outputMapping.outputs 提取所有 key
  2. 逐 key 调用 scenarioCacheService.get(scenarioCode, stepCode, key)
     ├─ get() 内部使用 findValidByKey() 自动过滤过期缓存
     └─ 反序列化后的数据结构与 extractOutput() 输出一致
  3. 全部 key 都未命中 → 返回空 Map → 正常调用 API
  4. 全部/部分命中 → 返回非空 Map → 缓存命中，跳过 API 调用
```

### 8.4 ScenarioCacheService 关键方法

```
get(scenarioCode, stepCode, outputKey)  — 读取未过期缓存值，JsonUtil.fromJson 反序列化
getString(scenarioCode, stepCode, key)  — 读取未过期缓存原始字符串
put(scenarioId, scenarioCode, stepCode, outputKey, value, cacheSeconds)
    — 写入缓存，统一 JsonUtil.toJson 序列化（字符串也加引号）
putAll(scenarioId, scenarioCode, stepCode, outputMap, cacheSeconds)
    — 批量写入（遍历调用 put）
clearByScenario(scenarioCode)           — 清除场景所有缓存
clearByStep(scenarioCode, stepCode)     — 清除步骤缓存
cleanExpired()                          — 定时清理过期缓存（每小时）
```

---

## 九、常见问题

**Q：缓存命中时，后续步骤引用 `{{step:xxx.yyy}}` 能正常获取到值吗？**
A：能。缓存读取后恢复的数据格式与 `extractOutput()` 输出完全一致，统一写入 `context.steps`，后续步骤不区分数据来源。

**Q：`enableCache` 和输入映射中的 `cache` 类型有什么区别？**
A：`enableCache` 是步骤级配置，开启后自动读取缓存（命中则跳过 API 调用）和写入缓存；输入映射中的 `cache` 类型是在参数构建时显式引用某个缓存值。两者可以配合使用：`enableCache` 控制整个步骤的缓存策略，`cache` 类型用于在其他步骤的输入中引用缓存。

**Q：缓存了 JWT Token 后，`JsonUtil.fromJson()` 报 `JsonParseException` 怎么办？**
A：这是因为旧版缓存值直接存储裸字符串（如 `eyJ0eXAi...`），不是合法 JSON。新版 `put()` 方法统一使用 `JsonUtil.toJson(value)` 序列化，字符串会存为 `"eyJ0eXAi..."`（带引号），`fromJson` 可正确还原。如需修复旧数据，调用 `ScenarioCacheService.clearAll()` 清空后重新生成。

**Q：条件表达式中的 `{{step:xxx.yyy}}` 引用的是什么数据？**
A：引用的是经过 `outputMapping` 提取后的字段，不是原始响应。如果某步骤未配置 outputMapping，则该步骤输出为空 Map，引用会得到 `null`。

**Q：skipOnError 和失败策略有什么区别？**
A：`skipOnError` 是步骤级别的控制——前序失败时是否跳过当前步骤。失败策略是场景级别的控制——遇到失败时是立即终止还是继续执行后续步骤。

**Q：为什么条件表达式要在接口调用之后评估？**
A：条件表达式的设计目的是"断言结果是否满足预期"，而不是"决定是否执行接口"。先调用接口再判断，可以获取真实的返回数据来做断言。如果先判断条件再调用，条件中引用的变量（来自前序步骤输出）永远为 `null`（因为还没调用），导致条件永远不满足。

**Q：outputMapping 配置为空会怎样？**
A：该步骤不会提取任何字段存入上下文，后续步骤无法通过 `step` 类型引用当前步骤的输出。同时，如果 `enableCache=true` 但 `outputMapping` 为空，`resolveCacheKeys()` 会返回空列表，缓存读取会跳过（因为没有可读取的 key）。

**Q：编辑场景时 inputMapping/outputMapping 出现大量 `\"` 转义符怎么办？**
A：这是前后端 JSON 双重序列化问题。前端保存时应 `JSON.parse()` textarea 内容为对象再提交；后端 `ScenarioController.parseStepDTOs()` 对已是对象类型的字段跳过序列化。

**Q：`cacheKeys` 不填会怎样？**
A：不填 `cacheKeys` 时，`resolveCacheKeys()` 会自动从 `outputMapping.outputs` 提取所有 key 作为缓存字段。写入缓存时也会缓存全部输出字段（`putAll`）。这意味着后续只要 `outputMapping` 有定义，缓存就能覆盖所有输出字段。
