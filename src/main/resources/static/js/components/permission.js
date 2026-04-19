/**
 * Permission 权限校验组件
 * 
 * 统一管理按钮权限和菜单权限校验，避免各页面重复请求 /api/auth/permissions。
 * 
 * 使用方式：
 *   1. 在页面 <script> 中引入：
 *      <script src="../js/components/permission.js"></script>
 * 
 *   2. 初始化（通常在 $(function(){}) 中调用）：
 *      await Permission.init();
 * 
 *   3. 按钮权限校验：
 *      // 方式一：代码判断
 *      if (Permission.has('api:add')) { ... }
 * 
 *      // 方式二：声明式 — 给按钮加 data-permission 属性，调用 apply() 自动隐藏
 *      // <button data-permission="api:add">新建</button>
 *      // <button data-permission="api:delete" class="btn-danger">删除</button>
 *      Permission.apply();  // 自动隐藏无权限按钮
 * 
 *   4. 菜单权限校验：
 *      if (Permission.hasMenu('list')) { ... }
 * 
 *   5. 判断当前用户是否管理员：
 *      if (Permission.isAdmin()) { ... }
 *
 * 数据来源优先级：
 *   - 优先从 parent.App 获取（主框架已加载权限数据，零请求）
 *   - 降级：自行请求 /api/auth/permissions（独立窗口或 iframe 未嵌套时）
 *
 * 全局页面守卫：
 *   Permission.init({ guard: true })  // 初始化后自动检查当前页面访问权限
 *   或手动调用 Permission.guard()
 *   无权限时页面内容会被遮罩，显示"无访问权限"提示。
 *   表单页（FORM 类型，不在侧边栏菜单中）默认放行。
 */
const Permission = (function() {
    'use strict';

    let _permissions = [];   // 权限代码列表
    let _menus = [];         // 菜单列表
    let _pageMap = {};       // 页面路由映射
    let _initialized = false;
    let _isAdmin = false;
    let _currentUser = null;
    let _guarded = false; // guard() 是否已拦截当前页面

    // 需要菜单权限的列表页 code（与 menu-config.json 中 pageType=LIST 的菜单对应）
    // 表单页（FORM 类型）不需要菜单权限，从列表页调用即可
    const LIST_PAGE_CODES = new Set([
        'dashboard', 'list', 'import', 'debug', 'logs', 'environments',
        'users', 'roles', 'audit-logs', 'sdk'
    ]);

    // 表单页 → 所需权限码（只要拥有任一即放行）
    const FORM_PERMISSION_MAP = {
        'form':         ['api:add', 'api:edit'],           // 接口表单（新建或编辑）
        'detail':       ['api:detail', 'api:edit'],        // 接口详情（查看或编辑）
        'import':       ['api:add'],                       // Curl 导入 = 创建
        'debug':        ['api:invoke'],                    // 接口调试 = 调用
        'logs':         ['log:view'],                      // 调用日志 = 查看日志
        'sdk':          ['api:view'],                      // SDK 文档 = 查看接口
        'envForm':      ['env:add', 'env:edit'],           // 环境表单
        'userForm':     ['user:add', 'user:edit'],         // 用户表单
        'roleEdit':     ['role:edit'],                     // 角色编辑
        'audit-logs-detail': ['audit-log:detail']          // 审计日志详情
    };

    /**
     * 初始化权限数据
     * @param {Object} [options] 配置项
     * @param {boolean} [options.applyOnLoad=true] 初始化后自动调用 apply() 隐藏无权限按钮
     * @param {boolean} [options.guard=false] 初始化后自动检查当前页面访问权限
     * @returns {Promise<void>}
     */
    async function init(options) {
        const opts = Object.assign({ applyOnLoad: true, guard: false }, options);

        // 优先从父窗口 App 获取（主框架已经加载过权限数据）
        try {
            if (parent && parent.App && parent.App.getCurrentUser) {
                _currentUser = parent.App.getCurrentUser();
                _isAdmin = !!(_currentUser && _currentUser.isAdmin);
                _permissions = parent.App.getUserPermissions() || [];
                _menus = parent.App.getUserMenus() || [];
                // 优先从 App.getPageMap() 获取，零请求
                if (parent.App.getPageMap) {
                    _pageMap = parent.App.getPageMap() || {};
                }
                // 降级：如果 pageMap 为空，请求后端
                if (Object.keys(_pageMap).length === 0) {
                    await _loadPageMap();
                }
                _initialized = true;
                console.log('[Permission] 从父窗口加载权限, admin:', _isAdmin, '权限数:', _permissions.length);
                if (opts.applyOnLoad) apply();
                if (opts.guard) guard();
                return;
            }
        } catch (e) {
            // 跨域或无父窗口，忽略
            console.log('[Permission] 父窗口不可用，自行加载权限');
        }

        // 降级：自行请求
        try {
            const [permRes, menuRes] = await Promise.all([
                API.auth.getPermissions(),
                API.auth.getMenus()
            ]);

            if (permRes.code === 200) {
                _permissions = permRes.data || [];
            }
            if (menuRes.code === 200 && menuRes.data) {
                _menus = menuRes.data.menus || [];
                _pageMap = menuRes.data.pageMap || {};
            }
        } catch (e) {
            console.error('[Permission] 加载权限失败:', e);
        }

        _initialized = true;
        if (opts.applyOnLoad) apply();
        if (opts.guard) guard();
    }

    /**
     * 加载 pageMap（仅在父窗口模式下需要额外请求，因为 App 未暴露 pageMap）
     */
    async function _loadPageMap() {
        try {
            const menuRes = await API.auth.getMenus();
            if (menuRes.code === 200 && menuRes.data && menuRes.data.pageMap) {
                _pageMap = menuRes.data.pageMap;
            }
        } catch (e) {
            console.warn('[Permission] 加载 pageMap 失败:', e);
        }
    }

    /**
     * 检查是否拥有指定按钮权限
     * @param {string} code 权限代码，如 'api:add', 'user:edit'
     * @returns {boolean}
     */
    function has(code) {
        if (_isAdmin) return true;
        return _permissions.includes(code);
    }

    /**
     * 检查是否拥有指定菜单访问权限
     * @param {string} menuCode 菜单代码，如 'list', 'users', 'roles'
     * @returns {boolean}
     */
    function hasMenu(menuCode) {
        if (_isAdmin) return true;
        return _menus.some(m => m.code === menuCode);
    }

    /**
     * 当前用户是否管理员
     * @returns {boolean}
     */
    function isAdmin() {
        return _isAdmin;
    }

    /**
     * 获取当前用户信息
     * @returns {Object|null}
     */
    function getCurrentUser() {
        return _currentUser;
    }

    /**
     * 获取所有权限代码列表
     * @returns {string[]}
     */
    function getAll() {
        return [..._permissions];
    }

    /**
     * 获取所有菜单列表
     * @returns {Array}
     */
    function getAllMenus() {
        return [..._menus];
    }

    /**
     * 声明式权限控制：自动隐藏无权限的按钮
     * 
     * 在 HTML 中给按钮添加 data-permission 属性：
     *   <button data-permission="api:add">新建</button>
     *   <button data-permission="api:delete">删除</button>
     * 
     * 调用 Permission.apply() 后，无权限的按钮将被隐藏。
     * 
     * @param {string} [scope] 可选，限定查找范围的选择器，默认 'body'
     */
    function apply(scope) {
        const root = scope ? document.querySelector(scope) : document.body;
        if (!root) return;

        root.querySelectorAll('[data-permission]').forEach(el => {
            const code = el.getAttribute('data-permission');
            if (code && !has(code)) {
                el.style.display = 'none';
            }
        });
    }

    /**
     * 条件渲染：根据权限返回内容或空字符串
     * 用于模板字符串中：
     *   `${Permission.render('api:edit', '<button>编辑</button>')}`
     * 
     * @param {string} code 权限代码
     * @param {string} content 有权限时返回的内容
     * @param {string} [fallback=''] 无权限时返回的内容
     * @returns {string}
     */
    function render(code, content, fallback) {
        return has(code) ? content : (fallback || '');
    }

    /**
     * 页面访问守卫：检查当前页面是否在用户可访问范围内。
     * 
     * 工作流程：
     *   1. 管理员 → 直接放行
     *   2. 从当前 iframe URL 提取页面路径（如 pages/api_list.html）
     *   3. 通过 pageMap 反查对应的 menu code
     *   4. 列表页 → 检查用户菜单权限
     *   5. 表单页/操作页 → 检查用户按钮权限（FORM_PERMISSION_MAP）
     *   6. 无权限 → 遮罩页面，显示"无访问权限"提示
     * 
     * @returns {boolean} true=有权限放行, false=无权限已拦截
     */
    function guard() {
        if (!_initialized) {
            console.warn('[Permission] guard() 调用时权限未初始化');
            return true;
        }

        // 管理员放行
        if (_isAdmin) return true;

        // 解析当前页面的 menu code
        const menuCode = _resolveMenuCode();
        if (!menuCode) {
            console.log('[Permission] guard: 无法解析 menu code，放行');
            return true;
        }

        // 列表页：检查菜单权限
        if (LIST_PAGE_CODES.has(menuCode)) {
            if (hasMenu(menuCode)) return true;
            _guarded = true;
            _showNoPermission(menuCode);
            return false;
        }

        // 表单页/操作页：检查按钮权限
        const requiredPerms = FORM_PERMISSION_MAP[menuCode];
        if (requiredPerms) {
            const hasAccess = requiredPerms.some(perm => has(perm));
            if (hasAccess) return true;
            _guarded = true;
            _showNoPermission(menuCode);
            return false;
        }

        // 未在 FORM_PERMISSION_MAP 中配置的页面，放行（兼容性）
        console.log('[Permission] guard: 未配置权限映射，放行, code=', menuCode);
        return true;
    }

    /**
     * 从当前 iframe URL 反查 menu code
     * URL: /pages/api_list.html → 从 pageMap 找到 key = 'list'（如果 pageMap['list'] === 'pages/api_list.html'）
     */
    function _resolveMenuCode() {
        try {
            // 提取页面路径，如 "/static/pages/api_list.html" → "pages/api_list.html"
            const pathname = window.location.pathname;
            // 尝试匹配 pages/xxx.html
            const match = pathname.match(/\/(pages\/[^/]+\.html)$/);
            const pageFile = match ? match[1] : '';

            if (!pageFile) return null;

            // 从 pageMap 反查 menu code
            for (const [code, file] of Object.entries(_pageMap)) {
                if (file === pageFile) return code;
            }
        } catch (e) {
            console.warn('[Permission] resolveMenuCode 异常:', e);
        }
        return null;
    }

    /**
     * 显示无权限遮罩
     */
    function _showNoPermission(menuCode) {
        const style = document.createElement('style');
        style.textContent = `
            .permission-denied-overlay {
                position: fixed; top: 0; left: 0; right: 0; bottom: 0;
                background: #f8f9fa; z-index: 99998;
                display: flex; align-items: center; justify-content: center;
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            }
            .permission-denied-box {
                text-align: center; padding: 48px 40px;
                background: #fff; border-radius: 12px;
                box-shadow: 0 4px 24px rgba(0,0,0,0.08);
                max-width: 380px;
            }
            .permission-denied-icon { font-size: 56px; margin-bottom: 16px; }
            .permission-denied-title { font-size: 20px; font-weight: 600; color: #1a1a1a; margin-bottom: 8px; }
            .permission-denied-desc { font-size: 14px; color: #888; line-height: 1.6; }
            .permission-denied-btn {
                margin-top: 24px; padding: 10px 28px;
                background: #4f46e5; color: #fff; border: none; border-radius: 8px;
                font-size: 14px; cursor: pointer;
            }
            .permission-denied-btn:hover { background: #4338ca; }
        `;
        document.head.appendChild(style);

        const overlay = document.createElement('div');
        overlay.className = 'permission-denied-overlay';
        overlay.innerHTML = `
            <div class="permission-denied-box">
                <div class="permission-denied-icon">🔒</div>
                <div class="permission-denied-title">无访问权限</div>
                <div class="permission-denied-desc">
                    您没有访问当前页面的权限，请联系管理员开通。
                </div>
                <button class="permission-denied-btn" onclick="parent.loadPage && parent.loadPage('list')">
                    返回首页
                </button>
            </div>
        `;
        document.body.appendChild(overlay);
    }

    return {
        init,
        has,
        hasMenu,
        isAdmin,
        getCurrentUser,
        getAll,
        getAllMenus,
        apply,
        render,
        guard,
        isDenied: () => _guarded
    };
})();
