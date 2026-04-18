# 角色权限问题修复 - 2026-04-18

## 问题1：角色编辑页面添加用户/接口不显示

### 根因
API 接口设计与前端调用不匹配：
- `setUserRoles(userId, roleIds)` 用于设置**用户**的角色
- 前端错误调用 `setUserRoles(roleId, userIds)`，传参错误

### 修复
新增正确的 API 接口：

| 新增接口 | 路径 | 说明 |
|----------|------|------|
| 设置角色的用户列表 | `POST /api/roles/{id}/users` | body: `{userIds: [...]}` |
| 获取角色的接口ID列表 | `GET /api/roles/{id}/apis/ids` | 返回 `List<Long>` |
| 设置角色的接口列表 | `POST /api/roles/{id}/apis` | body: `{apiIds: [...]}` |

修改文件：
- `RoleController.java` - 新增接口
- `RoleService.java` - 新增 `setRoleUsers()`, `getRoleApiIds()`, `setRoleApis()`
- `api.js` - 新增 `setRoleUsers()`, `getRoleApiIds()`, `setRoleApis()`
- `role_edit.html` - 修正 API 调用

---

## 问题2：普通用户只显示 SDK 菜单

### 根因
`DataInitializer` 只为 ADMIN 角色分配了菜单，DEVELOPER 和 READONLY 角色没有分配任何菜单。

### 修复
在 `initAdminUser()` 中新增两个方法：

1. **assignDeveloperMenus()** - 为 DEVELOPER 角色分配：
   - 集成管理全部菜单（接口配置、Curl导入、接口调试、调用日志、环境配置）
   - 开发文档（SDK文档）

2. **assignReadonlyMenus()** - 为 READONLY 角色分配：
   - 接口调试
   - 调用日志
   - SDK文档

修改文件：
- `DataInitializer.java` - 新增菜单分配逻辑
- `AuthController.java` - 添加日志输出
- `MenuService.java` - 添加日志输出

### 重启后验证
1. 用普通用户登录
2. 检查控制台日志：`[AuthController] 用户 X 的角色ID列表: [...]`
3. 检查菜单是否正确显示

---

## 注意事项

- 重启应用后，DataInitializer 会自动为各角色分配菜单
- 如果用户之前已绑定角色，菜单会立即生效
- 如果用户没有绑定任何角色，需要在用户管理页面为其分配角色
