/**
 * 主应用逻辑模块
 * 负责登录检查、侧边栏加载、路由控制、版本检测等
 */

const App = (function() {
    'use strict';

    let currentUser = null;
    let currentVersion = null;
    let versionCheckInterval = null;
    let userPermissions = [];  // 用户权限列表
    let userMenus = [];        // 用户菜单列表
    let pageMap = {};          // 页面映射（从后端加载）

    // ===== 初始化 =====
    async function init() {
        await checkLogin();
        await loadUserPermissions();
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

    // ===== 加载用户权限 =====
    async function loadUserPermissions() {
        try {
            const [permRes, menuRes] = await Promise.all([
                API.auth.getPermissions(),
                API.auth.getMenus()
            ]);
            
            if (permRes.code === 200) {
                userPermissions = permRes.data || [];
            }
            
            if (menuRes.code === 200) {
                // 后端返回的数据结构: { menus: [...], pageMap: {...} }
                // menus: 用户可访问的列表页菜单（用于侧边栏显示）
                // pageMap: 所有页面路由映射（包括列表页和表单页）
                const data = menuRes.data;
                if (data.menus) {
                    userMenus = data.menus;
                }
                if (data.pageMap) {
                    pageMap = data.pageMap;
                    console.log('[App] pageMap loaded from backend:', pageMap);
                }
            }
        } catch (e) {
            console.error('加载用户权限失败:', e);
        }
    }

    // ===== 检查权限 =====
    function hasPermission(permissionCode) {
        // 管理员拥有所有权限
        if (currentUser && currentUser.isAdmin) {
            return true;
        }
        return userPermissions.includes(permissionCode);
    }

    // ===== 检查菜单访问权限 =====
    function hasMenuAccess(menuCode) {
        // 管理员可访问所有菜单
        if (currentUser && currentUser.isAdmin) {
            return true;
        }
        // 检查是否在用户菜单列表中
        return userMenus.some(m => m.code === menuCode);
    }

    // ===== 检查页面是否为表单页（不需要检查菜单权限）=====
    function isFormPage(page) {
        // 表单页在 pageMap 中但不在 userMenus 列表中
        // 这些页面从列表页调用，不需要单独检查菜单权限
        if (!pageMap[page]) return false;
        return !userMenus.some(m => m.code === page);
    }

    // ===== 侧边栏加载（骨架从 _sidebar.html 加载，菜单动态渲染）=====
    function loadSidebar() {
        fetch('_sidebar.html')
            .then(r => r.text())
            .then(html => {
                const container = document.getElementById('sidebarContainer');
                if (container) {
                    container.innerHTML = html;
                    updateNav('list');
                    fillSidebarUser();
                    renderSidebarMenus();
                }
            })
            .catch(() => {
                const container = document.getElementById('sidebarContainer');
                if (container) {
                    container.innerHTML = `<iframe src="_sidebar.html" style="width:220px;height:100%;border:none;" frameborder="0"></iframe>`;
                }
            });
    }

    // ===== 动态渲染侧边栏菜单 =====
    function renderSidebarMenus() {
        const nav = document.getElementById('sidebarNav');
        if (!nav) return;

        // userMenus 来自 /api/auth/menus，已按用户权限过滤（管理员=全部，普通用户=角色授权）
        // 后端已按 sortOrder 升序返回
        if (!userMenus || userMenus.length === 0) {
            nav.innerHTML = '<div style="color:rgba(255,255,255,0.3);text-align:center;padding:30px;font-size:13px;">暂无菜单</div>';
            return;
        }

        // 按 section 分组，保持 sortOrder 顺序
        const sectionMap = {};
        const sectionOrder = [];
        userMenus.forEach(menu => {
            const section = menu.section || '其他';
            if (!sectionMap[section]) {
                sectionMap[section] = [];
                sectionOrder.push(section);
            }
            sectionMap[section].push(menu);
        });

        let html = '';
        sectionOrder.forEach(section => {
            const items = sectionMap[section];
            html += '<div class="nav-section" data-section="' + section + '">';
            html += '<div class="nav-section-title" onclick="toggleSection(\'' + section + '\')">';
            html += '<span>' + section + '</span>';
            html += '<span class="toggle-icon">▼</span>';
            html += '</div>';
            html += '<div class="nav-items">';
            items.forEach(menu => {
                const code = menu.code || menu.path || '';
                const name = menu.name || '';
                const icon = menu.icon || '📄';
                html += '<div class="nav-item" id="nav-' + code + '" onclick="parent.loadPage(\'' + code + '\')">';
                html += '<span class="nav-icon">' + icon + '</span>';
                html += '<span>' + name + '</span>';
                html += '<span class="nav-arrow">›</span>';
                html += '</div>';
            });
            html += '</div></div>';
        });

        nav.innerHTML = html;

        // 恢复折叠状态
        restoreSectionStates();
    }

    // ===== 侧边栏折叠状态 =====
    function toggleSection(sectionName) {
        const section = document.querySelector('.nav-section[data-section="' + sectionName + '"]');
        if (section) {
            section.classList.toggle('collapsed');
            const collapsed = section.classList.contains('collapsed');
            localStorage.setItem('nav-section-' + sectionName + '-collapsed', collapsed);
        }
    }

    function restoreSectionStates() {
        document.querySelectorAll('.nav-section').forEach(section => {
            const sectionName = section.dataset.section;
            const collapsed = localStorage.getItem('nav-section-' + sectionName + '-collapsed');
            if (collapsed === 'true') {
                section.classList.add('collapsed');
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
        if (roleEl) roleEl.textContent = currentUser.isAdmin ? '管理员' : '普通用户';
    }

    // ===== 页面路由 =====
    function loadPage(page, params) {
        // 表单页（FORM类型）不需要检查菜单权限，从列表页调用
        // 列表页（LIST类型）需要检查菜单权限
        if (!isFormPage(page) && !hasMenuAccess(page)) {
            utils.toast('无访问权限', 'error');
            return;
        }

        const pagePath = pageMap[page] || 'pages/api_list.html';
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
        getCurrentUser: () => currentUser,
        hasPermission,
        hasMenuAccess,
        getUserPermissions: () => userPermissions,
        getUserMenus: () => userMenus,
        getPageMap: () => pageMap
    };
})();

// 全局导出
if (typeof window !== 'undefined') {
    window.loadPage = App.loadPage;
    window.setNav = App.setNav;
    window.doLogout = App.doLogout;
    window.toggleUserMenu = App.toggleUserMenu;
    window.closeUpdateToast = App.closeUpdateToast;
    window.hasPermission = App.hasPermission;
    window.hasMenuAccess = App.hasMenuAccess;
    window.toggleSection = App.toggleSection;
}
