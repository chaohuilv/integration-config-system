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
 */
const Permission = (function() {
    'use strict';

    let _permissions = [];   // 权限代码列表
    let _menus = [];         // 菜单列表
    let _initialized = false;
    let _isAdmin = false;
    let _currentUser = null;

    /**
     * 初始化权限数据
     * @param {Object} [options] 配置项
     * @param {boolean} [options.applyOnLoad=true] 初始化后自动调用 apply() 隐藏无权限按钮
     * @returns {Promise<void>}
     */
    async function init(options) {
        const opts = Object.assign({ applyOnLoad: true }, options);

        // 优先从父窗口 App 获取（主框架已经加载过权限数据）
        try {
            if (parent && parent.App && parent.App.getCurrentUser) {
                _currentUser = parent.App.getCurrentUser();
                _isAdmin = !!(_currentUser && _currentUser.isAdmin);
                _permissions = parent.App.getUserPermissions() || [];
                _menus = parent.App.getUserMenus() || [];
                _initialized = true;
                console.log('[Permission] 从父窗口加载权限, admin:', _isAdmin, '权限数:', _permissions.length);
                if (opts.applyOnLoad) apply();
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
            }
        } catch (e) {
            console.error('[Permission] 加载权限失败:', e);
        }

        _initialized = true;
        if (opts.applyOnLoad) apply();
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

    return {
        init,
        has,
        hasMenu,
        isAdmin,
        getCurrentUser,
        getAll,
        getAllMenus,
        apply,
        render
    };
})();
