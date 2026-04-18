# 权限按钮控制完善 - 2026-04-18

## 任务背景

用户要求为所有详情查看按钮添加权限控制，确保用户只能看到有权限的操作按钮。

## 完成的工作

### 1. 新增权限配置 (`permission-config.json`)

新增了 6 个详情查看权限：

| 权限码 | 名称 | 描述 | 模块 |
|--------|------|------|------|
| `api:detail` | 查看接口详情 | 查看接口配置详细信息 | api |
| `env:detail` | 查看环境详情 | 查看环境配置详细信息 | environment |
| `user:detail` | 查看用户详情 | 查看用户详细信息 | user |
| `role:detail` | 查看角色详情 | 查看角色详细信息 | role |
| `invoke-log:detail` | 查看调用日志详情 | 查看调用日志详细信息 | log |
| `audit-log:detail` | 查看审计日志详情 | 查看审计日志详细信息 | log |

### 2. 前端页面修改

#### api_list.html (接口列表)
- ✅ 详情按钮添加 `api:detail` 权限控制
- ✅ 测试按钮添加 `api:invoke` 权限控制
- ✅ 编辑按钮已有 `api:edit` 权限控制
- ✅ 删除按钮已有 `api:delete` 权限控制
- ✅ 状态切换按钮已有 `api:edit` 权限控制

#### api_logs.html (调用日志)
- ✅ 详情按钮添加 `invoke-log:detail` 权限控制
- ✅ 批量删除按钮添加 `invoke-log:delete` 权限控制
- ✅ 新增权限加载逻辑 (`loadPermissions`, `hasPermission`)

#### audit_log_list.html (审计日志)
- ✅ 详情按钮添加 `audit-log:detail` 权限控制
- ✅ 批量删除按钮添加 `audit-log:delete` 权限控制
- ✅ 新增权限加载逻辑 (`loadPermissions`, `hasPermission`)

### 3. 权限控制模式

所有页面采用统一的权限控制模式：

```javascript
// 1. 加载权限
async function loadPermissions() {
    const res = await API.auth.getPermissions();
    if (res.code === 200) {
        permissions = res.data || [];
        applyPermissions();
    }
}

// 2. 检查权限（支持管理员豁免）
function hasPermission(code) {
    if (parent.App && parent.App.getCurrentUser && parent.App.getCurrentUser().isAdmin) {
        return true; // 管理员拥有所有权限
    }
    return permissions.includes(code);
}

// 3. 渲染按钮时检查权限
${hasPermission('api:detail') ? `<button>详情</button>` : ''}
```

### 4. 未处理的页面

以下页面暂无详情按钮或已有合适的权限控制：

- **environment_list.html** - 只有编辑和删除按钮，无独立详情页
- **user_list.html** - 只有编辑和删除按钮，无独立详情页
- **role_list.html** - 点击卡片进入编辑页面，编辑按钮已有 `role:edit` 权限控制

## 重启后生效

重启应用后，`DataInitializer` 会自动创建新权限。管理员可以在角色编辑页面为不同角色分配这些权限。

## 权限分配建议

| 角色 | 建议权限 |
|------|----------|
| ADMIN | 全部权限 |
| DEVELOPER | api:*, env:*, invoke-log:*, audit-log:detail |
| READONLY | api:view, api:detail, env:view, user:view, role:view, log:view, invoke-log:detail, audit-log:detail |
