# 统一接口配置系统 - 前端模块化重构

## 项目结构

```
www/
├── index.html              # 主入口页面
├── login.html              # 登录页面
├── _sidebar.html           # 侧边栏模板
├── pages/                  # 功能子页面
│   ├── api_list.html      # 接口列表（已完成）
│   ├── api_detail.html    # 接口详情（已完成）
│   ├── api_form.html      # 接口表单（已完成）
│   ├── api_debug.html     # 接口调试（已完成）
│   ├── api_import.html    # Curl导入（已完成）
│   ├── api_logs.html      # 调用日志（已完成）
│   ├── api_sdk.html       # SDK文档（已完成）
│   ├── user_list.html     # 用户管理（已完成）
│   └── environment_list.html  # 环境配置（已完成）
├── css/
│   ├── base.css           # 基础样式
│   ├── components.css     # 组件样式
│   └── sidebar.css        # 侧边栏样式
├── js/
│   ├── vendor/
│   │   └── jquery.min.js  # jQuery 3.7.1
│   ├── utils.js           # 通用工具函数
│   ├── api.js             # API 请求封装
│   └── app.js             # 主应用逻辑
└── nginx.conf.example     # Nginx 配置示例
```

## 技术栈

- **jQuery 3.7.1** - DOM 操作和事件处理
- **原生 Fetch API** - HTTP 请求（封装在 api.js 中）
- **模块化架构** - 工具函数、API 封装、业务逻辑分离

## 快速开始

### 开发环境

1. 将 `www` 目录部署到 Web 服务器（如 Nginx）
2. 配置 API 代理（见 nginx.conf.example）
3. 访问 `http://localhost`

### 配置 API 地址

默认 API 地址为 `/api`，如需修改，可在 HTML 中设置环境变量：

```html
<script>
window.API_BASE_URL = 'http://your-backend-server:8080/api';
</script>
<script src="js/api.js"></script>
```

## 核心模块说明

### utils.js - 工具函数库

```javascript
// Toast 提示
utils.toast('操作成功', 'success');
utils.toast('操作失败', 'error');

// 确认弹窗
const ok = await utils.showConfirm('确认删除', '确定要删除吗？', '🗑️', true);

// 日期格式化
utils.formatDate(new Date(), 'YYYY-MM-DD HH:mm:ss');

// HTML 转义
utils.escapeHtml('<script>alert("xss")</script>');

// URL 参数获取
utils.getUrlParam('id');
```

### api.js - API 封装

```javascript
// 认证相关
await API.auth.check();
await API.auth.login({ userCode, password });
await API.auth.logout();
await API.auth.getUsers({ page: 1, size: 10 });
await API.auth.createUser(data);
await API.auth.updateUser(id, data);
await API.auth.deleteUser(id);

// 接口配置
await API.config.list({ keyword, status, page, size });
await API.config.get(id);
await API.config.create(data);
await API.config.update(id, data);
await API.config.delete(id);
await API.config.toggle(id);
await API.config.importCurl(curl);
await API.config.getActiveList();
await API.config.getByCode(code);

// 调用日志
await API.invoke.debug({ apiCode, headers, params, body });
await API.invoke.getLogs({ page, size });
await API.invoke.getLogDetail(id);

// 环境配置
await API.environment.list({ page, size });
await API.environment.get(id);
await API.environment.create(data);
await API.environment.update(id, data);
await API.environment.delete(id);
```

### app.js - 主应用逻辑

```javascript
// 页面路由（在 iframe 子页面中调用）
parent.loadPage('list');
parent.loadPage('form', id);
parent.loadPage('detail', id);
parent.loadPage('debug');

// 导航高亮
parent.setNav('list');
```

## Nginx 部署

见 `nginx.conf.example` 文件。

## 子页面迁移指南

### 迁移步骤

1. 将原 HTML 文件复制到 `www/pages/` 目录
2. 修改 CSS/JS 引用路径为 `../css/` 和 `../js/`
3. 使用 jQuery 简化 DOM 操作
4. 使用 `API.*` 替代直接 `fetch` 调用
5. 使用 `utils.toast()` 替代自定义提示

### 示例对比

**原始代码：**
```javascript
fetch(`${API}/config/page?keyword=${kw}&page=${page}&size=${size}`)
    .then(r => r.json())
    .then(json => {
        if (json.code === 200) {
            renderTable(json.data.records);
        }
    });
```

**重构后：**
```javascript
API.config.list({ keyword, page, size })
    .then(json => {
        renderTable(json.data.records);
    })
    .catch(err => {
        utils.toast('加载失败: ' + err.message, 'error');
    });
```

## 已完成页面

- ✅ index.html - 主入口
- ✅ login.html - 登录页
- ✅ pages/api_list.html - 接口列表

## 待迁移页面

以下页面需要从原目录迁移并重构：

- [ ] api_detail.html
- [ ] api_form.html
- [ ] api_debug.html
- [ ] api_import.html
- [ ] api_logs.html
- [ ] api_sdk.html
- [ ] user_list.html
- [ ] environment_list.html

## 注意事项

1. 所有子页面通过 iframe 加载，使用 `parent.loadPage()` 进行路由
2. Toast 和 Confirm 组件在主页面中，子页面通过 `parent.utils` 调用
3. API 地址支持环境变量配置，便于不同环境部署
4. 保持与现有后端 API 接口完全兼容
