# 自动化测试设计文档

> 本文档描述自动化测试功能的设计与实现，涵盖测试用例管理、测试套件组织、批量执行引擎、断言引擎与报告生成。

---

## 一、功能概述

自动化测试（Automated Testing）在场景编排的基础上，引入**断言断言（Assertion）**和**测试报告**能力，支持对 API、场景、Mock 服务进行自动化验证，并生成详细的测试报告。

### 1.1 核心特性

| 特性 | 说明 |
|------|------|
| **断言引擎** | JSONPath 提取 + 7 种比较运算符 + 多断言组合（AND/OR） |
| **步骤类型** | API 调用 / 场景编排执行 / Mock 响应验证 |
| **测试套件** | 将多个测试用例分组，支持顺序/并行执行 |
| **批量执行** | 手动触发 / 定时触发，支持失败即停、并发数控制 |
| **实时报告** | 执行进度、步骤级通过/失败/跳过、响应时间趋势 |
| **超时控制** | 每个测试用例可配置超时时间，超时自动标记失败 |
| **通知推送** | 测试完成后通过邮件/企微/Webhook 推送结果（复用告警通道） |
| **执行历史** | 每次执行生成记录，可回溯对比历史结果 |

### 1.2 与场景编排的关系

```
场景编排（Scenario）          自动化测试（Test）
─────────────────────────    ─────────────────────────
目的：业务流程自动化           目的：验证接口正确性
核心：输入→API调用→输出       核心：调用→断言→报告
结果：返回处理后的数据         结果：通过/失败 + 报告
关注点：业务逻辑是否跑通       关注点：接口是否按预期工作
失败策略：STOP / CONTINUE    断言失败 = 测试失败
```

**复用关系**：
- 测试步骤可引用已创建的**场景编排（Scenario）**作为执行单元
- 测试步骤可引用**接口配置（ApiConfig）**直接发起 HTTP 调用
- 测试步骤可引用 **Mock 配置**验证 Mock 响应是否符合预期
- 断言引擎直接复用 `ScenarioExecutionService` 的上下文提取能力

---

## 二、系统架构

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端页面层                                │
│  test_case_list.html (用例列表)    test_suite_list.html (套件列表) │
│  test_case_edit.html (用例编辑)    test_suite_edit.html (套件编辑) │
│  test_report.html (测试报告)       test_execution.html (执行历史) │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTP
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                   TestCaseController                              │
│  /api/test/case/*        - 用例 CRUD                              │
│  /api/test/suite/*      - 套件 CRUD                              │
│  /api/test/execution/*  - 执行历史 / 报告                         │
│  POST /api/test/run      - 触发执行（单用例 / 套件 / 批量）        │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                    ┌──────────┴──────────┐
                    ▼                      ▼
┌───────────────────────────┐  ┌──────────────────────────────────┐
│     TestCaseService        │  │      TestExecutionEngine          │
│  - CRUD                    │  │  - 单用例执行                     │
│  - 步骤管理                 │  - 套件批量执行（顺序/并行）         │
│  - 套件管理                 │  - 断言引擎（AssertionEngine）       │
│  - 从场景导入               │  - 超时控制                        │
└──────────────┬────────────┘  └──────────────┬───────────────────┘
               │                               │
    ┌──────────┴──────────────┐   ┌────────────┴─────────────────┐
    ▼                          ▼   ▼                               ▼
┌────────────────────┐  ┌──────────────┐  ┌────────────────┐  ┌──────────┐
│ ApiConfigService   │  │ScenarioExe-  │  │MockConfig-     │  │Notify-   │
│ (API 调用)         │  │cutionService │  │Service         │  │Service   │
│                    │  │(场景复用)    │  │(Mock 验证)     │  │(告警通道)│
└────────────────────┘  └──────────────┘  └────────────────┘  └──────────┘
```

### 2.2 执行流程

```
用户触发测试
      ↓
加载测试用例 / 测试套件
      ↓
创建 TestExecution 记录（状态：PENDING）
      ↓
遍历用例 ──→ [单用例执行]
              │
              ├── 步骤 1
              │     ├── 类型 = API → ApiConfigService.invoke()
              │     ├── 类型 = SCENARIO → ScenarioExecutionService.execute()
              │     ├── 类型 = MOCK → MockConfigService.verify()
              │     ↓
              │     执行结果
              │     ↓
              │   [断言引擎] ──→ 提取实际值 ──→ 比较断言 ──→ PASS / FAIL
              │                                   ↓
              ├── 步骤 2 ...
              ├── 超时检查
              └── 失败策略（STOP / CONTINUE）

      ↓
更新 TestExecution（COMPLETED / FAILED）
      ↓
生成测试报告（TestReport）
      ↓
[通知推送] 发送邮件 / 企微 / Webhook
```

---

## 三、数据模型

### 3.1 实体关系

```
config 库：
  TEST_CASE (测试用例)
      └── TEST_CASE_STEP (用例步骤)
  TEST_SUITE (测试套件)
      └── TEST_SUITE_CASE (套件-用例关联)

log 库：
  TEST_EXECUTION (执行记录)
      └── TEST_STEP_RESULT (步骤级结果)
  TEST_SUITE_EXECUTION (套件执行记录)
  TEST_REPORT (测试报告)
```

### 3.2 TEST_CASE 测试用例（config 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键（雪花算法） |
| CODE | VARCHAR(50) | 用例编码（唯一） |
| NAME | VARCHAR(100) | 用例名称 |
| DESCRIPTION | VARCHAR(500) | 描述 |
| GROUP_NAME | VARCHAR(100) | 分组 |
| STATUS | VARCHAR(20) | ACTIVE / INACTIVE |
| PRIORITY | INT | 优先级（1=最高，5=最低），默认 3 |
| TAGS | VARCHAR(500) | 标签，逗号分隔，如 `smoke,regression` |
| TIMEOUT_MS | INT | 超时时间（毫秒），默认 60000 |
| FAILURE_STRATEGY | VARCHAR(20) | 失败策略：STOP / CONTINUE |
| NOTIFY_ON_FAILURE | BOOLEAN | 失败时是否通知，默认 false |
| CREATED_AT | DATETIME | 创建时间 |
| UPDATED_AT | DATETIME | 更新时间 |
| CREATED_BY_ID | BIGINT | 创建人ID |
| CREATED_BY_NAME | VARCHAR(50) | 创建人名称 |

### 3.3 TEST_CASE_STEP 用例步骤（config 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键（雪花算法） |
| TEST_CASE_ID | BIGINT | 所属用例ID |
| STEP_ORDER | INT | 步骤顺序（从 1 开始） |
| STEP_CODE | VARCHAR(50) | 步骤编码 |
| STEP_NAME | VARCHAR(100) | 步骤名称 |
| STEP_TYPE | VARCHAR(20) | **API** / **SCENARIO** / **MOCK** |
| TARGET_CODE | VARCHAR(50) | 目标编码（API code / Scenario code / Mock code） |
| TARGET_ID | BIGINT | 目标ID（冗余存储） |
| REQUEST_OVERRIDE | TEXT | 请求覆盖参数（JSON，合并到目标配置的请求参数上） |
| ASSERTIONS | TEXT | 断言配置（JSON 数组） |
| SKIP_ON_ERROR | INT | 前序失败时跳过（0/1），默认 0 |
| TIMEOUT_MS | INT | 步骤超时，默认继承用例超时 |
| ENABLED | BOOLEAN | 是否启用，默认 true |

### 3.4 断言配置（ASSERTIONS 字段）

```json
[
  {
    "id": "assert-1",
    "field": "$.code",
    "operator": "EQUALS",
    "expected": "0",
    "message": "响应 code 应为 0"
  },
  {
    "id": "assert-2",
    "field": "$.data.id",
    "operator": "EXISTS",
    "message": "data.id 字段必须存在"
  },
  {
    "id": "assert-3",
    "field": "$.data.items.length()",
    "operator": "GREATER_THAN",
    "expected": "0",
    "message": "items 列表不能为空"
  }
]
```

### 3.5 TEST_SUITE 测试套件（config 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键（雪花算法） |
| CODE | VARCHAR(50) | 套件编码（唯一） |
| NAME | VARCHAR(100) | 套件名称 |
| DESCRIPTION | VARCHAR(500) | 描述 |
| GROUP_NAME | VARCHAR(100) | 分组 |
| STATUS | VARCHAR(20) | ACTIVE / INACTIVE |
| EXECUTION_MODE | VARCHAR(20) | **SEQUENTIAL**（顺序）/ **PARALLEL**（并行） |
| CONCURRENCY | INT | 并发数（EXECUTION_MODE=PARALLEL 时生效），默认 3 |
| STOP_ON_FIRST_FAILURE | BOOLEAN | 第一个失败即停止整个套件，默认 false |
| TIMEOUT_MS | INT | 套件级超时时间（毫秒），默认 300000 |
| NOTIFY_ON_COMPLETE | BOOLEAN | 执行完成（无论成功失败）是否通知，默认 true |
| CREATED_AT | DATETIME | 创建时间 |
| UPDATED_AT | DATETIME | 更新时间 |

### 3.6 TEST_SUITE_CASE 套件-用例关联（config 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键 |
| TEST_SUITE_ID | BIGINT | 套件ID |
| TEST_CASE_ID | BIGINT | 用例ID |
| CASE_ORDER | INT | 用例在套件中的执行顺序 |

### 3.7 TEST_EXECUTION 执行记录（log 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键（雪花算法） |
| TEST_CASE_ID | BIGINT | 用例ID |
| TEST_CASE_CODE | VARCHAR(50) | 用例编码（冗余） |
| TEST_SUITE_ID | BIGINT | 套件ID（若属于套件执行） |
| STATUS | VARCHAR(20) | **PENDING / RUNNING / SUCCESS / FAILED / TIMEOUT / SKIPPED** |
| START_TIME | DATETIME | 开始时间 |
| END_TIME | DATETIME | 结束时间 |
| COST_TIME_MS | BIGINT | 总耗时（毫秒） |
| TRIGGER_SOURCE | VARCHAR(20) | 触发来源：MANUAL / SCHEDULED / API |
| TRIGGER_USER | VARCHAR(50) | 触发人 |
| TOTAL_STEPS | INT | 总步骤数 |
| PASSED_STEPS | INT | 通过步骤数 |
| FAILED_STEPS | INT | 失败步骤数 |
| SKIPPED_STEPS | INT | 跳过步骤数 |
| ERROR_MESSAGE | TEXT | 错误信息（如有） |
| TRACE_ID | VARCHAR(64) | 链路追踪ID |
| CREATED_AT | DATETIME | 创建时间 |

### 3.8 TEST_STEP_RESULT 步骤级结果（log 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键 |
| TEST_EXECUTION_ID | BIGINT | 执行记录ID |
| STEP_ORDER | INT | 步骤序号 |
| STEP_CODE | VARCHAR(50) | 步骤编码 |
| STEP_TYPE | VARCHAR(20) | API / SCENARIO / MOCK |
| TARGET_CODE | VARCHAR(50) | 目标编码 |
| STATUS | VARCHAR(20) | **SUCCESS / FAILED / TIMEOUT / SKIPPED** |
| START_TIME | DATETIME | 开始时间 |
| END_TIME | DATETIME | 结束时间 |
| COST_TIME_MS | BIGINT | 耗时（毫秒） |
| REQUEST_PARAMS | TEXT | 请求参数（JSON） |
| RESPONSE_DATA | TEXT | 响应数据（截断 10KB） |
| ASSERTION_RESULTS | TEXT | 断言结果（JSON） |
| ERROR_MESSAGE | TEXT | 错误信息 |

**ASSERTION_RESULTS** 示例：

```json
[
  {
    "id": "assert-1",
    "field": "$.code",
    "operator": "EQUALS",
    "expected": "0",
    "actual": "0",
    "status": "PASS",
    "message": "响应 code 应为 0"
  },
  {
    "id": "assert-2",
    "field": "$.data",
    "operator": "EXISTS",
    "actual": "{...}",
    "status": "PASS",
    "message": "data.id 字段必须存在"
  },
  {
    "id": "assert-3",
    "field": "$.data.length",
    "operator": "EQUALS",
    "expected": "5",
    "actual": "3",
    "status": "FAIL",
    "message": "items 列表应为 5 个，实际 3 个"
  }
]
```

### 3.9 TEST_REPORT 测试报告（log 库）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键 |
| TEST_EXECUTION_ID | BIGINT | 执行记录ID |
| TEST_SUITE_EXECUTION_ID | BIGINT | 套件执行ID（若属于套件） |
| REPORT_TYPE | VARCHAR(20) | **SUITE** / **CASE** |
| REPORT_NAME | VARCHAR(200) | 报告名称，如「用户模块冒烟测试报告」 |
| SUMMARY | TEXT | 摘要 JSON（含统计数据） |
| DETAILS | TEXT | 完整报告 JSON（含所有步骤详细结果） |
| DURATION_MS | BIGINT | 总耗时 |
| GENERATED_AT | DATETIME | 报告生成时间 |

**SUMMARY** 示例：

```json
{
  "totalCases": 10,
  "passedCases": 8,
  "failedCases": 2,
  "skippedCases": 0,
  "passRate": "80%",
  "totalSteps": 35,
  "passedSteps": 30,
  "failedSteps": 3,
  "skippedSteps": 2,
  "avgResponseTime": 245,
  "maxResponseTime": 1203,
  "minResponseTime": 42,
  "tags": ["smoke", "regression"],
  "durationMs": 15800
}
```

---

## 四、断言引擎

### 4.1 支持的断言运算符

| 运算符 | 说明 | 示例 |
|--------|------|------|
| `EQUALS` | 精确相等（字符串比较） | expected: `"0"` vs actual: `"0"` ✅ |
| `NOT_EQUALS` | 不相等 | expected: `"1"` vs actual: `"0"` ✅ |
| `CONTAINS` | 包含子串 | expected: `"success"` vs actual: `"success"` ✅ |
| `NOT_CONTAINS` | 不包含子串 | expected: `"error"` vs actual: `"success"` ✅ |
| `GREATER_THAN` | 大于 | expected: `"5"` vs actual: `"10"` ✅ |
| `LESS_THAN` | 小于 | expected: `"100"` vs actual: `"50"` ✅ |
| `GREATER_OR_EQUAL` | 大于等于 | - |
| `LESS_OR_EQUAL` | 小于等于 | - |
| `REGEX` | 正则匹配 | expected: `"^1[3-9]\\d{9}$"` vs actual: `"13812345678"` ✅ |
| `EXISTS` | 字段存在（忽略 value） | field: `$.data.id` → actual 非空 ✅ |
| `NOT_EXISTS` | 字段不存在 | field: `$.error` → actual 为 null 或字段不存在 ✅ |
| `IS_NULL` | 值为 null | - |
| `NOT_NULL` | 值不为 null | - |
| `IN` | 在列表中 | expected: `"A,B,C"` vs actual: `"B"` ✅ |
| `JSON_MATCH` | JSON Schema 验证 | expected: schema JSON vs actual: JSON ✅ |

### 4.2 断言执行流程

```
1. 根据 field（JSON Path 表达式）从实际响应中提取值
2. 若提取失败（路径不存在）：
   - EXISTS / NOT_NULL → PASS
   - IS_NULL → PASS
   - 其他 → FAIL（字段不存在）
3. 若提取成功，将 actual 和 expected 转为字符串进行比较
4. 根据 operator 执行对应比较逻辑
5. 返回 {status: PASS/FAIL, actual, message}
```

### 4.3 断言错误消息模板

断言失败时，生成友好错误消息：

```
❌ 断言失败：响应 code 应为 0
   字段：$.code
   期望：0
   实际：-1
   运算符：EQUALS
```

---

## 五、执行引擎

### 5.1 三种执行模式

#### 单用例执行
```
POST /api/test/run?caseId=123
Body: {"params": {"userId": 1}}
```
直接执行指定用例，返回完整执行结果（包含步骤结果 + 断言结果）。

#### 套件执行
```
POST /api/test/run/suite/{suiteId}
Body: {"triggerSource": "MANUAL"}
```
按顺序或并行执行套件内所有用例，生成套件级报告。

#### 批量执行
```
POST /api/test/run/batch
Body: {
  "caseIds": [1, 2, 3],
  "suiteIds": [10],
  "tags": ["smoke"],
  "stopOnFirstFailure": true
}
```
跨套件批量执行，支持按标签筛选用例。

### 5.2 并行执行策略

当 `EXECUTION_MODE = PARALLEL` 时：

```
CONCURRENCY = 3

用例池：[TC1, TC2, TC3, TC4, TC5, TC6]

第一轮：TC1 ─┐
            ├─ TC2 ─┐
            └─ TC3 ─┤
                   ├─ TC4 ─┐
                   └─ TC5 ─┤
                          └─ TC6（等待）
```

使用 Java `ExecutorService` + `CompletableFuture` 实现，控制并发数。

### 5.3 超时控制

| 层级 | 字段 | 默认值 | 说明 |
|------|------|--------|------|
| 测试套件 | `TIMEOUT_MS` | 300000（5分钟） | 整个套件执行超时 |
| 测试用例 | `TIMEOUT_MS` | 60000（1分钟） | 单个用例执行超时 |
| 用例步骤 | `TIMEOUT_MS` | 继承用例 | 单个步骤 HTTP 调用超时 |

超时时：
- 记录 `TEST_STEP_RESULT.STATUS = TIMEOUT`
- 记录 `ERROR_MESSAGE = "Execution timeout: xxx ms"`
- 按 `FAILURE_STRATEGY` 决定是否继续

### 5.4 从场景导入

支持将已创建的**场景编排（Scenario）**一键导入为测试用例：

```
POST /api/test/case/import-from-scenario/{scenarioId}
```

导入逻辑：
- Scenario → TestCase（复制基本信息）
- ScenarioStep → TestCaseStep（STEP_TYPE = SCENARIO）
- inputMapping / outputKey 保留，新增断言字段（用户需手动配置断言）

---

## 六、通知机制

### 6.1 通知触发条件

| 配置 | 触发时机 |
|------|---------|
| `NOTIFY_ON_FAILURE = true` | 用例执行失败时通知 |
| `NOTIFY_ON_COMPLETE = true` | 套件执行完成（无论成功失败）通知 |

### 6.2 通知内容模板

**邮件通知**：
```
主题：[测试失败] {套件名称} - {执行时间}

内容：
- 执行概况：通过 8/10，失败 2，跳过 0
- 失败用例：
  1. TC001-用户登录测试 ❌（响应状态码 500）
  2. TC005-订单创建测试 ❌（断言失败：$.code 期望 0 实际 -1）
- 报告链接：http://xxx/test/report/{reportId}
```

**企微 Webhook**：
```json
{
  "msgtype": "markdown",
  "markdown": {
    "content": "**🔴 测试套件执行失败**\n> 套件：用户模块冒烟测试\n> 通过率：80% (8/10)\n> 失败用例：TC001、TC005\n> [查看报告](http://xxx/test/report/xxx)"
  }
}
```

### 6.3 与告警通道复用

通知能力直接复用已有的告警通知体系（`NotificationService` / `AlertChannel`），包括：
- 邮件通道（`EmailAlertChannel`）
- 企微 Webhook（`WeComAlertChannel`）
- HTTP Webhook（`HttpAlertChannel`）

---

## 七、API 接口

### 7.1 测试用例管理（/api/test/case）

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/test/case` | 创建测试用例 |
| PUT | `/api/test/case/{id}` | 更新测试用例 |
| DELETE | `/api/test/case/{id}` | 删除测试用例 |
| GET | `/api/test/case/{id}` | 获取用例详情（含步骤） |
| GET | `/api/test/case/list` | 分页查询用例列表 |
| GET | `/api/test/case/code/{code}` | 根据编码查询 |
| POST | `/api/test/case/{id}/toggle` | 启用/禁用切换 |
| POST | `/api/test/case/{id}/steps` | 保存步骤列表 |
| GET | `/api/test/case/{id}/steps` | 获取步骤列表 |
| POST | `/api/test/case/import/{scenarioId}` | 从场景导入用例 |

### 7.2 测试套件管理（/api/test/suite）

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/test/suite` | 创建测试套件 |
| PUT | `/api/test/suite/{id}` | 更新测试套件 |
| DELETE | `/api/test/suite/{id}` | 删除测试套件 |
| GET | `/api/test/suite/{id}` | 获取套件详情 |
| GET | `/api/test/suite/list` | 分页查询套件列表 |
| POST | `/api/test/suite/{id}/toggle` | 启用/禁用切换 |
| POST | `/api/test/suite/{id}/cases` | 批量设置套件用例 |
| GET | `/api/test/suite/{id}/cases` | 获取套件内的用例列表 |

### 7.3 执行与报告（/api/test/execution）

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/test/run` | 执行单个用例 |
| POST | `/api/test/run/suite/{suiteId}` | 执行测试套件 |
| POST | `/api/test/run/batch` | 批量执行 |
| GET | `/api/test/execution/{id}` | 获取执行记录详情 |
| GET | `/api/test/execution/list` | 分页查询执行记录 |
| GET | `/api/test/execution/{id}/steps` | 获取步骤级执行结果 |
| GET | `/api/test/report/{id}` | 获取测试报告 |
| POST | `/api/test/execution/{id}/retry` | 重试失败的用例 |

### 7.4 执行请求示例

**单用例执行**：
```bash
curl -X POST http://localhost:8080/api/test/run \
  -H "Content-Type: application/json" \
  -d '{"caseId": 1, "params": {"userId": 100}}'
```

**套件执行**：
```bash
curl -X POST http://localhost:8080/api/test/run/suite/1 \
  -H "Content-Type: application/json" \
  -d '{"triggerSource": "MANUAL"}'
```

**批量执行（按标签）**：
```bash
curl -X POST http://localhost:8080/api/test/run/batch \
  -H "Content-Type: application/json" \
  -d '{"tags": ["smoke"], "stopOnFirstFailure": false}'
```

---

## 八、前端页面

### 8.1 页面清单

| 页面 | 文件 | 说明 |
|------|------|------|
| 用例列表 | `test_case_list.html` | 分页展示测试用例，支持按分组/标签/状态筛选 |
| 用例编辑 | `test_case_edit.html` | 新增/编辑用例，配置步骤和断言 |
| 套件列表 | `test_suite_list.html` | 分页展示测试套件 |
| 套件编辑 | `test_suite_edit.html` | 新增/编辑套件，关联用例列表（拖拽排序） |
| 执行历史 | `test_execution_list.html` | 展示执行记录，支持按套件/状态/时间筛选 |
| 执行详情 | `test_execution_detail.html` | 查看单次执行的步骤级结果和断言详情 |
| 测试报告 | `test_report.html` | 图形化报告页面（通过率图表、失败用例列表、响应时间趋势） |

### 8.2 用例编辑页核心交互

**步骤配置区**：
- 步骤类型下拉：API 调用 / 场景编排 / Mock 验证
- 目标选择：根据类型弹出 ApiConfig / Scenario / Mock 配置列表
- 请求覆盖：JSON 编辑器（合并到目标配置的请求参数上）
- 断言配置：
  - 点击「+ 添加断言」新增一行
  - 字段：JSON Path 表达式（输入框 + 语法提示）
  - 运算符：下拉选择（EQUALS / REGEX / EXISTS 等）
  - 期望值：根据运算符显示/隐藏
  - 失败消息：可选，自定义断言失败提示

**断言配置 UI 示例**：
```
┌──────────────────────────────────────────────────────────┐
│ ✓ 响应码等于 200                                          │
│   字段：$.status  运算符：[等于 ▾]  期望：[200]           │
├──────────────────────────────────────────────────────────┤
│ ✓ code 字段验证                                           │
│   字段：[$.code           ▾]  运算符：[等于 ▾]  期望：[0] │
├──────────────────────────────────────────────────────────┤
│ ✓ data.id 必须存在                                        │
│   字段：[$.data.id         ▾]  运算符：[存在 ▾]  期望：[ ]│
└──────────────────────────────────────────────────────────┘
                        [+ 添加断言]
```

### 8.3 测试报告页面

**顶部摘要卡片**：
- 通过率（大字百分比 + 环形图）
- 总用例数 / 通过 / 失败 / 跳过
- 总耗时 / 平均响应时间

**中部表格**：
- 执行时间 | 套件/用例名 | 状态 | 耗时 | 操作
- 点击行展开查看步骤级结果

**底部趋势图**（可选用）：
- 通过率时间趋势（折线图）
- 响应时间分布（柱状图）

---

## 九、数据库建表 SQL

```sql
-- config 库
CREATE TABLE TEST_CASE (
    ID BIGINT PRIMARY KEY,
    CODE VARCHAR(50) NOT NULL UNIQUE,
    NAME VARCHAR(100) NOT NULL,
    DESCRIPTION VARCHAR(500),
    GROUP_NAME VARCHAR(100),
    STATUS VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    PRIORITY INT DEFAULT 3,
    TAGS VARCHAR(500),
    TIMEOUT_MS INT DEFAULT 60000,
    FAILURE_STRATEGY VARCHAR(20) DEFAULT 'STOP',
    NOTIFY_ON_FAILURE BOOLEAN DEFAULT FALSE,
    CREATED_AT DATETIME NOT NULL,
    UPDATED_AT DATETIME,
    CREATED_BY_ID BIGINT,
    CREATED_BY_NAME VARCHAR(50)
);

CREATE TABLE TEST_CASE_STEP (
    ID BIGINT PRIMARY KEY,
    TEST_CASE_ID BIGINT NOT NULL,
    STEP_ORDER INT NOT NULL,
    STEP_CODE VARCHAR(50) NOT NULL,
    STEP_NAME VARCHAR(100),
    STEP_TYPE VARCHAR(20) NOT NULL,
    TARGET_CODE VARCHAR(50),
    TARGET_ID BIGINT,
    REQUEST_OVERRIDE TEXT,
    ASSERTIONS TEXT,
    SKIP_ON_ERROR INT DEFAULT 0,
    TIMEOUT_MS INT,
    ENABLED BOOLEAN DEFAULT TRUE
);

CREATE TABLE TEST_SUITE (
    ID BIGINT PRIMARY KEY,
    CODE VARCHAR(50) NOT NULL UNIQUE,
    NAME VARCHAR(100) NOT NULL,
    DESCRIPTION VARCHAR(500),
    GROUP_NAME VARCHAR(100),
    STATUS VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    EXECUTION_MODE VARCHAR(20) DEFAULT 'SEQUENTIAL',
    CONCURRENCY INT DEFAULT 3,
    STOP_ON_FIRST_FAILURE BOOLEAN DEFAULT FALSE,
    TIMEOUT_MS INT DEFAULT 300000,
    NOTIFY_ON_COMPLETE BOOLEAN DEFAULT TRUE,
    CREATED_AT DATETIME NOT NULL,
    UPDATED_AT DATETIME
);

CREATE TABLE TEST_SUITE_CASE (
    ID BIGINT PRIMARY KEY,
    TEST_SUITE_ID BIGINT NOT NULL,
    TEST_CASE_ID BIGINT NOT NULL,
    CASE_ORDER INT NOT NULL
);

-- log 库
CREATE TABLE TEST_EXECUTION (
    ID BIGINT PRIMARY KEY,
    TEST_CASE_ID BIGINT,
    TEST_CASE_CODE VARCHAR(50),
    TEST_SUITE_ID BIGINT,
    STATUS VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    START_TIME DATETIME,
    END_TIME DATETIME,
    COST_TIME_MS BIGINT,
    TRIGGER_SOURCE VARCHAR(20),
    TRIGGER_USER VARCHAR(50),
    TOTAL_STEPS INT DEFAULT 0,
    PASSED_STEPS INT DEFAULT 0,
    FAILED_STEPS INT DEFAULT 0,
    SKIPPED_STEPS INT DEFAULT 0,
    ERROR_MESSAGE TEXT,
    TRACE_ID VARCHAR(64),
    CREATED_AT DATETIME NOT NULL
);

CREATE TABLE TEST_STEP_RESULT (
    ID BIGINT PRIMARY KEY,
    TEST_EXECUTION_ID BIGINT NOT NULL,
    STEP_ORDER INT,
    STEP_CODE VARCHAR(50),
    STEP_TYPE VARCHAR(20),
    TARGET_CODE VARCHAR(50),
    STATUS VARCHAR(20),
    START_TIME DATETIME,
    END_TIME DATETIME,
    COST_TIME_MS BIGINT,
    REQUEST_PARAMS TEXT,
    RESPONSE_DATA TEXT,
    ASSERTION_RESULTS TEXT,
    ERROR_MESSAGE TEXT
);

CREATE TABLE TEST_REPORT (
    ID BIGINT PRIMARY KEY,
    TEST_EXECUTION_ID BIGINT,
    TEST_SUITE_EXECUTION_ID BIGINT,
    REPORT_TYPE VARCHAR(20),
    REPORT_NAME VARCHAR(200),
    SUMMARY TEXT,
    DETAILS TEXT,
    DURATION_MS BIGINT,
    GENERATED_AT DATETIME NOT NULL
);

-- 索引
CREATE INDEX IDX_TEST_CASE_CODE ON TEST_CASE(CODE);
CREATE INDEX IDX_TEST_CASE_STATUS ON TEST_CASE(STATUS);
CREATE INDEX IDX_TEST_CASE_GROUP ON TEST_CASE(GROUP_NAME);
CREATE INDEX IDX_TEST_CASE_STEP_CASE ON TEST_CASE_STEP(TEST_CASE_ID);
CREATE INDEX IDX_TEST_SUITE_CODE ON TEST_SUITE(CODE);
CREATE INDEX IDX_TEST_EXECUTION_CASE ON TEST_EXECUTION(TEST_CASE_ID);
CREATE INDEX IDX_TEST_EXECUTION_SUITE ON TEST_EXECUTION(TEST_SUITE_ID);
CREATE INDEX IDX_TEST_STEP_EXECUTION ON TEST_STEP_RESULT(TEST_EXECUTION_ID);
```

---

## 十、权限配置

### 10.1 权限定义（permission-config.json）

```json
[
  {
    "code": "test:view",
    "name": "查看测试用例",
    "sortOrder": 80
  },
  {
    "code": "test:case:add",
    "name": "新增测试用例",
    "sortOrder": 81
  },
  {
    "code": "test:case:edit",
    "name": "编辑测试用例",
    "sortOrder": 82
  },
  {
    "code": "test:case:delete",
    "name": "删除测试用例",
    "sortOrder": 83
  },
  {
    "code": "test:suite:add",
    "name": "新增测试套件",
    "sortOrder": 84
  },
  {
    "code": "test:suite:edit",
    "name": "编辑测试套件",
    "sortOrder": 85
  },
  {
    "code": "test:suite:delete",
    "name": "删除测试套件",
    "sortOrder": 86
  },
  {
    "code": "test:execute",
    "name": "执行测试",
    "sortOrder": 87
  }
]
```

### 10.2 菜单配置（menu-config.json）

```json
{
  "code": "test",
  "name": "自动化测试",
  "icon": "🧪",
  "path": "test",
  "pageFile": "pages/test_case_list.html",
  "section": "集成管理",
  "sortOrder": 8,
  "pageType": "LIST"
},
{
  "code": "testCaseEdit",
  "name": "用例编辑",
  "icon": "📝",
  "path": "testCaseEdit",
  "pageFile": "pages/test_case_edit.html",
  "pageType": "FORM"
},
{
  "code": "testSuite",
  "name": "测试套件",
  "icon": "📦",
  "path": "testSuite",
  "pageFile": "pages/test_suite_list.html",
  "pageType": "LIST"
},
{
  "code": "testSuiteEdit",
  "name": "套件编辑",
  "icon": "📝",
  "path": "testSuiteEdit",
  "pageFile": "pages/test_suite_edit.html",
  "pageType": "FORM"
},
{
  "code": "testExecution",
  "name": "执行历史",
  "icon": "📋",
  "path": "testExecution",
  "pageFile": "pages/test_execution_list.html",
  "pageType": "LIST"
}
```

---

## 十一、实现计划

### Phase 1：核心框架（第 1 周）
- [ ] TestCase / TestCaseStep / TestSuite 实体 + Repository
- [ ] TestCaseService CRUD（含步骤管理）
- [ ] TestSuiteService CRUD
- [ ] TestCaseController / TestSuiteController（含权限注解、审计日志）

### Phase 2：断言引擎（第 2 周）
- [ ] AssertionEngine 实现（13 种运算符）
- [ ] 断言 UI 组件（test_case_edit.html）
- [ ] 断言结果序列化与展示

### Phase 3：执行引擎（第 3 周）
- [ ] TestExecutionEngine 单用例执行
- [ ] 套件顺序执行 / 并行执行
- [ ] 超时控制
- [ ] 从场景导入用例

### Phase 4：报告与通知（第 4 周）
- [ ] TestReport 生成逻辑
- [ ] test_report.html 报告页面
- [ ] 通知集成（复用 AlertChannel）
- [ ] 执行历史页面

---

## 十二、相关文档

- [接口配置设计文档](api-config-design.md)
- [场景编排设计文档](scenario-design.md)
- [Mock 服务设计文档](mock-design.md)
- [权限设计文档](rbac-design.md)
