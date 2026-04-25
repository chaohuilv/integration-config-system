# Mock 服务设计文档

> 本文档描述 Mock 服务的架构设计、核心组件与实现细节。

---

## 一、功能概述

Mock 服务提供本地 API 响应模拟能力，支持前端开发、集成测试、演示演示等场景，无需依赖真实后端服务。

### 1.1 核心特性

| 特性 | 说明 |
|------|------|
| 路径匹配 | 支持精确匹配、Ant 风格通配符、路径变量（如 `/api/user/{id}`） |
| 方法匹配 | GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS |
| 请求匹配 | 支持查询参数、请求头、请求体（JSON Path）多维度匹配 |
| 响应模板 | 20+ 内置动态变量，支持请求上下文引用 |
| 模拟延迟 | 可配置响应延迟，模拟网络延迟场景 |
| 响应头配置 | 支持自定义响应头，如 Content-Type、Authorization |
| 分组管理 | 按业务分组管理 Mock 配置 |
| 优先级控制 | 同一路径多个配置时，按优先级匹配 |
| 命中统计 | 记录命中次数与最后命中时间 |

### 1.2 典型应用场景

1. **前端并行开发**：前端团队无需等待后端接口实现，直接基于 Mock 数据并行开发
2. **集成测试**：编写自动化测试时，使用 Mock 服务返回固定响应，确保测试稳定性
3. **演示/原型**：产品演示时，无需依赖真实服务，使用 Mock 返回理想数据
4. **边缘场景测试**：模拟异常响应（如超时、错误码、特定错误信息）测试前端边界处理

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      前端页面层                               │
│  mock_list.html (列表页)  ←→  mock_edit.html (编辑页)         │
└───────────────────────────────┬─────────────────────────────┘
                                │ HTTP (管理端 API)
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                   MockConfigController                       │
│  /api/mock          - CRUD                                   │
│  /api/mock/list     - 分页查询                                 │
│  /api/mock/groups   - 分组列表                                 │
│  /api/mock/{id}/toggle - 启用/禁用                            │
│  /api/mock/{id}/reset  - 重置命中统计                         │
│  /api/mock/stats    - 统计信息                                 │
└───────────────────────────────┬─────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                    MockConfigService                          │
│  - CRUD 操作                                                  │
│  - 匹配逻辑 (路径/方法/参数/请求头/请求体)                      │
│  - 模板渲染调度                                                │
│  - 命中统计更新                                                │
└───────────────────────────────┬─────────────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
┌───────────────────────────┐   ┌───────────────────────────┐
│   MockTemplateEngine      │   │    MockConfigRepository    │
│  - 内置变量渲染            │   │  - CRUD                    │
│  - 请求上下文变量渲染       │   │  - 分页查询                 │
│  - JSON Path 提取         │   │  - 分组查询                 │
└───────────────────────────┘   └───────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                 独立端口 Mock 服务                             │
│  Undertow Servlet (端口 9999)                                │
│  POST/GET/PUT/DELETE... /mock/*                              │
│                                                              │
│  MockEndpointServlet                                         │
│  1. 解析请求 (路径/参数/请求头/请求体)                         │
│  2. 调用 MockConfigService.findMatch()                       │
│  3. 渲染响应模板                                              │
│  4. 返回 Mock 响应                                            │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 端口隔离

| 端口 | 用途 | 说明 |
|------|------|------|
| 8080 | 主应用 | 管理 API、Web 页面 |
| 9999 | Mock 服务 | 独立端口，避免跨域问题 |

**设计原因**：
- 主应用需要认证鉴权，Mock 服务需要无认证访问
- 前端可直接请求 `http://localhost:9999/mock/...` 避免跨域
- Mock 服务逻辑完全独立，不干扰主应用路由

### 2.3 数据流

#### 管理端流程

```
用户 → mock_edit.html → POST /api/mock → MockConfigController → MockConfigService → DB
```

#### Mock 服务请求流程

```
前端请求 → MockEndpointServlet (9999)
                     ↓
           1. 解析请求路径、方法、参数、请求头、请求体
                     ↓
           2. 调用 MockConfigService.findMatch()
                     ↓
           3. 按优先级遍历启用的配置，检查匹配规则
                     ↓
           匹配成功？
         ┌─────┴─────┐
         ↓           ↓
        是          否
         ↓           ↓
   渲染响应模板   返回 404
         ↓
   返回 Mock 响应
```

---

## 三、数据模型

### 3.1 实体定义

**表名**：`MOCK_CONFIG`

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键（雪花算法） |
| CODE | VARCHAR(50) | Mock 编码（业务唯一标识） |
| NAME | VARCHAR(100) | Mock 名称 |
| PATH | VARCHAR(255) | API 路径，支持 Ant 风格 |
| METHOD | VARCHAR(10) | HTTP 方法 |
| STATUS_CODE | INT | 响应状态码，默认 200 |
| RESPONSE_BODY | TEXT | 响应体模板 |
| RESPONSE_HEADERS | TEXT | 响应头配置（JSON） |
| DELAY_MS | INT | 模拟延迟（毫秒），默认 0 |
| MATCH_RULES | TEXT | 匹配规则配置（JSON） |
| ENABLED | BOOLEAN | 是否启用 |
| GROUP_NAME | VARCHAR(50) | 分组名称 |
| PRIORITY | INT | 优先级（数值越小优先级越高），默认 100 |
| HIT_COUNT | INT | 命中次数统计 |
| LAST_HIT_TIME | DATETIME | 最后命中时间 |
| DESCRIPTION | VARCHAR(500) | 描述 |
| CREATED_AT | DATETIME | 创建时间 |
| UPDATED_AT | DATETIME | 更新时间 |
| CREATED_BY | VARCHAR(50) | 创建人 |
| UPDATED_BY | VARCHAR(50) | 更新人 |

### 3.2 索引设计

```sql
CREATE UNIQUE INDEX idx_mock_code ON MOCK_CONFIG(CODE);
CREATE INDEX idx_mock_path_method ON MOCK_CONFIG(PATH, METHOD);
CREATE INDEX idx_mock_group ON MOCK_CONFIG(GROUP_NAME);
CREATE INDEX idx_mock_enabled ON MOCK_CONFIG(ENABLED);
```

### 3.3 匹配规则数据结构

`MATCH_RULES` 字段存储 JSON 数组，每条规则结构：

```json
[
  {
    "type": "HEADER",
    "field": "X-Request-Id",
    "operator": "EXISTS"
  },
  {
    "type": "QUERY",
    "field": "debug",
    "operator": "EQUALS",
    "value": "true"
  },
  {
    "type": "BODY",
    "field": "$.user.id",
    "operator": "JSON_PATH",
    "value": "123"
  }
]
```

**匹配类型 (type)**：

| 值 | 说明 | 适用运算符 |
|------|------|------|
| BODY | 请求体匹配（JSON） | EQUALS / CONTAINS / REGEX / EXISTS / **JSON_PATH** |
| HEADER | HTTP 请求头匹配 | EQUALS / CONTAINS / REGEX / EXISTS |
| QUERY | URL 查询参数匹配 | EQUALS / CONTAINS / REGEX / EXISTS |

> ⚠️ **JSON_PATH 运算符仅适用于「请求体」(BODY)**，请求头和查询参数匹配不支持 JSON_PATH，请使用 EQUALS/CONTAINS/REGEX/EXISTS。

**匹配操作 (operator)**：

| 值 | 说明 | 示例 |
|------|------|------|
| EQUALS | 精确相等 | value="_exact" |
| CONTAINS | 包含子串 | value="_part" |
| REGEX | 正则匹配 | value="^https://github.com/chaohbridgement/integration-config-system/blob/main/.*" |
| EXISTS | 存在即匹配（忽略 value） | - |
| JSON_PATH | JSON Path 匹配 | field="$.user.id", value="123" |

**取反规则 (negate)**：

```json
{
  "type": "HEADER",
  "field": "Authorization",
  "operator": "EXISTS",
  "negate": true
}
```

表示：请求头中**不包含** Authorization 时匹配。

---

### 3.4 匹配规则填写说明

以下表格详细说明每种「匹配来源 × 运算符」组合的 **field** 和 **value** 正确填法。

#### 请求头（HEADER）匹配

请求头指 HTTP 请求的 Header 字段，如 `Authorization`、`Content-Type`、`X-Request-Id`。

| 运算符 | field 填写 | value 填写 | 示例 |
|--------|-----------|-----------|------|
| **等于** | Header 名称，如 `Authorization` | 期望的完整值，如 `Bearer eyJhbGciOi...` | 请求头 `Authorization: Bearer xxx` 时匹配 |
| **包含** | Header 名称，如 `Content-Type` | 值需包含的字符串，如 `json` | 请求头值包含 `json` 时匹配 |
| **正则** | Header 名称，如 `User-Agent` | 正则表达式，如 `Mozilla/5.*Windows` | Header 值符合正则时匹配 |
| **存在** | Header 名称，如 `X-Debug-Token` | （留空） | 请求包含该 Header 即匹配 |

**示例**：匹配带 `Authorization` 请求头的请求

```json
{
  "type": "HEADER",
  "field": "Authorization",
  "operator": "EXISTS"
}
```

#### 查询参数（QUERY）匹配

查询参数指 URL `?` 后面的参数，如 `/api/user?debug=true&page=1`。

| 运算符 | field 填写 | value 填写 | 示例 |
|--------|-----------|-----------|------|
| **等于** | 参数名，如 `status` | 期望值，如 `active` | `?status=active` 时匹配 |
| **包含** | 参数名，如 `keyword` | 值需包含的字符串，如 `user` | `?keyword=user123` 时匹配 |
| **正则** | 参数名，如 `phone` | 正则表达式，如 `^1[3-9]\d{9}$` | 手机号格式参数匹配 |
| **存在** | 参数名，如 `page` | （留空） | URL 带 `page` 参数即匹配 |

**示例**：匹配 `debug=true` 的查询请求

```json
{
  "type": "QUERY",
  "field": "debug",
  "operator": "EQUALS",
  "value": "true"
}
```

#### 请求体（BODY）匹配

请求体指 POST/PUT/PATCH 请求的 JSON Body，支持两种匹配方式：直接字段名和 JSON Path。

| 运算符 | field 填写 | value 填写 | 示例 |
|--------|-----------|-----------|------|
| **等于** | JSON 字段名，如 `code` 或嵌套 `user.id` | 期望值，如 `11` | Body `{"code":"11"}` 时 `code` = `11` 匹配 |
| **包含** | JSON 字段名，如 `message` | 值需包含的字符串，如 `error` | Body 中 `message` 包含 `error` 时匹配 |
| **正则** | JSON 字段名，如 `email` | 正则表达式，如 `^[\w.-]+@[\w.-]+$` | 字段值符合正则时匹配 |
| **存在** | JSON 字段名，如 `data` | （留空） | Body 含 `data` 字段即匹配 |
| **JSON Path** | **JSON Path 表达式**，如 `$.code` | 期望值，如 `11` | 从 Body 中提取 `$.code` 值与期望值比对 |

**JSON Path 运算符特别说明**：
- field 使用 [JSON Path](https://goessner.net/articles/JsonPath/) 表达式语法，从请求体中提取字段
- `$.field` — 根对象的 field 属性
- `$.user.id` — 嵌套对象的 field 属性
- `$.items[0].name` — 数组第一个元素的 name 属性
- `$.items[*].id` — 数组所有元素的 id

**示例 1**：精确匹配请求体中 `code` 字段值为 `11`

```json
{
  "type": "BODY",
  "field": "code",
  "operator": "EQUALS",
  "value": "11"
}
```
请求体 `{"code":"11","name":"xxx"}` → 匹配 ✅（`code` = `"11"`）

**示例 2**：使用 JSON Path 匹配嵌套字段

```json
{
  "type": "BODY",
  "field": "$.user.id",
  "operator": "JSON_PATH",
  "value": "100"
}
```
请求体 `{"user":{"id":100,"name":"李明"}}` → 匹配 ✅

**示例 3**：组合多条规则（AND 关系）

```json
[
  {
    "type": "QUERY",
    "field": "action",
    "operator": "EQUALS",
    "value": "login"
  },
  {
    "type": "BODY",
    "field": "$.username",
    "operator": "EXISTS"
  }
]
```
所有规则必须同时满足，请求才匹配该 Mock 配置。

---

## 四、模板引擎

### 4.1 内置动态变量

MockTemplateEngine 支持 20+ 内置动态变量，在响应模板中使用 `{{variable}}` 语法。

#### 随机数据类

| 变量 | 说明 | 示例输出 |
|------|------|------|
| `{{randomInt}}` | 随机整数（0-9999） | `4287` |
| `{{randomInt:min:max}}` | 指定范围随机整数 | `{{randomInt:1:100}}` → `42` |
| `{{randomFloat}}` | 随机浮点数 | `3.14` |
| `{{randomFloat:min:max:decimals}}` | 指定范围随机浮点数 | `{{randomFloat:0:1:4}}` → `0.7281` |
| `{{randomString}}` | 随机字符串（8位） | `aBc9xYz1` |
| `{{randomString:length}}` | 指定长度随机字符串 | `{{randomString:16}}` → `aBc9xYz1mN3pQr5s` |
| `{{randomEmail}}` | 随机邮箱 | `user47382@qq.com` |
| `{{randomPhone}}` | 随机手机号 | `13847382910` |
| `{{randomName}}` | 随机中文姓名 | `张伟` |
| `{{randomCity}}` | 随机城市 | `北京` |

#### 时间日期类

| 变量 | 说明 | 示例输出 |
|------|------|------|
| `{{timestamp}}` | Unix 时间戳（秒） | `1714453800` |
| `{{timestampMs}}` | Unix 时间戳（毫秒） | `1714453800123` |
| `{{date}}` | 日期（yyyy-MM-dd） | `2026-04-25` |
| `{{datetime}}` | 日期时间（yyyy-MM-dd HH:mm:ss） | `2026-04-25 20:30:00` |
| `{{datetime:format}}` | 自定义格式 | `{{datetime:yyyyMMdd}}` → `20260425` |

#### 标识类

| 变量 | 说明 | 示例输出 |
|------|------|------|
| `{{uuid}}` | UUID | `550e8400-e29b-41d4-a716-446655440000` |
| `{{uuid:nodash}}` | 无横线 UUID | `550e8400e29b41d4a716446655440000` |

### 4.2 请求上下文变量

在响应模板中引用请求中的数据：

| 变量 | 说明 | 示例 |
|------|------|------|
| `$request.path.xxx` | 路径变量 | `/api/user/{{id}}` → `$request.path.id` |
| `$request.query.xxx` | 查询参数 | `?name=test` → `$request.query.name` |
| `$request.header.xxx` | 请求头 | `Authorization: Bearer xxx` → `$request.header.Authorization` |
| `$request.body.xxx` | 请求体字段（JSON Path） | `{"user":{"id":1}}` → `$request.body.user.id` |

### 4.3 模板渲染流程

```
1. 第一轮：渲染内置变量（{{randomInt}}、{{timestamp}} 等）
2. 第二轮：渲染请求上下文变量（$request.path.xxx 等）
3. 返回最终响应
```

**注意**：变量替换是全局文本替换，支持在 JSON 字符串、XML、HTML 任意位置使用。

### 4.4 示例模板

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": {{randomInt:1:10000}},
    "name": "{{randomName}}",
    "email": "{{randomEmail}}",
    "phone": "{{randomPhone}}",
    "city": "{{randomCity}}",
    "createdAt": "{{datetime}}",
    "requestId": "{{uuid:nodash}}"
  }
}
```

请求 `/api/user/1` 时返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 7382,
    "name": "李娜",
    "email": "user83910@163.com",
    "phone": "15083920183",
    "city": "上海",
    "createdAt": "2026-04-25 20:30:15",
    "requestId": "a1b2c3d4e5f6g7h8"
  }
}
```

---

## 五、匹配逻辑

### 5.1 匹配流程

```
1. 过滤启用的配置
2. 按优先级升序排序（priority 越小优先级越高）
3. 依次检查每条配置：
   a. HTTP 方法必须匹配
   b. 路径必须匹配（Ant 风格）
   c. 如果有自定义匹配规则，逐一检查
4. 返回第一条匹配成功的配置
```

### 5.2 Ant 路径匹配

使用 Spring 的 `AntPathMatcher`，支持：

| 模式 | 说明 | 示例 |
|------|------|------|
| `?` | 单字符 | `/api/user?` 匹配 `/api/user1` |
| `*` | 任意字符（不含路径分隔符） | `/api/*` 匹配 `/api/user` |
| `**` | 任意字符（含路径分隔符） | `/api/**` 匹配 `/api/user/1` |
| `{var}` | 路径变量 | `/api/user/{id}` 匹配 `/api/user/123` |

### 5.3 匹配规则示例

**示例 1：查询参数匹配**

```json
[
  {
    "type": "QUERY",
    "field": "debug",
    "operator": "EQUALS",
    "value": "true"
  }
]
```

请求：`GET /mock/api/user?debug=true` → 匹配
请求：`GET /mock/api/user?debug=false` → 不匹配

**示例 2：请求头匹配**

```json
[
  {
    "type": "HEADER",
    "field": "X-Request-Id",
    "operator": "EXISTS"
  }
]
```

请求头包含 `X-Request-Id` → 匹配

**示例 3：请求体匹配**

```json
[
  {
    "type": "BODY",
    "field": "$.user.type",
    "operator": "JSON_PATH",
    "value": "admin"
  }
]
```

请求体：
```json
{"user": {"id": 1, "type": "admin"}}
```
→ 匹配

**示例 4：组合规则**

```json
[
  {
    "type": "QUERY",
    "field": "action",
    "operator": "EQUALS",
    "value": "login"
  },
  {
    "type": "BODY",
    "field": "$.username",
    "operator": "EXISTS"
  }
]
```

所有规则必须同时满足（AND 关系）。

---

## 六、API 接口

### 6.1 管理 API（端口 8080）

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/mock` | 创建 Mock 配置 |
| PUT | `/api/mock/{id}` | 更新 Mock 配置 |
| DELETE | `/api/mock/{id}` | 删除 Mock 配置 |
| GET | `/api/mock/{id}` | 根据 ID 查询 |
| GET | `/api/mock/code/{code}` | 根据编码查询 |
| GET | `/api/mock/list` | 分页查询列表 |
| GET | `/api/mock/groups` | 获取分组列表 |
| POST | `/api/mock/{id}/toggle` | 启用/禁用切换 |
| POST | `/api/mock/{id}/reset` | 重置命中统计 |
| GET | `/api/mock/stats` | 统计信息 |

### 6.2 分页查询参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| groupName | String | 否 | 分组名称筛选 |
| enabled | Boolean | 否 | 启用状态筛选 |
| keyword | String | 否 | 关键词搜索（名称/编码） |
| page | int | 否 | 页码，从 0 开始，默认 0 |
| size | int | 否 | 每页条数，默认 20 |

**排序规则**：优先级升序 → ID 升序

### 6.3 Mock 服务 API（端口 9999）

| 方法 | 端点 | 说明 |
|------|------|------|
| * | `/mock/**` | Mock 响应服务 |

支持所有 HTTP 方法：GET、POST、PUT、DELETE、PATCH、HEAD、OPTIONS。

**请求示例**：

```bash
# GET 请求
curl http://localhost:9999/mock/api/user/1

# POST 请求
curl -X POST http://localhost:9999/mock/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

**响应说明**：
- 匹配成功：返回配置的状态码 + 渲染后的响应体
- 无匹配：返回 `{"code":404,"message":"No mock configuration matched"}`

---

## 七、配置说明

### 7.1 application.yml

```yaml
# Mock 服务配置
mock:
  server:
    port: 9999  # Mock 服务独立端口

server:
  port: 8080    # 主应用端口
```

### 7.2 Undertow 多端口配置

项目使用 Undertow 替代默认 Tomcat，通过 `MockServerConfig` 配置多端口监听：

```java
@Bean
public ServletWebServerFactory webServerFactory() {
    UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
    factory.addBuilderCustomizers(builder -> {
        builder.addHttpListener(mockPort, "0.0.0.0");
    });
    return factory;
}
```

### 7.3 依赖配置

```xml
<!-- Undertow 容器 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-undertow</artifactId>
</dependency>

<!-- JSON Path 支持 -->
<dependency>
    <groupId>com.jayway.jsonpath</groupId>
    <artifactId>json-path</artifactId>
</dependency>
```

---

## 八、前端页面

### 8.1 列表页（mock_list.html）

**功能**：
- 分页展示 Mock 配置列表
- 按分组/启用状态/关键词筛选
- 快速启用/禁用切换
- 命中统计展示

**操作按钮**：
- 新增：跳转编辑页
- 编辑：跳转编辑页
- 删除：确认删除
- 切换启用状态
- 重置命中统计

### 8.2 编辑页（mock_edit.html）

**功能**：
- 新增/编辑 Mock 配置
- 响应模板编辑器
- 匹配规则配置
- 响应头配置

**表单字段**：
- 基础信息：编码、名称、分组、描述
- 请求配置：路径、HTTP 方法
- 匹配规则：JSON 编辑器（高级）
- 响应配置：状态码、响应体、响应头
- 高级配置：延迟时间、优先级

---

## 九、最佳实践

### 9.1 路径设计

**推荐**：
```
/api/user/{id}       # 单个资源
/api/users           # 列表资源
/api/orders/{id}/items/{itemId}  # 嵌套资源
```

**避免**：
```
/api/*               # 过于宽泛
/api/user/*          # 与其他配置冲突
```

### 9.2 优先级策略

| 场景 | 推荐优先级 |
|------|------|
| 精确匹配（如特定用户） | 10 |
| 带参数匹配 | 50 |
| 通配匹配 | 100 |

### 9.3 响应模板建议

1. **返回完整结构**：包含 code、message、data 标准响应格式
2. **使用动态数据**：避免硬编码，使用 `{{randomInt}}` 等变量
3. **引用请求参数**：使用 `$request.query.xxx` 实现请求响应关联

### 9.4 命名规范

- **编码**：`{业务域}_{资源}_{操作}`，如 `user_detail_query`、`order_create`
- **名称**：中文描述，如 `用户详情查询 Mock`、`创建订单 Mock`
- **分组**：按业务域分组，如 `用户模块`、`订单模块`

---

## 十、扩展能力

### 10.1 自定义模板变量

可在 `MockTemplateEngine` 中扩展新的动态变量：

```java
private String evaluateExpression(String expression) {
    // 添加自定义变量
    if (expression.equals("customVar")) {
        return "customValue";
    }
    // ...
}
```

### 10.2 匹配规则扩展

可在 `MockMatchRuleDTO` 中添加新的匹配类型和操作符。

### 10.3 响应后置处理

可在 `MockEndpointServlet` 中添加响应后置处理逻辑，如日志记录、性能采集等。

---

## 十一、依赖关系

```
MockConfigController
    └── MockConfigService
            ├── MockConfigRepository (JPA)
            ├── MockTemplateEngine
            └── ObjectMapper (JSON)

MockEndpointServlet (端口 9999)
    └── MockConfigService
            └── MockTemplateEngine
                    └── ObjectMapper (JSON Path)

MockServerConfig
    └── Undertow Builder (多端口配置)
```

---

## 十二、版本历史

| 版本 | 日期 | 变更说明 |
|------|------|------|
| 1.0.0 | 2026-04-25 | 初始版本，实现核心 Mock 功能 |

---

## 十三、相关文档

- [接口配置设计文档](api-config-design.md)
- [场景编排设计文档](scenario-design.md)
- [权限设计文档](rbac-design.md)
