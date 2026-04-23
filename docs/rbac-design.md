# RBAC 权限系统设计文档

> 本文档描述 integration-config-system 权限系统的完整设计与实现。
> 对应章节：《说明文档.md》第九章 — 权限系统

---

## 一、架构概览

系统采用 **RBAC（Role-Based Access Control）** 模型，用户通过角色间接获得菜单权限、页面权限和按钮权限。

### 1.1 三层权限控制

```
┌─────────────────────────────────────────────────────────┐
│                    用户（User）                          │
│                     ↕  绑定                             │
│                  角色（Role）                            │
│                     ↕  绑定                             │
│        ┌───────────────┼────────────────┐               │
│   菜单权限           按钮权限          接口权限            │
│  (Menu)           (Permission)      (@RequirePermission)  │
│        │               │                   │            │
│   侧边栏动态         前端隐藏          后端拦截             │
│   菜单显示           按钮控制           API校验             │
└─────────────────────────────────────────────────────────┘
```

- **菜单权限**：控制用户在侧边栏能看到哪些菜单分组和菜单项
- **按钮权限**：控制用户在页面上能看到哪些操作按钮（新增/编辑/删除等）
- **接口权限**：后端对每个 Controller 方法进行权限校验，防止 URL 直接绕过前端

### 1.2 核心实体关系

```
User ──N:1──▶ UserRole ──N:1──▶ Role
                                ├──N:M──▶ RoleMenu ──N:1──▶ Menu
                                └──N:M──▶ RolePermission ──N:1──▶ Permission
```

6 张数据库表：
- `SYS_USER` — 用户表
- `SYS_ROLE` — 角色表
- `SYS_USER_ROLE` — 用户-角色关联
- `SYS_ROLE_MENU` — 角色-菜单关联
- `SYS_PERMISSION` — 权限表
- `SYS_ROLE_PERMISSION` — 角色-权限关联

---

## 二、数据库设计

### 2.1 SYS_ROLE（角色表）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键，雪花 ID |
| ROLE_CODE | VARCHAR(50) | 角色编码，唯一 |
| ROLE_NAME | VARCHAR(50) | 角色名称 |
| DESCRIPTION | VARCHAR(200) | 描述 |
| IS_SYSTEM | TINYINT | 是否系统角色（1=系统角色，不可删除） |
| STATUS | VARCHAR(20) | ACTIVE / INACTIVE |
| CREATED_AT | DATETIME | 创建时间 |
| UPDATED_AT | DATETIME | 更新时间 |

### 2.2 SYS_USER_ROLE（用户-角色关联）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键 |
| USER_ID | BIGINT | 用户 ID |
| ROLE_ID | BIGINT | 角色 ID |

### 2.3 SYS_MENU（菜单表）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键，雪花 ID |
| CODE | VARCHAR(50) | 菜单编码，唯一 |
| NAME | VARCHAR(50) | 菜单名称 |
| ICON | VARCHAR(50) | 图标类名 |
| PATH | VARCHAR(100) | 前端页面路径 |
| SECTION | VARCHAR(50) | 所属分组（集成管理/系统管理/开发文档） |
| SORT_ORDER | INT | 排序号 |
| STATUS | VARCHAR(20) | ACTIVE / INACTIVE |
| CREATED_AT | DATETIME | 创建时间 |

### 2.4 SYS_ROLE_MENU（角色-菜单关联）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键 |
| ROLE_ID | BIGINT | 角色 ID |
| MENU_ID | BIGINT | 菜单 ID |

### 2.5 SYS_PERMISSION（权限表）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键，雪花 ID |
| CODE | VARCHAR(100) | 权限编码，如 `api:add` |
| NAME | VARCHAR(50) | 权限名称 |
| MENU_CODE | VARCHAR(50) | 所属菜单编码 |
| TYPE | VARCHAR(20) | 类型：MENU / BUTTON |
| CREATED_AT | DATETIME | 创建时间 |

### 2.6 SYS_ROLE_PERMISSION（角色-权限关联）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键 |
| ROLE_ID | BIGINT | 角色 ID |
| PERMISSION_ID | BIGINT | 权限 ID |

---

## 三、权限编码规则

格式：`{模块}:{操作}`

### 3.1 全部权限码清单（39 个）

**接口管理（api-*）**

| 权限码 | 名称 | 类型 |
|--------|------|------|
| `api:view` | 查看接口列表 | 页面 |
| `api:detail` | 查看接口详情 | 页面 |
| `api:add` | 新增接口 | 按钮 |
| `api:edit` | 编辑接口 | 按钮 |
| `api:delete` | 删除接口 | 按钮 |
| `api:invoke` | 调用接口 | 按钮 |
| `api:doc` | 接口文档查看 | 页面 |
| `api:export` | 导出接口文档 | 按钮 |

**环境配置（environment-*）**

| 权限码 | 名称 | 类型 |
|--------|------|------|
| `environment:view` | 查看环境列表 | 页面 |
| `environment:add` | 新增环境 | 按钮 |
| `environment:edit` | 编辑环境 | 按钮 |
| `environment:delete` | 删除环境 | 按钮 |

**系统管理（system-*）**

| 权限码 | 名称 | 类型 |
|--------|------|------|
| `system:view` | 查看系统列表 | 页面 |
| `system:add` | 新增系统 | 按钮 |
| `system:edit` | 编辑系统 | 按钮 |
| `system:delete` | 删除系统 | 按钮 |

**用户管理（user-*）**

| 权限码 | 名称 | 类型 |
|--------|------|------|
| `user:view` | 查看用户列表 | 页面 |
| `user:add` | 新增用户 | 按钮 |
| `user:edit` | 编辑用户 | 按钮 |
| `user:delete` | 删除用户 | 按钮 |
| `user:resetpwd` | 重置密码 | 按钮 |

**角色管理（role-*）**

| 权限码 | 名称 | 类型 |
|--------|------|------|
| `role:view` | 查看角色列表 | 页面 |
| `role:add` | 新增角色 | 按钮 |
| `role:edit` | 编辑角色 | 按钮 |
| `role:delete` | 删除角色 | 按钮 |

**审计日志（audit-*）**

| 权限码 | 名称 | 类型 |
|--------|------|------|
| `audit:view` | 查看审计日志 | 页面 |
| `audit:export` | 导出审计日志 | 按钮 |

**告警规则（alert-rule-*）**

| 权限码 | 名称 | 类型 |
|--------|------|------|
| `alert-rule:view` | 查看告警规则 | 页面 |
| `alert-rule:add` | 新增告警规则 | 按钮 |
| `alert-rule:edit` | 编辑告警规则 | 按钮 |
| `alert-rule:delete` | 删除告警规则 | 按钮 |
| `alert-rule:toggle` | 启停告警规则 | 按钮 |
| `alert-rule:test` | 测试告警规则 | 按钮 |

**告警记录（alert-record-*）**

| 权限码 | 名称 | 类型 |
|--------|------|------|
| `alert-record:view` | 查看告警记录 | 页面 |
| `alert-record:ack` | 确认告警 | 按钮 |
| `alert-record:resolve` | 解决告警 | 按钮 |

**仪表盘（dashboard-*）**

| 权限码 | 名称 | 类型 |
|--------|------|------|
| `dashboard:view` | 查看仪表盘 | 页面 |

---

## 四、菜单配置

### 4.1 菜单清单（19 项）

菜单数据通过 `menu-config.json` 驱动，分为三个分组：

**集成管理（SECTION: 集成管理）**

| 编码 | 名称 | 图标 | 页面路径 |
|------|------|------|----------|
| `api-list` | 接口配置 | 📡 | pages/api/api_list.html |
| `api-import` | 接口导入 | 📥 | pages/api/api_import.html |
| `api-debug` | 接口调试 | 🔧 | pages/debug/debug.html |
| `invoke-log` | 调用日志 | 📋 | pages/log/invoke_log_list.html |
| `environment-list` | 环境配置 | 🌐 | pages/environment/environment_list.html |

**系统管理（SECTION: 系统管理）**

| 编码 | 名称 | 图标 | 页面路径 |
|------|------|------|----------|
| `user-list` | 用户管理 | 👤 | pages/system/user_list.html |
| `role-list` | 角色管理 | 🔐 | pages/system/role_list.html |
| `audit-log` | 审计日志 | 📜 | pages/system/audit_log_list.html |
| `dashboard` | 实时大盘 | 📊 | pages/system/dashboard.html |
| `alert-rule-list` | 告警规则 | 🔔 | pages/alert/alert_list.html |
| `alert-record-list` | 告警记录 | 📨 | pages/alert/alert_record_list.html |

**开发文档（SECTION: 开发文档）**

| 编码 | 名称 | 图标 | 页面路径 |
|------|------|------|----------|
| `api-doc` | 接口文档 | 📖 | pages/doc/api_doc.html |

### 4.2 菜单与权限的对应关系

菜单项与权限码的映射关系如下：

| 菜单编码 | 页面权限 | 按钮权限 |
|----------|----------|----------|
| `api-list` | `api:view` | `api:add` `api:edit` `api:delete` `api:invoke` `api:doc` `api:export` |
| `api-import` | `api:add` | — |
| `api-debug` | `api:view` | — |
| `invoke-log` | `audit:view` | `audit:export` |
| `environment-list` | `environment:view` | `environment:add` `environment:edit` `environment:delete` |
| `user-list` | `user:view` | `user:add` `user:edit` `user:delete` `user:resetpwd` |
| `role-list` | `role:view` | `role:add` `role:edit` `role:delete` |
| `audit-log` | `audit:view` | `audit:export` |
| `dashboard` | `dashboard:view` | — |
| `alert-rule-list` | `alert-rule:view` | `alert-rule:add` `alert-rule:edit` `alert-rule:delete` `alert-rule:toggle` `alert-rule:test` |
| `alert-record-list` | `alert-record:view` | `alert-record:ack` `alert-record:resolve` |
| `api-doc` | `api:doc` | `api:export` |

---

## 五、三种内置角色

### 5.1 角色定义

| 角色 | 编码 | 说明 |
|------|------|------|
| 超级管理员 | ADMIN | 拥有所有菜单和权限，不可删除 |
| 开发者 | DEVELOPER | 日常使用，可调用接口、调试、查看日志 |
| 访客 | READONLY | 只读权限，仅能查看报表和文档 |

### 5.2 权限分配矩阵

| 菜单/权限 | ADMIN | DEVELOPER | READONLY |
|-----------|-------|-----------|----------|
| 全部菜单 | ✅ 全部 | ✅ 全部 | ⚠️ 仅部分 |
| 接口配置（CRUD） | ✅ | ✅ | ❌ |
| 接口调试 | ✅ | ✅ | ❌ |
| 接口导入 | ✅ | ✅ | ❌ |
| 环境配置（CRUD） | ✅ | ✅ | ❌ |
| 调用日志 | ✅ | ✅ | ✅（只读） |
| 用户管理 | ✅ | ❌ | ❌ |
| 角色管理 | ✅ | ❌ | ❌ |
| 审计日志 | ✅ | ✅ | ✅（只读） |
| 实时大盘 | ✅ | ✅ | ✅ |
| 告警规则 | ✅ | ✅（不含删除） | ❌ |
| 告警记录 | ✅ | ✅ | ❌ |
| 接口文档 | ✅ | ✅ | ✅ |
| admin 用户绑定 | ✅ | ❌ | ❌ |

---

## 六、后端实现

### 6.1 @RequirePermission 注解

用于标注需要权限校验的 Controller 方法：

```java
@RequirePermission("api:add")
@PostMapping
public Result<Void> create(@RequestBody ApiConfigDTO dto) { ... }
```

注解定义：
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String[] value();  // 支持多个权限码（任一满足即可）
}
```

### 6.2 PermissionAspect 切面

切入点：所有带有 `@RequirePermission` 注解的方法。

校验流程：
1. 解析注解中的权限码
2. 从 `request.setAttribute("userPermissions")` 获取当前用户权限集合
3. 判断是否持有至少一个所需权限
4. 无权限 → 抛出 `BusinessException(ErrorCode.FORBIDDEN)`

```java
@Around("@annotation(requirePermission)")
public Object checkPermission(ProceedingJoinPoint pjp, RequirePermission requirePermission) {
    @SuppressWarnings("unchecked")
    Set<String> userPermissions = (Set<String>) request.getAttribute("userPermissions");
    if (userPermissions == null) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    String[] required = requirePermission.value();
    for (String r : required) {
        if (userPermissions.contains(r)) return pjp.proceed();
    }
    throw new BusinessException(ErrorCode.FORBIDDEN);
}
```

### 6.3 数据初始化（DataInitializer）

系统启动时，`DataInitializer` 从三个 JSON 配置文件增量初始化数据：

- `menu-config.json` → 初始化菜单（根据 CODE 匹配，存在则跳过）
- `permission-config.json` → 初始化权限
- `role-config.json` → 初始化角色及其菜单/权限绑定

**核心原则**：
- 以配置文件为准，数据库仅做补充
- 配置变更后重启即可同步（仅影响新增/修改，删除需手动处理）
- ADMIN 角色始终绑定所有菜单和权限（即使 admin 用户已存在也更新）

---

## 七、Redis 缓存策略

### 7.1 四类缓存 Key

| Key 模式 | 内容 | TTL |
|----------|------|-----|
| `integration:auth:user:{userId}` | 用户信息（UserDTO） | 30 分钟 |
| `integration:auth:permissions:{userId}` | 用户权限码集合 | 30 分钟 |
| `integration:auth:menus:{userId}` | 用户菜单列表 | 30 分钟 |
| `integration:auth:pageMap:{userId}` | 菜单编码→权限码映射 | 30 分钟 |

### 7.2 缓存清除

用户权限变更时，调用 `RoleService.clearUserPermissionCache(userId)` 清除该用户的全部 4 类缓存，下次请求时重新加载。

### 7.3 降级策略

`RedisCacheService` 对 Redis 操作进行了异常捕获，Redis 不可用时自动降级为内存 Map 缓存，保证系统可用性。

---

## 八、前端实现

### 8.1 permission.js 统一权限组件

前端统一封装在 `js/components/permission.js` 中，提供两种使用方式：

**方式一：声明式（推荐）**

```html
<!-- 页面加载后自动根据权限显示/隐藏 -->
<button data-permission="api:add">新增接口</button>
```

**方式二：编程式**

```javascript
// 显式检查权限
if (Permission.check('api:delete')) {
    $('#btn-delete').show();
}
```

### 8.2 侧边栏动态构建

`buildPageMap()` 从后端 `/api/auth/page-map` 获取当前用户的菜单权限映射，前端据此决定侧边栏显示哪些菜单项。未授权的菜单不渲染。

### 8.3 页面初始化流程

```
页面加载
    │
    ├── Permission.load() 加载当前用户权限集合
    │         │
    │         └── 获取 /api/auth/page-map → pageMap
    │
    ├── $(document).ready()
    │         │
    │         ├── Permission.apply() 扫描 data-permission 属性控制按钮显示
    │         │
    │         └── Permission.guard() 阻止用户直接访问未授权 URL
```

---

## 九、API 接口

### 9.1 权限相关接口

```bash
# 获取当前用户权限集合
GET /api/auth/permissions
Response: ["api:view", "api:add", "environment:view", ...]

# 获取当前用户菜单列表
GET /api/auth/menus
Response: [{id, code, name, icon, path, section, sortOrder}, ...]

# 获取菜单-权限映射（用于侧边栏和页面控制）
GET /api/auth/page-map
Response: { "api-list": ["api:view", "api:add", "api:edit", ...], ... }

# 清除用户权限缓存（管理员操作）
POST /api/roles/{roleId}/clear-cache?userId=xxx
```

### 9.2 角色管理接口

```bash
# 分页查询角色
GET /api/roles?page=0&size=20

# 创建角色（含菜单+权限绑定）
POST /api/roles
Body: { roleCode, roleName, description, menuIds[], permissionIds[] }

# 更新角色
PUT /api/roles/{id}
Body: { roleName, description, menuIds[], permissionIds[] }

# 删除角色（系统角色不可删除）
DELETE /api/roles/{id}

# 获取角色详情（含菜单+权限）
GET /api/roles/{id}

# 获取所有菜单（用于角色配置表单）
GET /api/menus/all

# 获取所有权限（用于角色配置表单）
GET /api/permissions/all
```

---

## 十、常见问题

**Q：新增接口后用户看不到？**
检查该接口所属菜单是否已分配给对应角色。菜单权限和按钮权限是分开控制的。

**Q：修改了 menu-config.json 后不生效？**
重启后端服务。`DataInitializer` 在启动时读取配置文件，增量同步到数据库。

**Q：用户绑定了角色但不生效？**
清除该用户的权限缓存：调用 `POST /api/roles/{roleId}/clear-cache?userId={userId}` 或让用户重新登录。

**Q：如何新增自定义权限？**
1. 在 `permission-config.json` 中添加新权限项
2. 在对应的前端页面上添加 `data-permission="xxx:yyy"` 属性
3. 重启后端，同步新权限到数据库
4. 在角色管理页面将新权限分配给对应角色

**Q：ADMIN 角色为什么删不掉？**
`role-config.json` 中 `isSystem: true` 的角色受保护，前端隐藏删除按钮，后端 `RoleService.delete()` 也做了校验。

**Q：Redis 挂了系统还能用吗？**
能。`RedisCacheService` 捕获异常后自动降级为内存缓存，不影响正常访问。但内存缓存在重启后丢失，用户需重新登录。
