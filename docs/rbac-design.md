# RBAC 权限系统重构方案

## 需求概述

1. **菜单权限**：根据角色动态显示菜单
2. **按钮权限**：新增(add)、编辑(edit)、删除(delete) 按钮级别的权限控制
3. **接口权限**：后端 API 权限校验
4. **管理员保护**：ADMIN 角色绑定 admin 账户，不可删除

## 数据库设计

### 1. SYS_MENU（菜单表）
```sql
CREATE TABLE SYS_MENU (
    ID BIGINT PRIMARY KEY,
    CODE VARCHAR(50) NOT NULL UNIQUE,      -- 菜单编码，如 'api-list'
    NAME VARCHAR(50) NOT NULL,              -- 菜单名称
    ICON VARCHAR(50),                       -- 图标
    PATH VARCHAR(100),                      -- 前端路由路径
    PARENT_ID BIGINT,                       -- 父菜单ID
    SORT_ORDER INT DEFAULT 0,               -- 排序
    STATUS VARCHAR(20) DEFAULT 'ACTIVE',    -- 状态
    CREATED_AT TIMESTAMP,
    UPDATED_AT TIMESTAMP
);
```

### 2. SYS_PERMISSION（权限表）
```sql
CREATE TABLE SYS_PERMISSION (
    ID BIGINT PRIMARY KEY,
    CODE VARCHAR(100) NOT NULL UNIQUE,      -- 权限编码，如 'api:add'
    NAME VARCHAR(50) NOT NULL,              -- 权限名称
    MENU_ID BIGINT,                         -- 所属菜单
    TYPE VARCHAR(20),                       -- 类型：MENU / BUTTON
    CREATED_AT TIMESTAMP
);
```

### 3. SYS_ROLE_MENU（角色菜单关联）
```sql
CREATE TABLE SYS_ROLE_MENU (
    ID BIGINT PRIMARY KEY,
    ROLE_ID BIGINT NOT NULL,
    MENU_ID BIGINT NOT NULL,
    CREATED_AT TIMESTAMP
);
```

### 4. SYS_ROLE_PERMISSION（角色权限关联）
```sql
CREATE TABLE SYS_ROLE_PERMISSION (
    ID BIGINT PRIMARY KEY,
    ROLE_ID BIGINT NOT NULL,
    PERMISSION_ID BIGINT NOT NULL,
    CREATED_AT TIMESTAMP
);
```

## 实现步骤

### Phase 1: 后端实体和Repository
- [x] 创建 Menu 实体
- [x] 创建 Permission 实体
- [x] 创建 RoleMenu 实体
- [x] 创建 RolePermission 实体
- [x] 创建对应 Repository

### Phase 2: 后端服务层
- [ ] MenuService - 菜单管理
- [ ] PermissionService - 权限管理
- [ ] RoleService 扩展 - 菜单/权限分配
- [ ] AuthService 扩展 - 获取用户菜单/权限

### Phase 3: 后端权限校验
- [ ] 创建 @RequirePermission 注解
- [ ] 创建 PermissionAspect 切面
- [ ] 修改 LoginFilter 传递权限信息

### Phase 4: 前端改造
- [ ] 动态加载菜单
- [ ] 按钮权限控制
- [ ] 角色管理页面增加菜单/权限配置

### Phase 5: 初始化数据
- [ ] DataInitializer 初始化菜单和权限数据
- [ ] ADMIN 角色绑定所有菜单和权限
- [ ] admin 用户绑定 ADMIN 角色

## 权限编码规则

格式：`{模块}:{操作}`

- `api:add` - 接口配置新增
- `api:edit` - 接口配置编辑
- `api:delete` - 接口配置删除
- `environment:add` - 环境配置新增
- `environment:edit` - 环境配置编辑
- `environment:delete` - 环境配置删除
- `user:add` - 用户新增
- `user:edit` - 用户编辑
- `user:delete` - 用户删除
- `role:view` - 角色查看
- `role:edit` - 角色编辑
- `audit:view` - 审计日志查看

## 按钮ID规范

所有按钮统一使用以下ID格式：
- 新增按钮：`id="btn-add"`
- 编辑按钮：`id="btn-edit"`
- 删除按钮：`id="btn-delete"`

根据权限动态显示/隐藏按钮。
