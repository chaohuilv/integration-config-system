# 路由配置后端化 - 2026-04-18（简化版）

## 需求

将前端页面路由信息从硬编码改为从后端配置返回，角色配置只需控制菜单列表中显示哪些菜单项。

## 设计方案

### 核心思路

在 `Menu` 表中添加 `pageType` 字段区分页面类型：
- **LIST**：列表页，显示在侧边栏菜单，需要角色授权
- **FORM**：表单/详情页，不显示在菜单，从列表页调用，不需要单独授权

### 数据结构

#### Menu 实体新增字段
```java
@Column(name = "PAGE_TYPE", length = 20)
private String pageType;  // LIST 或 FORM，默认 LIST
```

#### menu-config.json 示例
```json
{
  "menus": [
    { "code": "list", "name": "接口配置", "path": "list", "pageFile": "pages/api_list.html", "pageType": "LIST", "section": "集成管理" },
    { "code": "form", "name": "接口表单", "path": "form", "pageFile": "pages/api_form.html", "pageType": "FORM" },
    { "code": "detail", "name": "接口详情", "path": "detail", "pageFile": "pages/api_detail.html", "pageType": "FORM" }
  ]
}
```

### 接口设计

#### GET /api/auth/menus

返回数据结构：
```json
{
  "code": 200,
  "data": {
    "menus": [           // 用户可访问的列表页菜单（pageType=LIST）
      { "code": "list", "path": "list", "pageFile": "pages/api_list.html", ... }
    ],
    "pageMap": {         // 所有页面路由映射（包括 LIST 和 FORM）
      "list": "pages/api_list.html",
      "form": "pages/api_form.html",
      "detail": "pages/api_detail.html"
    }
  }
}
```

### 权限控制逻辑

| 页面类型 | 侧边栏显示 | 权限检查 |
|---------|-----------|---------|
| LIST（列表页） | ✅ 显示 | 检查用户角色是否有该菜单权限 |
| FORM（表单页） | ❌ 不显示 | 不检查，从列表页调用 |

### 前端实现

```javascript
// app.js
async function loadUserPermissions() {
    const menuRes = await API.auth.getMenus();
    const data = menuRes.data;
    userMenus = data.menus;     // 列表页菜单（用于侧边栏）
    pageMap = data.pageMap;     // 所有页面路由
}

function isFormPage(page) {
    // 在 pageMap 中但不在 userMenus 中 = 表单页
    return pageMap[page] && !userMenus.some(m => m.code === page);
}

function loadPage(page, params) {
    // 表单页不检查权限，列表页检查菜单权限
    if (!isFormPage(page) && !hasMenuAccess(page)) {
        utils.toast('无访问权限', 'error');
        return;
    }
    // 加载页面...
}
```

## 文件变更

### 后端修改
| 文件 | 变更 |
|------|------|
| `entity/config/Menu.java` | 新增 `pageType` 字段 |
| `service/MenuService.java` | 新增 `getListMenus()`, `getAllMenusForPageMap()` |
| `controller/AuthController.java` | `/menus` 接口返回结构变更 |
| `config/DataInitializer.java` | 移除 ExtraPage 相关，读取 pageType |
| `menu-config.json` | 合并所有页面，添加 pageType 字段 |

### 后端删除
- `entity/config/ExtraPage.java`
- `repository/config/ExtraPageRepository.java`

### 前端修改
| 文件 | 变更 |
|------|------|
| `js/app.js` | 使用后端返回的 pageMap，简化权限检查逻辑 |

## 数据库变更

启动时自动添加 `PAGE_TYPE` 列：
```sql
ALTER TABLE SYS_MENU ADD COLUMN PAGE_TYPE VARCHAR(20) DEFAULT 'LIST';
```

## 优势

1. **单一数据源**：所有页面配置在 `SYS_MENU` 表，统一管理
2. **简化权限**：角色只需关联 LIST 类型菜单，FORM 页面自动可访问
3. **灵活扩展**：可轻松添加新的页面类型（如 DASHBOARD, REPORT 等）
4. **前端简化**：无需维护额外的 ExtraPage 表和映射逻辑
