/**
 * 主应用逻辑模块
 * 负责登录检查、侧边栏加载、路由控制、版本检测等
 */

const App = (function() {
    'use strict';

    let currentUser = null;
    let currentVersion = null;
    let versionCheckInterval = null;

    // 页面映射
    const PAGE_MAP = {
        list: 'pages/api_list.html',
        import: 'pages/api_import.html',
        debug: 'pages/api_debug.html',
        logs: 'pages/api_logs.html',
        sdk: 'pages/api_sdk.html',
        users: 'pages/user_list.html',
        userForm: 'pages/user_form.html',
        form: 'pages/api_form.html',
        detail: 'pages/api_detail.html',
        environments: 'pages/environment_list.html'
    };

    // ===== 初始化 =====
    async function init() {
        await checkLogin();
        loadSidebar();
        startVersionCheck();
    }

    // ===== 登录检查 =====
    async function checkLogin() {
        try {
            const json = await API.auth.check();
            if (json.code === 200 && json.data && json.data.loggedIn) {
                currentUser = json.data;
                return true;
            } else {
                window.location.href = 'login.html';
                return false;
            }
        } catch (e) {
            console.error('检查登录状态失败:', e);
            window.location.href = 'login.html';
            return false;
        }
    }

    // ===== 侧边栏加载 =====
    function loadSidebar() {
        fetch('_sidebar.html')
            .then(r => r.text())
            .then(html => {
                const container = document.getElementById('sidebarContainer');
                if (container) {
                    container.innerHTML = html;
                    updateNav('list');
                    fillSidebarUser();
                }
            })
            .catch(() => {
                const container = document.getElementById('sidebarContainer');
                if (container) {
                    container.innerHTML = `<iframe src="_sidebar.html" style="width:220px;height:100%;border:none;" frameborder="0"></iframe>`;
                }
            });
    }

    // ===== 填充侧边栏用户信息 =====
    function fillSidebarUser() {
        if (!currentUser) return;

        const name = currentUser.displayName || currentUser.username || '用户';
        const avatarEl = document.getElementById('sidebarAvatar');
        const nameEl = document.getElementById('sidebarUserName');
        const roleEl = document.getElementById('sidebarUserRole');

        if (avatarEl) avatarEl.textContent = name.charAt(0).toUpperCase();
        if (nameEl) nameEl.textContent = name;
        if (roleEl) roleEl.textContent = currentUser.role === 'ADMIN' ? '管理员' : '普通用户';
    }

    // ===== 页面路由 =====
    function loadPage(page, params) {
        const pagePath = PAGE_MAP[page] || PAGE_MAP.list;
        let src = pagePath;

        // 添加参数
        if (params) {
            const separator = src.includes('?') ? '&' : '?';
            src += `${separator}id=${params}`;
        }

        // 添加时间戳防止缓存
        const timestampSeparator = src.includes('?') ? '&' : '?';
        src += `${timestampSeparator}_t=${Date.now()}`;

        const iframe = document.getElementById('contentFrame');
        if (iframe) {
            iframe.src = src;
        }

        updateNav(page);

        // 关闭用户菜单
        const menu = document.getElementById('sidebarUserMenu');
        if (menu) menu.classList.remove('show');
    }

    // ===== 更新导航高亮 =====
    function updateNav(page) {
        document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
        const el = document.getElementById('nav-' + page);
        if (el) el.classList.add('active');
    }

    // ===== 版本检测 =====
    async function checkVersion() {
        // TODO: 实现版本检测逻辑
        // try {
        //     const res = await fetch(`${API_CONFIG.baseURL}/version`);
        //     const json = await res.json();
        //     if (json.code === 200 && json.data && json.data.version) {
        //         const newVersion = json.data.version;
        //         if (currentVersion === null) {
        //             currentVersion = newVersion;
        //         } else if (newVersion !== currentVersion) {
        //             showUpdateToast();
        //         }
        //     }
        // } catch (e) {
        //     console.warn('版本检测失败:', e);
        // }
    }

    function showUpdateToast() {
        const toast = document.getElementById('updateToast');
        if (toast) toast.classList.add('show');
    }

    function closeUpdateToast() {
        const toast = document.getElementById('updateToast');
        if (toast) toast.classList.remove('show');
    }

    function startVersionCheck() {
        checkVersion();
        versionCheckInterval = setInterval(checkVersion, 30000);
    }

    // ===== 登出 =====
    async function doLogout() {
        const ok = await utils.showConfirm(
            '确认退出',
            '确定要退出登录吗？下次访问需要重新输入账号密码。',
            '🚪',
            true
        );

        if (!ok) return;

        try {
            await API.auth.logout();
            window.location.href = 'login.html';
        } catch (e) {
            utils.toast('退出失败，请重试', 'error');
        }
    }

    // ===== 侧边栏用户菜单 =====
    function toggleUserMenu() {
        const menu = document.getElementById('sidebarUserMenu');
        if (!menu) return;
        menu.classList.toggle('show');
    }

    // 点击其他地方关闭菜单
    function initUserMenuClose() {
        document.addEventListener('click', function(e) {
            const userArea = document.getElementById('sidebarUser');
            const menu = document.getElementById('sidebarUserMenu');
            if (menu && userArea && !userArea.contains(e.target)) {
                menu.classList.remove('show');
            }
        });
    }

    // ===== 页面可见性变化 =====
    function initVisibilityChange() {
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') {
                checkVersion();
                checkLogin();
            }
        });
    }

    // 初始化
    $(document).ready(function() {
        initUserMenuClose();
        initVisibilityChange();
        init();
    });

    // 暴露公共 API
    return {
        loadPage,
        updateNav,
        setNav: updateNav,
        doLogout,
        toggleUserMenu,
        closeUpdateToast,
        getCurrentUser: () => currentUser
    };
})();

// 全局导出
if (typeof window !== 'undefined') {
    window.loadPage = App.loadPage;
    window.setNav = App.setNav;
    window.doLogout = App.doLogout;
    window.toggleUserMenu = App.toggleUserMenu;
    window.closeUpdateToast = App.closeUpdateToast;
}
