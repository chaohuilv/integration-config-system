# 统一接口配置管理系统

> 业务系统集成的核心 — 通过前端配置接口参数，业务系统只需一行 SDK 调用即可。

## 一、项目概述

### 1.1 解决什么问题

企业级项目中，多个业务系统需要对接大量外部接口。传统做法是各业务系统各自编写接口调用代码，导致：

- **代码重复**：同一接口在多个项目中重复实现
- **配置分散**：接口配置散落在各个项目中，无法统一管理
- **修改困难**：接口变更需要修改多个项目的代码
- **无法监控**：无法统一查看所有接口的调用情况

**统一接口配置系统**将所有接口配置集中管理，通过可视化界面配置接口参数，业务系统只需通过 SDK 调用即可。

### 1.2 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.2 + MyBatis-Plus + JPA |
| 数据库 | H2（开发）/ MySQL（生产）· 三数据源架构 |
| 缓存 | Redis（Token 存储 + 运行时缓存） |
| 前端 | 原生 HTML/CSS/JS（零框架依赖） + Thymeleaf |
| 文档 | SpringDoc OpenAPI 3 + Swagger UI |
| SDK | Java / Python / C# |

### 1.3 数据库架构

采用**三数据源**隔离设计，避免不同业务数据相互影响：

```
┌─────────────────────────────────────────────────────┐
│              integration_config (主库)                │
│  用户 · 角色 · 菜单 · 权限 · 接口配置 · 环境配置      │
├─────────────────────────────────────────────────────┤
│               integration_log (日志库)               │
│              接口调用日志 · 审计日志                   │
├─────────────────────────────────────────────────────┤
│              integration_token (Token库)             │
│                 用户认证 Token                        │
└─────────────────────────────────────────────────────┘
```

---

## 二、功能模块

### 2.1 功能总览

| 模块 | 说明 |
|------|------|
| **接口配置管理** | CRUD、在线调试、参数预览、导入导出 |
| **环境管理** | 多环境（开发/测试/生产）URL 替换，支持同系统单环境激活 |
| **调用日志** | 每次接口调用的请求/响应/耗时/状态完整记录 |
| **审计日志** | 所有写操作的模块+操作+描述记录，含请求参数 |
| **实时大盘** | 7 天趋势图、成功率 Top API 排行、系统资源监控 |
| **RBAC 权限** | 菜单/按钮级权限控制，JSON 文件驱动权限码 |
| **API 文档** | Swagger UI 在线调试 + 系统内文档页 + Word 导出 |
| **API 版本控制** | 多版本共存、平滑迁移、废弃标记 |
| **在线调试** | 无需编写代码，直接在页面测试接口调用 |
| **告警通知** | 错误率/延迟/限流/连续失败监控，自动钉钉/企微/邮件通知 |

### 2.2 接口配置

支持以下高级特性：

- **占位符替换**：`{{paramName}}` 在 URL/请求体/请求头中自动替换
- **认证方式**：Bearer Token / Basic Auth / API Key / 无认证
- **响应缓存**：按接口独立配置 TTL，减少重复调用
- **重试机制**：自动重试失败请求（可配置次数）
- **限流控制**：基于时间窗口的请求频率限制
- **动态 Token**：运行时从另一个接口获取 Token 后自动注入

### 2.3 环境管理

```yaml
# 原始接口 URL
https://api.production.com/users/{{id}}

# 测试环境 baseUrl
https://api.test.com

# 替换后 → 调用时自动拼接
https://api.test.com/users/{{id}}
```

同系统只能有一个环境处于激活状态，激活时自动停用其他环境。

---

## 三、快速开始

### 3.1 环境要求

- JDK 17+
- Maven 3.8+
- Redis 6+
- H2（开发）或 MySQL 8+（生产）

### 3.2 编译启动

```bash
cd integration-config-system
mvn clean package -DskipTests
java -jar target/integration-config-system-1.0.0.jar
```

### 3.3 访问地址

| 服务 | 地址 |
|------|------|
| 管理后台 | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| API 文档 | http://localhost:8080/swagger-ui.html |
| H2 控制台 | http://localhost:8080/h2-console |

> H2 默认凭据：`sa` / 空密码，JDBC URL: `jdbc:h2:./data/integration_config`

### 3.4 默认账号

系统初始化会自动创建管理员账号：

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | admin123 | 管理员（拥有所有权限） |

---

## 四、配置说明

### 4.1 application.yml 关键配置

```yaml
# ========== 数据源配置（主库）==========
spring:
  datasource-config:
    url: jdbc:h2:./data/integration_config  # 切换 MySQL 见下方
    driver-class-name: org.h2.Driver
    username: sa
    password:

  # ========== MySQL 生产配置 ==========
  # datasource-config:
  #   url: jdbc:mysql://localhost:3306/integration_config
  #   driver-class-name: com.mysql.cj.jdbc.Driver
  #   username: root
  #   password: your_password

# ========== Redis 配置 ==========
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 2          # 用于 Token 和缓存
      timeout: 5000ms

# ========== Token 配置 ==========
integration:
  token:
    expire-hours: 168       # 7 天过期

  # ========== 缓存配置 ==========
  cache:
    enabled: true
    prefix: "integration:cache:"
    ttl: 300                # 秒

  # ========== 环境管理 ==========
  environment:
    enabled: true

# ========== Swagger ==========
springdoc:
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
```

---

## 五、API 参考

### 5.1 认证接口 `/api/auth`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/login` | POST | 登录，返回 Token |
| `/api/auth/logout` | POST | 登出 |
| `/api/auth/current` | GET | 获取当前用户信息 |
| `/api/auth/check` | GET | 验证 Token 有效性 |

**登录示例：**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"userCode":"admin","password":"admin123"}'
```

**响应：**
```json
{
  "code": 200,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1,
    "userCode": "admin"
  }
}
```

### 5.2 接口配置 `/api/config`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/config/page` | GET | 分页查询（支持 version 筛选） |
| `/api/config/{id}` | GET | 获取详情 |
| `/api/config` | POST | 创建 |
| `/api/config/{id}` | PUT | 更新 |
| `/api/config/{id}` | DELETE | 删除 |
| `/api/config/active` | GET | 所有启用接口 |
| `/api/config/{id}/toggle` | POST | 切换启用状态 |
| `/api/config/{id}/version` | POST | 创建新版本 |
| `/api/config/{id}/versions` | GET | 获取所有版本 |
| `/api/config/{id}/set-latest` | POST | 设为主版本 |
| `/api/config/{id}/deprecate` | POST | 切换废弃状态 |

### 5.3 接口调用 `/api/invoke`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/invoke` | POST | 调用接口 |
| `/api/invoke/{apiCode}` | GET | 通过编码调用 |
| `/api/invoke/logs` | GET | 查询调用日志 |
| `/api/invoke/logs/detail/{id}` | GET | 日志详情 |
| `/api/invoke/logs/{apiCode}/recent` | GET | 最近调用记录 |

**调用示例：**
```bash
curl -X POST http://localhost:8080/api/invoke \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"apiCode":"user-get","params":{"id":1}}'
```

### 5.4 环境管理 `/api/environment`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/environment/list` | GET | 列表（支持按系统筛选） |
| `/api/environment/{id}` | GET | 详情 |
| `/api/environment` | POST | 创建 |
| `/api/environment/{id}` | PUT | 更新 |
| `/api/environment/{id}` | DELETE | 删除 |

### 5.5 实时大盘 `/api/dashboard`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/dashboard/overview` | GET | 统计总览 |
| `/api/dashboard/invoke-trend` | GET | 7 天调用趋势 |
| `/api/dashboard/top-apis` | GET | Top 10 API |
| `/api/dashboard/audit-stats` | GET | 审计统计 |
| `/api/dashboard/recent-activity` | GET | 最近活动 |
| `/api/dashboard/health` | GET | 健康检查 |
| `/api/dashboard/system-resources` | GET | 系统资源（CPU/内存/JVM） |

### 5.6 告警管理 `/api/alert`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/alert/rules` | GET | 分页查询告警规则 |
| `/api/alert/rules/active` | GET | 所有启用规则 |
| `/api/alert/rules/{id}` | GET | 规则详情 |
| `/api/alert/rules` | POST | 创建规则 |
| `/api/alert/rules/{id}` | PUT | 更新规则 |
| `/api/alert/rules/{id}` | DELETE | 删除规则 |
| `/api/alert/rules/{id}/toggle` | POST | 启用/停用 |
| `/api/alert/rules/{id}/test` | POST | 发送测试告警 |
| `/api/alert/rules/{id}/evaluate` | POST | 手动触发评估 |
| `/api/alert/records` | GET | 分页查询告警记录 |
| `/api/alert/records/{id}/acknowledge` | POST | 确认告警 |
| `/api/alert/records/{id}/resolve` | POST | 标记已解决 |
| `/api/alert/overview` | GET | 告警概览（告警中数量等） |

### 5.7 角色管理 `/api/roles`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/roles/page` | GET | 分页列表 |
| `/api/roles/{id}` | GET | 详情 |
| `/api/roles` | POST | 创建 |
| `/api/roles/{id}` | PUT | 更新 |
| `/api/roles/{id}` | DELETE | 删除 |
| `/api/roles/{id}/users` | POST | 设置角色用户 |
| `/api/roles/{id}/apis` | POST | 设置角色接口权限 |
| `/api/roles/{id}/menus` | POST | 设置角色菜单 |
| `/api/roles/{id}/permissions` | POST | 设置角色按钮权限 |

---

## 六、权限系统

### 6.1 设计概述

采用 **RBAC**（基于角色的访问控制）模型：

```
用户 ←→ 用户角色 ←→ 角色
  │                      │
  │                      ├── 角色菜单（可见菜单）
  │                      │
  │                      └── 角色权限（按钮级控制）
  │                             │
  └── 角色接口权限（API 访问控制）
```

### 6.2 权限码体系

所有权限码定义在 `permission-config.json` 中：

```json
{
  "categories": [
    { "name": "接口管理", "code": "api", "permissions": [
      { "code": "api:add",   "name": "新建接口" },
      { "code": "api:edit",  "name": "编辑接口" },
      { "code": "api:delete","name": "删除接口" },
      { "code": "api:view",  "name": "查看接口" },
      { "code": "api:invoke","name": "调用接口" },
      { "code": "api:import","name": "导入接口" },
      { "code": "api:export","name": "导出文档" }
    ]},
    { "name": "角色管理", "code": "role", "permissions": [
      { "code": "role:add",    "name": "新建角色" },
      { "code": "role:edit",   "name": "编辑角色" },
      { "code": "role:delete", "name": "删除角色" },
      { "code": "role:view",   "name": "查看角色" },
      { "code": "role:detail", "name": "角色详情" }
    ]}
  ]
}
```

### 6.3 后端权限校验

使用 `@RequirePermission` 注解标注在 Controller 方法上：

```java
@RequirePermission("api:add")
@PostMapping
public Result<ApiConfig> create(@RequestBody ApiConfigDTO dto) { ... }

@RequirePermission("api:edit")
@PutMapping("/{id}")
public Result<Void> update(@PathVariable Long id, @RequestBody ApiConfigDTO dto) { ... }
```

权限切面 `PermissionAspect` 在方法执行前校验用户是否拥有对应权限。

### 6.4 前端权限校验

统一封装在 `js/components/permission.js` 中，支持两种方式：

**声明式（推荐）：**
```html
<button data-permission="api:delete" onclick="deleteConfig(1)">删除</button>
```

**编程式：**
```javascript
if (Permission.check('api:edit')) {
    $('#editBtn').show();
}
```

页面加载时，`App.loadPage()` 自动调用 `Permission.check()` 隐藏无权限按钮。

---

## 七、审计日志

### 7.1 使用方式

在 Controller 方法上添加 `@AuditLog` 注解：

```java
@AuditLog(module = "接口管理", action = "创建接口", recordParams = true)
public Result<ApiConfig> create(@RequestBody ApiConfigDTO dto) { ... }

@AuditLog(module = "接口管理", action = "查询接口", operateType = "QUERY")
public Result<PageResult<ApiConfig>> pageQuery(...) { ... }
```

### 7.2 记录内容

| 字段 | 说明 |
|------|------|
| module | 模块名称 |
| action | 操作描述 |
| recordParams | 是否记录请求参数（query string 或 JSON body） |
| requestParams | 结构化 JSON：`{"query":{...},"body":"..."}` |
| description | 由 SpEL 表达式动态生成的描述文本 |

### 7.3 requestParams 结构

```json
{
  "query": { "page": 1, "size": 10 },
  "body":  { "name": "用户接口", "code": "user-api" }
}
```

POST JSON 请求由 `LoginFilter` 中的 `CachedBodyHttpServletRequest` 提前缓存，保证审计 AOP 能读取到 body。

---

## 八、SDK 使用

### 8.1 Java SDK

**引入依赖：**
```xml
<dependency>
    <groupId>com.integration</groupId>
    <artifactId>integration-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

**方式一：用户名密码登录（自动获取 Token）**
```java
IntegrationClient client = new IntegrationClient(
    "http://localhost:8080", "admin", "admin123"
);
String result = client.invoke("user-get", Map.of("id", 1));
```

**方式二：直接传入 Token**
```java
IntegrationClient client = new IntegrationClient(
    "http://localhost:8080", "your-token-here"
);
String result = client.invoke("user-get", params);
```

### 8.2 Python SDK

```python
from integration_sdk import IntegrationClient

client = IntegrationClient(
    base_url="http://localhost:8080",
    user_code="admin",
    password="admin123"
)
result = client.invoke("user-get", {"id": 1})
```

### 8.3 C# SDK

```csharp
var client = new IntegrationClient(
    "http://localhost:8080",
    "admin",
    "admin123"
);
var result = client.Invoke("user-get", new Dictionary<string, object> { {"id", 1} });
```

---

## 九、项目结构

```
integration-config-system/
├── src/main/java/com/integration/config/
│   ├── IntegrationConfigApplication.java       # 启动类
│   │
│   ├── annotation/
│   │   ├── AuditLog.java                      # 审计日志注解
│   │   └── RequirePermission.java              # 权限注解
│   │
│   ├── aspect/
│   │   ├── AuditLogAspect.java                 # 审计日志 AOP
│   │   └── PermissionAspect.java               # 权限校验切面
│   │
│   ├── config/
│   │   ├── DataInitializer.java                # 启动数据初始化
│   │   ├── HeaderFilter.java                   # 请求头处理
│   │   ├── IntegrationConfig.java              # 全局配置属性
│   │   ├── JacksonConfig.java                  # JSON 序列化配置
│   │   ├── LoginFilter.java                    # Token 认证过滤器
│   │   ├── MultiDataSourceConfig.java          # 三数据源配置
│   │   ├── OpenApiConfig.java                  # Swagger 配置
│   │   ├── RestTemplateConfig.java             # HTTP 客户端配置
│   │   └── WebConfig.java                     # Web 初始化配置
│   │
│   ├── controller/
│   │   ├── ApiConfigController.java           # 接口配置管理
│   │   ├── ApiDocController.java              # 文档导出
│   │   ├── AuditSysLogController.java         # 审计日志
│   │   ├── AuthController.java               # 认证
│   │   ├── DashboardController.java           # 实时大盘
│   │   ├── EnvironmentController.java        # 环境管理
│   │   ├── HealthController.java             # 健康检查
│   │   ├── InvokeController.java             # 接口调用
│   │   ├── OpenApiController.java           # OpenAPI 解析
│   │   ├── RoleController.java              # 角色管理
│   │   ├── SystemController.java           # 系统菜单/权限查询
│   │   └── VersionController.java          # API 版本控制
│   │
│   ├── dto/                                  # 数据传输对象
│   ├── enums/                               # 枚举类
│   │   ├── ContentType.java                # 请求内容类型
│   │   ├── ErrorCode.java                  # 错误码
│   │   ├── HttpMethod.java                 # HTTP 方法
│   │   └── Status.java                     # 状态枚举
│   │
│   ├── entity/
│   │   ├── config/                         # 配置域实体
│   │   │   ├── ApiConfig.java             # 接口配置
│   │   │   ├── ApiRole.java              # 接口-角色关联
│   │   │   ├── Environment.java         # 环境配置
│   │   │   ├── Menu.java                # 菜单
│   │   │   ├── Permission.java          # 权限
│   │   │   ├── Role.java                # 角色
│   │   │   ├── RoleMenu.java           # 角色-菜单关联
│   │   │   ├── RolePermission.java     # 角色-权限关联
│   │   │   ├── User.java               # 用户
│   │   │   └── UserRole.java          # 用户-角色关联
│   │   ├── log/                        # 日志域实体
│   │   └── token/                      # Token 域实体
│   │
│   ├── exception/
│   │   ├── BusinessException.java      # 业务异常
│   │   └── GlobalExceptionHandler.java # 全局异常处理
│   │
│   ├── repository/                      # 数据访问层
│   │   ├── config/                     # 配置域 Repository
│   │   ├── log/                        # 日志域 Repository
│   │   └── token/                      # Token 域 Repository
│   │
│   ├── service/
│   │   ├── ApiConfigService.java      # 接口配置服务
│   │   ├── ApiDocExportService.java   # Word 文档导出
│   │   ├── ApiDocService.java         # 在线文档服务
│   │   ├── AuditSysLogService.java    # 审计日志服务
│   │   ├── EnvironmentService.java    # 环境管理服务
│   │   ├── HttpInvokeService.java    # HTTP 调用服务
│   │   ├── MenuService.java          # 菜单服务
│   │   ├── OpenApiService.java       # OpenAPI 解析
│   │   ├── PermissionService.java    # 权限服务
│   │   ├── RateLimitService.java    # 限流服务
│   │   ├── RedisCacheService.java   # Redis 缓存封装
│   │   ├── RoleService.java         # 角色服务
│   │   ├── TokenCacheManager.java   # Token 缓存管理
│   │   ├── TokenService.java        # Token 服务
│   │   └── UserService.java         # 用户服务
│   │
│   └── util/
│       ├── AppConstants.java         # 全局常量
│       ├── CurlParser.java           # Curl 导入解析
│       ├── JsonPathUtil.java         # JSON 路径工具
│       ├── JsonUtil.java             # JSON 工具
│       ├── Messages.java             # 国际化消息
│       ├── SnowflakeUtil.java        # 雪花 ID
│       └── TraceUtil.java            # 链路追踪
│
├── src/main/resources/
│   ├── application.yml               # 主配置文件
│   ├── messages.properties           # 国际化消息（英文）
│   ├── messages_zh.properties        # 国际化消息（中文）
│   │
│   ├── config/
│   │   ├── menu-config.json          # 菜单配置
│   │   ├── permission-config.json    # 权限码配置
│   │   └── role-config.json          # 角色配置
│   │
│   └── static/
│       ├── index.html                # 管理后台入口
│       ├── login.html                # 登录页
│       ├── 接口文档模板.docx          # Word 导出模板
│       ├── css/                      # 样式文件
│       ├── js/                       # 前端脚本
│       └── pages/                    # 各功能页面
│
├── client-sdk/                       # 客户端 SDK 源码
│   ├── java/
│   ├── python/
│   └── csharp/
│
└── docs/                             # 设计文档
    ├── README.md                     # 本文档
    ├── rbac-design.md               # RBAC 设计文档
    └── *_20260*.md                  # 历次开发记录
```

---

## 十、前端架构

### 10.1 核心文件

| 文件 | 作用 |
|------|------|
| `index.html` | SPA 入口，加载 Sidebar + main content |
| `login.html` | 登录页 |
| `js/app.js` | 路由控制、页面加载、权限守卫 |
| `js/api.js` | API 请求封装，自动附加 Authorization |
| `js/utils.js` | 通用工具函数 |
| `js/components/permission.js` | 权限加载与页面守卫 |
| `js/components/pagination.js` | 分页组件 |
| `css/base.css` | 全局基础样式 |
| `css/components.css` | 通用组件样式（按钮/表格/表单/徽章等） |
| `css/sidebar.css` | 侧边栏样式 |

### 10.2 页面加载流程

```
用户访问 index.html
  → App.init() 读取 localStorage 中的 Token
  → Token 有效？ → AuthController /current 验证
    ├─ 有效 → 加载侧边栏 + 首页仪表盘
    └─ 无效 → 跳转 login.html
```

### 10.3 页面加载权限守卫

每个列表页和表单页初始化时：

```javascript
// api_list.html
App.loadPage('pages/api_list.html', () => {
    // 页面内容加载后...
    Permission.checkAll();  // 自动隐藏无权限按钮
});
```

---

## 十一、设计决策记录

### 11.1 Session → Bearer Token 迁移

- **放弃方案**：Spring Session + Redis 存储 HttpSession（Cookie 跨域问题无法解决）
- **最终方案**：Bearer Token。`LoginFilter` 从 `Authorization: Bearer <token>` 头提取 Token，存入 Redis（7 天过期），请求属性中传递 userId/userCode

### 11.2 雪花 ID 精度

- **问题**：JavaScript Number 最大安全整数 9007199254740991，雪花 ID 超出范围会丢失精度
- **解决**：后端 `@JsonSerialize(using = ToStringSerializer.class)` 将 Long 类型序列化为字符串

### 11.3 POST JSON Body 读取

- **问题**：Request body 流只能读一次，`request.getParameterMap()` 对 JSON 无效
- **解决**：`LoginFilter` 中使用 `CachedBodyHttpServletRequest` 包装请求，缓存 body 供后续审计 AOP 读取

### 11.4 菜单映射硬编码 → 动态构建

- 初始 `PAGE_MAP` 硬编码 → 中期 `PAGE_TO_MENU_CODE` 映射表
- **最终方案**：`MenuService` 从数据库加载菜单，`App.buildPageMap()` 动态构建路由映射，菜单 Code 与 Sidebar ID 一一对应

### 11.5 API 版本控制设计

| 字段 | 说明 |
|------|------|
| `baseCode` | 分组标识（如 `user-api`） |
| `version` | 版本号（如 `v1`, `v2`） |
| `code` | 全局唯一 = `baseCode-version` |
| `latestVersion` | 是否为当前主版本 |
| `deprecated` | 是否已废弃 |

---

## 十二、错误码体系

所有业务异常通过 `BusinessException` 抛出，`GlobalExceptionHandler` 统一处理，返回语义化错误信息：

| 错误码 | 含义 |
|--------|------|
| `BAD_REQUEST` | 请求参数错误 |
| `UNAUTHORIZED` | 未认证或 Token 过期 |
| `FORBIDDEN` | 无权限访问 |
| `NOT_FOUND` | 资源不存在 |
| `CONFLICT` | 资源冲突（如重复编码） |
| `SERVER_ERROR` | 服务器内部错误 |

---

## 十三、运营维护

### 13.1 缓存清理

用户角色变更后，系统自动清除以下 Redis 缓存：

```
integration:cache:user:{userId}:permissions
integration:cache:user:{userId}:menus
integration:cache:user:{userId}:pageMap
integration:cache:menu:active
integration:cache:role:{roleId}:permissions
integration:cache:role:{roleId}:apis
```

### 13.2 系统监控端点

```
GET /api/dashboard/system-resources  # CPU / 内存 / JVM 堆内存
GET /api/health/session              # 会话状态
GET /api/health/redis-keys           # Redis 连接状态
```

### 13.3 审计日志查询

支持按模块、操作类型、时间范围、操作用户精确筛选，可导出为 Excel。

---

## 十四、告警通知系统

支持对接口调用进行实时监控，当错误率、响应延迟、请求频率等指标超过预设阈值时，自动通过钉钉、企业微信、邮件等渠道发送告警通知。

### 14.1 功能入口

系统菜单：**系统管理 → 告警规则** 或 **系统管理 → 告警记录**

### 14.2 告警规则

在「告警规则」页面点击「新建规则」，配置触发条件和通知渠道：

**基本信息**

| 字段 | 说明 |
|------|------|
| 规则名称 | 如：用户接口错误率监控 |
| 规则编码 | 唯一标识，如：`user-api-error-rate` |

**触发条件**

| 字段 | 说明 |
|------|------|
| 告警类型 | 错误率 / 延迟 / 限流 / 连续失败 |
| 统计范围 | 全局（所有接口）/ 指定接口 |
| 阈值 | 超此值即触发告警 |
| 统计窗口（秒） | 在该时间窗口内统计指标 |

告警类型说明：

- **错误率**：失败调用数 / 总调用数 × 100%，超过阈值告警
- **延迟**：平均响应时间（毫秒），超过阈值告警
- **限流**：时间窗口内调用总次数，超过阈值告警
- **连续失败**：最近 N 次调用全部失败，立即告警

**通知渠道（至少选一个）**

- 🤖 **钉钉**：需要群机器人的 Webhook 地址，可选加签密钥
- 💬 **企微**：需要群机器人的 Webhook 地址
- 📧 **邮件**：填写收件人邮箱（多个逗号分隔），需配置 SMTP

**高级设置**

- 冷却时间（秒）：告警触发后，多久内不重复告警，默认 300 秒

### 14.3 告警记录

「告警记录」页面展示所有触发过的告警事件：

- 🔥 **告警中（FIRING）**：指标持续超标，等待处理
- 🟡 **已确认（ACKNOWLEDGED）**：运维人员已知悉
- ✅ **已解决（RESOLVED）**：指标恢复正常或已处理

点击「确认」可标记为已知悉；点击「解决」可标记为已处理。

### 14.4 测试告警

配置完成后，点击规则列表的「测试」按钮，可立即触发一条测试告警，前往「告警记录」页面查看通知是否正常到达。

### 14.5 自动触发机制

系统在后台每 **60 秒**自动评估所有启用规则，无需人工干预。首次触发告警后，进入冷却期（默认 5 分钟），期间同一规则不会重复告警。

### 14.6 配置说明

**application.yml 配置示例：**

```yaml
spring:
  mail:
    host: smtp.example.com
    port: 465
    username: alert@example.com
    password: your-smtp-password
    properties:
      mail:
        smtp:
          auth: true
          ssl:
            enable: true
```

钉钉/企微 Webhook 无需在配置文件中填写，在规则表单中直接填写群机器人地址即可。

### 14.7 通知内容格式

告警消息包含以下信息：

```
🔔 [告警通知] 接口错误率超标

规则：用户接口错误率监控
接口：全局
类型：错误率
实际值：15.3%
阈值：10%
触发时间：2026-04-22 16:30:00
详情：最近5分钟内，user-api 接口共调用200次，其中失败31次，错误率15.3%，超过阈值10%
```

---

## 附录 A：错误排查

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| 登录失败 401 | Token 过期或 Redis 未启动 | 重启后端或检查 Redis 连接 |
| 接口调用失败 | 接口未启用或认证方式配置错误 | 检查接口状态和认证配置 |
| H2 控制台无法连接 | JDBC URL 不正确 | 确认 URL 为 `jdbc:h2:./data/integration_config` |
| 前端页面空白 | Token 过期 | 清除 localStorage 重新登录 |
| 雪花 ID 显示异常 | 前端 JSON 解析大数精度丢失 | 后端已配置 `@JsonSerialize(using=ToStringSerializer)` |

---

## 附录 B：相关文档

| 文档 | 说明 |
|------|------|
| `docs/rbac-design.md` | RBAC 权限系统详细设计 |
| `docs/rbac-task-summary_20260418.md` | 权限系统开发记录 |
| `docs/menu-config-refactor_20260418.md` | 菜单配置重构记录 |
| `docs/role-permission-fix_20260418.md` | 角色权限修复记录 |
| `docs/alert-guide.md` | 告警通知系统完整使用指南 |
