# 菜单配置重构 - 2026-04-18

## 任务背景

用户要求取消 `app.js` 中的 `PAGE_MAP` 和 `PAGE_TO_MENU_CODE` 映射，改为后端返回的 code 与 `_sidebar.html` 一致，JSON 中对应的 html 路由添加 `pages/` 前缀。

## 主要变更

### 1. menu-config.json 重构

**修改前：**
- code: `api-list`, `api-import`, `api-debug`, `api-logs` ...
- pageFile: `api_list.html`, `api_import.html` ...

**修改后：**
- code: `list`, `import`, `debug`, `logs`, `environments`, `users`, `roles`, `audit-logs`, `sdk`
- pageFile: `pages/api_list.html`, `pages/api_import.html` ...（包含 `pages/` 前缀）

**对应关系（code → _sidebar.html nav-item id）：**
| code | nav-item id | 说明 |
|------|-------------|------|
| list | nav-list | 接口配置 |
| import | nav-import | Curl导入 |
| debug | nav-debug | 接口调试 |
| logs | nav-logs | 调用日志 |
| environments | nav-environments | 环境配置 |
| users | nav-users | 用户管理 |
| roles | nav-roles | 角色权限 |
| audit-logs | nav-audit | 审计日志（特殊映射） |
| sdk | nav-sdk | SDK文档 |

### 2. app.js 重构

**移除：**
- `PAGE_MAP` 常量（硬编码的页面映射）
- `PAGE_TO_MENU_CODE` 常量（页面路径到菜单code的映射）

**新增：**
- `pageMap` 动态变量（从后端菜单数据构建）
- `buildPageMap(menus)` 方法：从后端返回的菜单数据构建页面映射
- 额外页面（非菜单页面）硬编码在 `buildPageMap` 中

**权限检查简化：**
- `hasMenuAccess(menuCode)` 直接用 menuCode 检查，无需转换
- `loadPage(page, params)` 直接用 page 作为 menuCode
- `updateNav(page)` 特殊处理 `audit-logs` → `nav-audit`

### 3. Menu 实体新增字段

```java
@Column(name = "PAGE_FILE", length = 200)
private String pageFile;
```

### 4. DataInitializer 修改

- `initMenusFromJson()`: 添加 `.pageFile(def.getPageFile())`
- `initDefaultMenus()`: 更新 code 和添加 pageFile
- `assignReadonlyMenus()`: 更新菜单 code（`debug`, `logs`, `sdk`）
- `generatePageMap()`: 直接使用 pageFile（不再添加 `pages/` 前缀）

## 权限控制逻辑

**菜单权限检查流程：**
1. 用户登录后调用 `API.auth.getMenus()` 获取可访问的菜单列表
2. 每个菜单项的 `code` 与 `_sidebar.html` 中的 `nav-item id` 对应
3. `applyMenuPermissions()` 遍历 `.nav-item`，根据 code 检查权限
4. 特殊映射：`nav-audit` 对应 `audit-logs`

**页面访问权限检查：**
1. `loadPage(page)` 被调用
2. `hasMenuAccess(page)` 检查用户是否有该菜单的权限
3. 管理员（`isAdmin=true`）拥有所有权限

## 重启后生效

重启应用后：
1. `DataInitializer` 会更新菜单数据（如果数据库已有数据则跳过）
2. 如需重新初始化菜单，需手动清空 `SYS_MENU` 表

## 注意事项

1. **audit-logs 特殊处理**：
   - 菜单 code 是 `audit-logs`
   - _sidebar.html 中 nav-item id 是 `nav-audit`
   - app.js 中有特殊映射逻辑

2. **数据库已有菜单数据**：
   - 如果 `SYS_MENU` 表已有数据，`DataInitializer` 会跳过初始化
   - 需要手动更新或清空后重启

3. **前端缓存**：
   - 修改后建议强制刷新（Ctrl+Shift+R）
