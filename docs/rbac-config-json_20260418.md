# RBAC 配置 JSON 化 - 2026-04-18

## 背景

用户反馈：
1. 新创建的角色组没有显示菜单
2. 希望角色和权限配置像菜单一样从 JSON 文件读取

## 问题根因

1. **新角色没有分配菜单** - 创建角色后，需要在"菜单配置"Tab 手动勾选菜单并保存
2. **操作不便** - 没有全选功能，逐个勾选效率低
3. **配置硬编码** - 角色和权限在代码中硬编码，不够灵活

## 解决方案

### 1. 配置文件 JSON 化

**创建 `role-config.json`**：
```json
{
  "roles": [
    {
      "code": "ADMIN",
      "name": "管理员",
      "description": "拥有所有权限，可管理用户、角色、接口配置",
      "isSystem": true,
      "sortOrder": 1
    },
    // ... 其他角色
  ]
}
```

**创建 `permission-config.json`**：
```json
{
  "permissions": [
    {
      "code": "api:view",
      "name": "查看接口",
      "description": "查看接口配置列表和详情",
      "module": "api",
      "sortOrder": 1
    },
    // ... 其他权限
  ]
}
```

### 2. 实体修改

**Permission.java** 添加字段：
- `module` - 所属模块（api/environment/user/role/log/system）
- `sortOrder` - 排序号

### 3. 服务层修改

**RoleService.initSystemRoles()**：
- 改为从 JSON 读取
- 支持创建和更新（每次启动检查并更新）
- 强制设置 `status = ACTIVE`

**DataInitializer.initPermissions()**：
- 改为从 JSON 读取
- 支持创建和更新

### 4. 前端改进

**role_edit.html**：
- 添加全局全选/取消全选按钮
- 添加分组全选功能
- 显示已选择数量
- 更明显的保存提示

## 修改文件清单

### 后端
- `src/main/resources/config/role-config.json` - 新增
- `src/main/resources/config/permission-config.json` - 新增
- `src/main/java/.../entity/config/Permission.java` - 添加 module、sortOrder 字段
- `src/main/java/.../service/RoleService.java` - JSON 化 initSystemRoles
- `src/main/java/.../config/DataInitializer.java` - JSON 化 initPermissions

### 前端
- `src/main/resources/static/pages/role_edit.html` - 全选功能、计数显示

## 用户操作指南

### 创建新角色并分配菜单

1. **创建角色**：点击"新建角色"，填写编码、名称、描述
2. **编辑角色**：点击角色卡片进入编辑页
3. **切换到"菜单配置"Tab**
4. **勾选菜单**：
   - 点击分组标题旁的"全选"快速选中整个分组
   - 或点击顶部的"全选"选中所有菜单
5. **点击"保存菜单配置"按钮**

### 权限配置同理

1. 切换到"按钮权限"Tab
2. 勾选需要的权限
3. 点击"保存权限配置"按钮

## 注意事项

1. **每次启动都会同步配置**：如果修改了 JSON 文件，重启应用后会更新数据库
2. **系统角色不可删除**：ADMIN/DEVELOPER/READONLY 标记为 `isSystem=true`，删除按钮隐藏
3. **ADMIN 角色自动拥有所有菜单和权限**：每次启动都会同步

---
*Created: 2026-04-18*
